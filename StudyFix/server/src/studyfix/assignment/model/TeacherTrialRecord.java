package studyfix.assignment.model;

/** Isolated teacher-only trial; it never contributes to student assignment/contest statistics. */
public record TeacherTrialRecord(
        String id, String exerciseId, String roomId, String teacherUserId, String teacherUsername,
        String source, long submittedAt, String status, double score, double maxScore,
        String verdict, int passedTests, int totalTests, String feedback, long durationMillis,
        String idempotencyKey) { }
