package studyfix.assignment.model;

/** A contest submission is attached to a catalog exercise, not to an ordinary assignment. */
public record ContestSubmissionRecord(
        String id,
        String contestId,
        String exerciseId,
        String roomId,
        String userId,
        String username,
        String source,
        long submittedAt,
        String status,
        double score,
        double maxScore,
        String verdict,
        int passedTests,
        int totalTests,
        String feedback,
        long durationMillis,
        String idempotencyKey) {
}
