package studyfix.assignment.integration;

/** Narrow read-only bridge to the authoritative shared-code state in the StudyFix core. */
public interface SharedRoomCodeProvider {
    record Snapshot(String roomId, String source, long version, boolean draftActive,
                    boolean editTurnActive) {}
    Snapshot snapshot(String sessionId, String roomId);
    long loadStarterCode(String sessionId, String roomId, long expectedVersion,
                         String starterCode, String assignmentTitle);
}
