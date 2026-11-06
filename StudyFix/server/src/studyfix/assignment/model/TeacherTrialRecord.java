package studyfix.assignment.model;

/** Lượt chạy thử riêng của giáo viên, không tính vào thống kê bài tập hoặc cuộc thi của sinh viên. */
public record TeacherTrialRecord(
        String id, String exerciseId, String roomId, String teacherUserId, String teacherUsername,
        String source, long submittedAt, String status, double score, double maxScore,
        String verdict, int passedTests, int totalTests, String feedback, long durationMillis,
        String idempotencyKey) { }
