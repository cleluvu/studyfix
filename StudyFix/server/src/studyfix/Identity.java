package studyfix;

/** Server quản lý thông tin người dùng. Không truyền mật khẩu qua lớp tích hợp này. */
public record Identity(String userId, String username, String displayName, Role role) {
    public enum Role { TEACHER, STUDENT }
}
