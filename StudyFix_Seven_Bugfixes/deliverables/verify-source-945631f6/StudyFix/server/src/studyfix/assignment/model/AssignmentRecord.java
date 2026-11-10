package studyfix.assignment.model;

public record AssignmentRecord(String id, String exerciseId, String roomId, String createdByUserId,
                               String scope, String targetUserId, String targetUsername,
                               long createdAt, long deadlineAt, String status, ExerciseMode mode) {
    public AssignmentRecord(String id, String exerciseId, String roomId, String createdByUserId,
                            String scope, String targetUserId, String targetUsername,
                            long createdAt, long deadlineAt, String status) {
        this(id, exerciseId, roomId, createdByUserId, scope, targetUserId, targetUsername,
                createdAt, deadlineAt, status, ExerciseMode.BUILD_FROM_SCRATCH);
    }
}
