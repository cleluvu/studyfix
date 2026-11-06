package vn.studyfix.client.model;

public record SessionInfo(String username, String displayName, String sessionId,
                          String userId, String role) {
    public boolean isTeacher() { return "TEACHER".equalsIgnoreCase(role); }
}
