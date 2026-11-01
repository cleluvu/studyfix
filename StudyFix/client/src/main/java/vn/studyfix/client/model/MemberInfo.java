package vn.studyfix.client.model;

public record MemberInfo(String sessionId, String displayName) {
    @Override public String toString() {
        return sessionId == null || sessionId.isBlank() ? displayName : displayName + "  ·  " + sessionId;
    }
}
