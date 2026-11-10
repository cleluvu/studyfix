package studyfix.assignment.model;

/** Lưu kết quả chấm code chung. Code chỉ giữ ở server, không đưa vào dữ liệu công khai. */
public record SharedCheckRun(String id, String requestKey, String assignmentId, String roomId,
                             long checkedVersion, String sourceHash, String source,
                             String requestedByUserId, String requestedByUsername,
                             long createdAt, String status, String verdict, double score,
                             double maxScore, int passedTests, int totalTests,
                             String feedback, long durationMillis) {
}
