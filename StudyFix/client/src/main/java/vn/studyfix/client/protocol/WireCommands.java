package vn.studyfix.client.protocol;

import java.nio.charset.StandardCharsets;

/** Centralized commands supported by the server's existing ClientHandler. */
public final class WireCommands {
    private WireCommands() { }
    public static String login(String username, String password) {
        if (username == null || username.isBlank() || username.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Username không được trống hoặc chứa khoảng trắng.");
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("Mật khẩu không được trống.");
        if (username.length() > 64 || password.length() > 256) throw new IllegalArgumentException("Tài khoản hoặc mật khẩu vượt giới hạn server.");
        return "/login " + username + " " + password;
    }
    public static String join(String roomId) {
        String id = requireSingleToken(roomId, "Mã phòng"); return "/join " + id;
    }
    public static String chat(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Tin nhắn không được trống.");
        if (text.length() > 2000) throw new IllegalArgumentException("Tin chat tối đa 2000 ký tự Java.");
        return "/chat " + text.trim();
    }
    public static String select(long version, long token, String selection) {
        if (version < 0 || token < 0) throw new IllegalArgumentException("Chưa nhận được version/token hợp lệ từ server.");
        String spec = selection == null ? "" : selection.trim();
        if (!spec.matches("[0-9]+(,[0-9]+)*|[0-9]+-[0-9]+")) throw new IllegalArgumentException("Chọn dòng dạng 2,5,8 hoặc đoạn 4-7.");
        return "/select " + version + " " + token + " " + spec;
    }
    public static String setLine(long token, int line, String text) {
        if (token < 0 || line < 1) throw new IllegalArgumentException("Token/dòng không hợp lệ.");
        if (text == null) text = "";
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) throw new IllegalArgumentException("Sửa một dòng không nhận xuống dòng; hãy dùng sửa đoạn.");
        if (text.getBytes(StandardCharsets.UTF_8).length > 32000) throw new IllegalArgumentException("Code vượt 32000 byte UTF-8.");
        return "/setline " + token + " " + line + " " + text;
    }
    public static String lineAction(String action, long token, int line) {
        if (!(action.equals("delete") || action.equals("keep"))) throw new IllegalArgumentException("Thao tác không hỗ trợ.");
        if (token < 0 || line < 1) throw new IllegalArgumentException("Token/dòng không hợp lệ.");
        return "/" + action + " " + token + " " + line;
    }
    public static String rangeAction(String action, long token, String text) {
        if (token < 0) throw new IllegalArgumentException("Token không hợp lệ.");
        return switch (action) {
            case "setrange" -> {
                String replacement = text == null ? "" : text;
                if (replacement.getBytes(StandardCharsets.UTF_8).length > 32000) throw new IllegalArgumentException("Code vượt 32000 byte UTF-8.");
                yield "/setrange " + token + " " + replacement;
            }
            case "deleterange", "keeprange" -> "/" + action + " " + token;
            default -> throw new IllegalArgumentException("Thao tác đoạn không hỗ trợ.");
        };
    }
    public static String update(long version, long token, String code) {
        if (version < 0 || token < 0) throw new IllegalArgumentException("Version/token không hợp lệ.");
        if (code == null || code.trim().isEmpty()) throw new IllegalArgumentException("Code không được rỗng.");
        if (code.getBytes(StandardCharsets.UTF_8).length > 32000) throw new IllegalArgumentException("Code vượt 32000 byte UTF-8.");
        return "/update " + version + " " + token + " " + code;
    }
    private static String requireSingleToken(String value, String label) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException(label + " không được trống hoặc chứa khoảng trắng.");
        return value.trim();
    }
}
