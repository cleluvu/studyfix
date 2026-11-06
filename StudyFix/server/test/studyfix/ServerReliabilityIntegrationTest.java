package studyfix;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import studyfix.assignment.transport.AssignmentTcpServer;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Deterministic lifecycle/concurrency and database failure tests, using isolated databases/ports. */
public final class ServerReliabilityIntegrationTest {
    private static Object field(Object object, String name) throws Exception {
        var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static final class PausedRoom extends Room {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        PausedRoom(Server server, Store.StoredRoom stored) throws SQLException { super(server, stored); }
        @Override public void chat(ClientHandler client, String text) throws IOException {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test release timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            super.chat(client, text);
        }
    }
    private static void sql(Path database, String statement) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath()); Statement s = c.createStatement()) { s.execute(statement); }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("studyfix.screen.port", "0"); System.setProperty("studyfix.web.port", "0");
        Path temp = Files.createTempDirectory(Path.of("build"), "server-reliability-");
        Path learning = temp.resolve("learning.db");
        String oldDocker = System.getProperty("studyfix.assignment.docker");
        System.setProperty("studyfix.assignment.docker", temp.resolve("missing-docker.exe").toAbsolutePath().toString());
        try (Server core = new Server(0, temp.resolve("core.db"));
             AssignmentTcpServer assignments = new AssignmentTcpServer(core.gateway(), learning, 0, 32, 1, "unused");
             WebGatewayServer web = new WebGatewayServer(core, assignments)) {
            core.start(); assignments.start(); web.start();
            String base = "http://127.0.0.1:" + web.port();
            Identity owner = core.store().login("teacher", "Teacher123!".toCharArray());
            PausedRoom room = new PausedRoom(core, core.store().createRoom(owner, Room.INITIAL_CODE, "Concurrent room"));
            core.rooms.put(room.getRoomId(), room);
            String student = login(base, "student1", "Student123!");
            join(base, student, room.getRoomCode());
            ClientHandler handler = core.clients.values().stream().filter(c -> c.identity() != null && c.identity().username().equals("student1")).findFirst().orElseThrow();
            call(base, "/api/rooms/current/command", student, mapOf("action", "CHAT", "text", "concurrent message"));
            check(room.entered.await(3, TimeUnit.SECONDS), "TCP chat reached controlled pause");
            Thread leaving = new Thread(handler::leaveCurrentRoom, "test-concurrent-leave");
            try {
                leaving.start();
                waitFor(() -> leaving.getState() == Thread.State.BLOCKED, "leave waits on command membership lock");
                check(room.getRoomId().equals(handler.getCurrentRoomId()), "room remains attached until in-flight command completes");
            } finally { room.release.countDown(); }
            leaving.join(3000);
            check(!leaving.isAlive() && handler.getCurrentRoomId() == null, "leave completes after chat without losing connection");
            check(handler.isConnected() && call(base, "/api/me", student, null).status() == 200, "authenticated session survives concurrent leave");
            check(new ClassroomRepository(core.store()).chat(room.getRoomId()).size() == 1, "in-flight message committed before membership leave");

            // A dead core socket must cancel the web heartbeat, including repeated cleanup.
            String observer = login(base, "student2", "Student234!");
            @SuppressWarnings("unchecked") Map<String, Object> sessions = (Map<String, Object>) field(web, "sessions");
            Object session = sessions.get(observer);
            ScheduledFuture<?> heartbeat = (ScheduledFuture<?>) field(session, "heartbeat");
            String coreId = (String) field(session, "coreSessionId");
            core.clients.get(coreId).closeConnection();
            waitFor(heartbeat::isCancelled, "heartbeat cancelled immediately after core EOF");
            var cleanup = web.getClass().getDeclaredMethod("cleanup"); cleanup.setAccessible(true);
            cleanup.invoke(web); cleanup.invoke(web);
            check(!sessions.containsKey(observer) && heartbeat.isCancelled(), "repeated cleanup removes dead session and keeps heartbeat cancelled");

            // Hold result writes with a trigger. The worker must retry saving the original verdict.
            sql(learning, "CREATE TRIGGER fail_result_write BEFORE UPDATE ON p3_practice_submissions WHEN NEW.status='DONE' BEGIN SELECT RAISE(ABORT,'test persistence unavailable'); END");
            Map<String, Object> list = obj(call(base, "/api/assignments", student, mapOf("type", "PRACTICE_LIST")).body().get("data"));
            String exercise = text(rows(list.get("exercises")).getFirst(), "id", "");
            Map<String, Object> submitted = obj(call(base, "/api/assignments", student,
                    mapOf("type", "PRACTICE_SUBMIT", "exerciseId", exercise, "sourceCode", "public class Main {}", "idempotencyKey", "retry-save")).body().get("data"));
            String submission = text(obj(submitted.get("submission")), "id", "");
            check(!submission.isBlank(), "submission accepted for persistence fault test");
            waitFor(() -> assignments.pendingResultWrites() == 1, "failed result write becomes observable");
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("Chờ lưu kết quả") && x.contains(submission)), "failure log correlates original verdict with submission");
            sql(learning, "DROP TRIGGER fail_result_write");
            waitFor(() -> {
                Map<String, Object> data = obj(call(base, "/api/assignments", student, mapOf("type", "PRACTICE_RESULT", "submissionId", submission)).body().get("data"));
                return "DONE".equals(obj(data.get("submission")).get("status"));
            }, "worker retries result write after database recovers");
            Map<String, Object> data = obj(call(base, "/api/assignments", student, mapOf("type", "PRACTICE_RESULT", "submissionId", submission)).body().get("data"));
            check("SANDBOX_UNAVAILABLE".equals(obj(data.get("submission")).get("verdict")), "persistence retry preserves original sandbox verdict");
            waitFor(() -> assignments.pendingResultWrites() == 0 && assignments.metrics().activeTasks() == 0, "worker releases result and active counts");
            check(core.snapshot().log().stream().anyMatch(x -> x.contains("Đã lưu lại kết quả") && x.contains(submission)), "successful retry recorded");

            // Independent repository instances must serialize version allocation against concurrent writes.
            Identity who = handler.identity();
            var firstRepo = new studyfix.assignment.persistence.PracticeRepository(learning);
            var secondRepo = new studyfix.assignment.persistence.PracticeRepository(learning);
            ExecutorService submitters = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Integer>>> futures = new ArrayList<>();
            try {
                for (var repository : List.of(firstRepo, secondRepo)) futures.add(submitters.submit(() -> {
                    start.await(); List<Integer> versions = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        Map<String, Object> row = repository.create(who, exercise, "test fixture", false, UUID.randomUUID().toString(), 1);
                        versions.add(((Number) row.get("version")).intValue());
                        repository.finish(String.valueOf(row.get("id")), new studyfix.assignment.model.GradingResult("WRONG_ANSWER", 0, 100, 0, 1, "Persistence fixture only", 0, List.of()));
                    }
                    return versions;
                }));
                start.countDown(); Set<Integer> versions = new TreeSet<>();
                for (var future : futures) versions.addAll(future.get(10, TimeUnit.SECONDS));
                check(versions.size() == 40 && versions.contains(2) && versions.contains(41), "concurrent repositories allocate 40 distinct consecutive versions while writing results");
            } finally { start.countDown(); submitters.shutdownNow(); submitters.awaitTermination(5, TimeUnit.SECONDS); }

            // Manager loads source/check history only for the selected room.
            Room second = core.createRoom(owner, "Other room");
            Map<String, ServerMonitor.RoomView> overview = ServerMonitor.capture(core, second.getRoomId());
            check(overview.get(second.getRoomId()).detailsLoaded() && !overview.get(room.getRoomId()).detailsLoaded(), "monitor scopes detail loading to selected room");
            check(ServerMonitor.capture(core, "").values().stream().noneMatch(ServerMonitor.RoomView::detailsLoaded), "initial overview avoids loading all branch sources");
        } finally {
            if (oldDocker == null) System.clearProperty("studyfix.assignment.docker"); else System.setProperty("studyfix.assignment.docker", oldDocker);
        }
        LoginLockTest.run(temp.resolve("login.db"));
        System.out.println("ServerReliabilityIntegrationTest: " + checks + " checks passed");
    }
}
