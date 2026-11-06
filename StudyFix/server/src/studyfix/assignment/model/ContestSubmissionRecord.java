package studyfix.assignment.model;

/** Bài nộp cuộc thi gắn với bài trong ngân hàng đề, không gắn với bài giao thông thường. */
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
