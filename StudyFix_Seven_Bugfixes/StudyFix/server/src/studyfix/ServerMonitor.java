package studyfix;

import studyfix.assignment.protocol.AssignmentJson;
import java.sql.SQLException;
import java.util.*;

/** Read-only data for Server Manager. Capture on its background thread, never on the EDT. */
final class ServerMonitor {
    record BranchView(String key, String summary, String detail) { }
    record RoomView(String code, String owner, String phase, int round, List<BranchView> branches, boolean detailsLoaded) { }

    static Map<String, RoomView> capture(Server server) throws SQLException {
        return capture(server, null);
    }

    /** null means all details (tests/tools); UI passes only its selected room, or "" before selection. */
    static Map<String, RoomView> capture(Server server, String selectedRoomId) throws SQLException {
        Map<String, RoomView> result = new HashMap<>();
        Map<String, String> owners = new HashMap<>();
        ClassroomRepository repo = new ClassroomRepository(server.store());
        for (Room room : server.rooms.values()) {
            boolean loadDetails = selectedRoomId == null || room.getRoomId().equals(selectedRoomId);
            Room.Snapshot snapshot = room.snapshot();
            String owner = owners.get(snapshot.ownerId());
            if (owner == null) { owner = server.store().userLabel(snapshot.ownerId()); owners.put(snapshot.ownerId(), owner); }
            ClassroomRepository.Session session;
            List<ClassroomRepository.Branch> savedBranches;
            Map<String, List<ClassroomRepository.Check>> savedChecks = new HashMap<>();
            synchronized (room) {
                if (room.isExpired()) continue;
                session = repo.session(room.getRoomId());
                savedBranches = loadDetails ? repo.branches(session) : List.of();
                for (ClassroomRepository.Branch branch : savedBranches)
                    savedChecks.put(branch.userId(), repo.checks(session, branch.userId(), 20));
            }
                // Formatting full source/history is deliberately outside the Room lock.
                List<BranchView> branches = new ArrayList<>();
                for (ClassroomRepository.Branch branch : savedBranches) {
                    List<ClassroomRepository.Check> checks = savedChecks.get(branch.userId());
                    String status = checks.isEmpty() ? "Chưa kiểm tra" : checkLabel(checks.getFirst());
                    if (!checks.isEmpty() && checks.getFirst().branchVersion() != branch.version())
                        status += " (kết quả bản cũ)";
                    String summary = branch.name() + " (" + branch.username() + ") · v" + branch.version() + " · " + status;
                    StringBuilder detail = new StringBuilder("Phòng: ").append(room.getRoomCode())
                            .append(" | ID nội bộ: ").append(room.getRoomId())
                            .append("\nHọc sinh: ").append(branch.name()).append(" (").append(branch.username()).append(")")
                            .append("\nVòng: ").append(branch.round()).append(" | Nhánh v").append(branch.version())
                            .append(" | Từ Master v").append(branch.baseVersion())
                            .append("\nTrạng thái: ").append(status)
                            .append("\n\nĐây là nhánh riêng đã lưu. AC không tự thay Master; giáo viên cần chọn Merge.\n\nCODE NHÁNH HIỆN TẠI\n")
                            .append(branch.code()).append("\n\nLỊCH SỬ KIỂM TRA (tối đa 20 lượt gần nhất)\n");
                    for (ClassroomRepository.Check check : checks) {
                        detail.append("\nNhánh v").append(check.branchVersion()).append(" · ").append(checkLabel(check))
                                .append(" · ").append(java.time.Instant.ofEpochMilli(check.createdAt())).append('\n');
                        Map<String, Object> grading = AssignmentJson.object(check.result());
                        detail.append(AssignmentJson.text(grading, "feedback", "")).append('\n');
                    }
                    branches.add(new BranchView(branch.round() + ":" + branch.userId(), summary, detail.toString()));
                }
                result.put(room.getRoomId(), new RoomView(room.getRoomCode(), owner,
                        session.phase(), session.round(), List.copyOf(branches), loadDetails));
        }
        return Map.copyOf(result);
    }

    private static String checkLabel(ClassroomRepository.Check check) {
        Map<String, Object> grading = AssignmentJson.object(check.result());
        return check.status() + " / " + AssignmentJson.text(grading, "verdict", check.status());
    }

    /** Follow new commits only when already viewing the latest revision, or when switching rooms. */
    static int historySelection(String previousRoom, String currentRoom, int index, int oldSize, int newSize) {
        if (newSize == 0) return -1;
        if (!Objects.equals(previousRoom, currentRoom) || index < 0 || index >= oldSize - 1) return newSize - 1;
        return Math.min(index, newSize - 1);
    }
}
