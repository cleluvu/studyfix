package studyfix;

/** Server-owned identity; passwords never cross this integration boundary. */
public record Identity(String userId, String username, String displayName, Role role) {
    public enum Role { TEACHER, STUDENT }
}
