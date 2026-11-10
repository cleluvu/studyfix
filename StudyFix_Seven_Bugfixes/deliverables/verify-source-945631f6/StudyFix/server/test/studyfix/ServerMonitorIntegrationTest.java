package studyfix;

import java.nio.file.*;
import java.util.*;
import studyfix.assignment.model.GradingResult;
import studyfix.assignment.transport.AssignmentTcpServer;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP/TCP/SQLite monitoring data; deterministic test grader, not a Docker benchmark. */
public final class ServerMonitorIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("studyfix.screen.port", "0");
        System.setProperty("studyfix.web.port", "0");
        Path temp = Files.createTempDirectory(Path.of("build"), "server-monitor-");
        try (Server core = new Server(0, temp.resolve("core.db"));
             AssignmentTcpServer assignments = new AssignmentTcpServer(core.gateway(), temp.resolve("learning.db"), 0, 32, 1, "unused");
             WebGatewayServer web = new WebGatewayServer(core, assignments,
                     (id, source, tests, max) -> new GradingResult("ACCEPTED", max, max, tests.size(), tests.size(), "TEST DOUBLE", 1, List.of()))) {
            core.start(); assignments.start(); web.start();
            String base = "http://127.0.0.1:" + web.port();
            String teacher = login(base, "teacher", "Teacher123!");
            String student = login(base, "student1", "Student123!");
            Response created = call(base, "/api/rooms/current/command", teacher,
                    mapOf("action", "CREATE_ROOM", "roomCode", "Lớp Java riêng"));
            check(created.status() == 200, "custom room created");
            String roomId = room(base, teacher);
            join(base, student, "Lớp Java riêng");
            ServerMonitor.RoomView monitor = ServerMonitor.capture(core).get(roomId);
            check(monitor.code().equals("Lớp Java riêng"), "manager sees public code instead of numeric ID");
            check(monitor.owner().contains("teacher") && !monitor.owner().equals(core.rooms.get(roomId).snapshot().ownerId()), "owner resolved to name and account");
            check(monitor.branches().isEmpty(), "waiting room has no invented branches");
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("Tạo phòng Lớp Java riêng") && x.contains("(teacher)")), "create log includes public room and actor");
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("Vào phòng Lớp Java riêng") && x.contains("(student1)")), "join log includes student and named room");
            ok(base, teacher, "START", Map.of());
            ok(base, teacher, "SELECT_EXERCISE", mapOf("exerciseId", "DBG_TEXT001"));
            ok(base, teacher, "FORK", Map.of());
            String studentId = text(rows(view(base, student).get("branches")).getFirst(), "userId", "");
            String source = "public class Main { public static void main(String[] args) { System.out.println(1); } } // saved branch";
            ok(base, student, "BRANCH_SAVE", mapOf("expectedVersion", 0, "code", source));
            ok(base, student, "BRANCH_CHECK", mapOf("expectedVersion", 1));
            waitFor(() -> ServerMonitor.capture(core).get(roomId).branches().getFirst().summary().contains("ACCEPTED"), "manager reads completed check");
            monitor = ServerMonitor.capture(core).get(roomId);
            check(monitor.phase().equals("BRANCHING") && monitor.round() == 1, "manager exposes phase and round");
            check(monitor.branches().getFirst().detail().contains(source), "manager reads exact saved student source");
            check(monitor.branches().getFirst().summary().contains("student1") && monitor.branches().getFirst().summary().contains("v1"), "branch label identifies actor and version");
            check(!core.rooms.get(roomId).snapshot().code().equals(source), "AC does not automatically replace Master");
            ok(base, student, "BRANCH_SAVE", mapOf("expectedVersion", 1, "code", source + "\n// new draft saved"));
            check(ServerMonitor.capture(core).get(roomId).branches().getFirst().summary().contains("kết quả bản cũ"), "manager marks stale check after a newer save");
            ok(base, teacher, "FREEZE", Map.of());
            ok(base, teacher, "MERGE", mapOf("userId", studentId, "branchVersion", 2));
            check(core.rooms.get(roomId).snapshot().code().endsWith("// new draft saved"), "Merge updates Master shown by manager");
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("MERGE") && x.contains("Lớp Java riêng") && x.contains("teacher")), "Merge activity identifies room and teacher");
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("Lưu nhánh") && x.contains("student1")), "branch saves are visible in activity log");
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("Kết quả nhánh") && x.contains("ACCEPTED")), "grading completion appears in activity log");
            call(base, "/api/rooms/current/command", student, mapOf("action", "LEAVE_ROOM"));
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("Rời phòng Lớp Java riêng") && x.contains("student1")), "leave log identifies account and room");
            check(ServerMonitor.historySelection("A", "A", 0, 1, 2) == 1, "viewing newest follows new revision");
            check(ServerMonitor.historySelection("A", "A", 0, 3, 4) == 0, "explicit old revision selection remains stable");
            check(ServerMonitor.historySelection("A", "B", 0, 3, 4) == 3, "switching rooms selects latest revision");
            check(ServerMonitor.historySelection("A", "B", 0, 3, 0) == -1, "empty history has no invalid selection");
        }
        System.out.println("ServerMonitorIntegrationTest: " + checks + " checks passed");
    }
}
