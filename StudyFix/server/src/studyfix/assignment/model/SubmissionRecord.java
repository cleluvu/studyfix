package studyfix.assignment.model;
public record SubmissionRecord(String id,String assignmentId,String roomId,String userId,String username,String source,long submittedAt,String status,double score,double maxScore,String verdict,int passedTests,int totalTests,String feedback,long durationMillis,String submissionMode,String idempotencyKey) {}
