package studyfix.assignment.integration;

/** Cho module bài tập đọc trạng thái code chung do phần lõi StudyFix quản lý. */
public interface SharedRoomCodeProvider {
    record Snapshot(String roomId, String source, long version, boolean draftActive,
                    boolean editTurnActive) {}
    Snapshot snapshot(String sessionId, String roomId);
    long loadStarterCode(String sessionId, String roomId, long expectedVersion,
                         String starterCode, String assignmentTitle);
}
