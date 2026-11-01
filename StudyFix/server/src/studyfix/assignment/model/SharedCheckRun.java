package studyfix.assignment.model;

/** Persisted group check; source is server-side only and is never included in its public view. */
public record SharedCheckRun(String id, String requestKey, String assignmentId, String roomId,
                             long checkedVersion, String sourceHash, String source,
                             String requestedByUserId, String requestedByUsername,
                             long createdAt, String status, String verdict, double score,
                             double maxScore, int passedTests, int totalTests,
                             String feedback, long durationMillis) {
}
