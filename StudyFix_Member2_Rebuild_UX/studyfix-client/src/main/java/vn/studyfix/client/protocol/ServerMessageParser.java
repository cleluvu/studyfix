package vn.studyfix.client.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser for the existing Vietnamese text protocol. Do not assume JSON responses. */
public final class ServerMessageParser {
    private static final Pattern LOGIN = Pattern.compile("^\\[LOGIN_OK] session=([^ ]+) userId=([^ ]+) role=([^ ]+) \\| ?(.*)$", Pattern.DOTALL);
    private static final Pattern STATE = Pattern.compile("\\[Phòng ([^]]+)] version=(\\d+), editor=(.*?), token=(-?\\d+), hàng đợi=\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern MEMBER_ENTRY = Pattern.compile("([^,\\[\\]]+)\\s*\\|\\s*([^,\\[\\]]+)");
    private static final Pattern CHAT = Pattern.compile("^\\[Phòng ([^]]+)] ([^:]+): ([\\s\\S]*)$");
    private static final Pattern SYSTEM = Pattern.compile("^\\[Hệ thống]([\\s\\S]*)$");
    private static final Pattern SCREEN_TICKET = Pattern.compile("^\\[SCREEN_TICKET]\\s+mode=([^ ]+)\\s+room=([^ ]+)\\s+port=(\\d+)\\s+ticket=(.+)$");

    public ServerMessage parse(String raw) {
        if (raw == null) raw = "";
        if (raw.startsWith("[LOGIN_OK]")) {
            Matcher m = LOGIN.matcher(raw);
            if (m.matches()) return message(ServerMessage.Type.LOGIN_OK, raw, null, null, null, null, null, null, null,
                    m.group(1), m.group(2), m.group(3), m.group(4));
            return generic(raw);
        }
        if (raw.startsWith("[LOGIN_FAILED]")) return genericType(ServerMessage.Type.LOGIN_FAILED, raw);
        if (raw.equals("/pong")) return genericType(ServerMessage.Type.PONG, raw);
        if (raw.startsWith("[SCREEN_TICKET]")) return genericType(ServerMessage.Type.SCREEN_TICKET, raw);
        if (raw.startsWith("[SCREEN_SHARE]")) return genericType(ServerMessage.Type.SCREEN_EVENT, raw);

        String room = null, editor = null, code = null;
        Long version = null, token = null;
        List<String> queue = List.of();
        // State may be line 1 or follow [COMMITTED]/[CANCELLED] in a compound broadcast.
        for (String line : raw.split("\\R")) {
            Matcher state = STATE.matcher(line);
            if (state.find()) {
                room = state.group(1); version = Long.parseLong(state.group(2)); editor = state.group(3).trim();
                token = Long.parseLong(state.group(4)); queue = splitQueue(state.group(5));
                break;
            }
        }
        if (raw.contains("--- CODE CHUNG ---")) {
            code = afterMarker(raw, "--- CODE CHUNG ---");
            return message(ServerMessage.Type.SHARED_CODE, raw, room, version, editor, token, queue, code, null, null, null, null, null);
        }
        if (raw.contains("--- CODE CHUNG ĐÃ CẬP NHẬT ---")) {
            code = afterMarker(raw, "--- CODE CHUNG ĐÃ CẬP NHẬT ---");
            return message(ServerMessage.Type.SHARED_CODE, raw, room, version, editor, token, queue, code, null, null, null, null, null);
        }
        if (raw.contains("--- SỐ DÒNG THEO VERSION HIỆN TẠI ---"))
            return message(ServerMessage.Type.NUMBERED_CODE, raw, room, version, editor, token, queue, null, null, null, null, null, null);
        if (room != null)
            return message(ServerMessage.Type.ROOM_STATE, raw, room, version, editor, token, queue, null, null, null, null, null, null);
        if (raw.startsWith("[Thành viên]")) {
            return message(ServerMessage.Type.MEMBERS, raw, null, null, null, null, null, null,
                    splitNames(raw.substring("[Thành viên]".length()).trim()), null, null, null, null);
        }
        if (raw.startsWith("Thành viên phòng ")) {
            return message(ServerMessage.Type.MEMBERS, raw, null, null, null, null, null, null,
                    parseMemberSnapshot(raw), null, null, null, null);
        }
        if (raw.startsWith("[Lịch sử phòng") || raw.startsWith("[Xem lịch sử phòng"))
            return genericType(ServerMessage.Type.HISTORY, raw);
        if (raw.startsWith("[Bản nháp]") || raw.contains("--- XEM TRƯỚC CODE (CHƯA LƯU) ---"))
            return genericType(ServerMessage.Type.DRAFT, raw);
        Matcher chat = CHAT.matcher(raw);
        if (chat.matches()) return message(ServerMessage.Type.CHAT, raw, chat.group(1), null, null, null, null, null, null, null, null, null, null);
        if (SYSTEM.matcher(raw).matches()) return genericType(ServerMessage.Type.SYSTEM, raw);
        return generic(raw);
    }

    private static String afterMarker(String raw, String marker) {
        int index = raw.indexOf(marker);
        int start = index + marker.length();
        if (start < raw.length() && raw.charAt(start) == '\n') start++;
        return raw.substring(start);
    }
    private static List<String> splitQueue(String text) {
        if (text == null || text.isBlank()) return List.of();
        return splitNames(text);
    }
    private static List<String> splitNames(String text) {
        if (text == null || text.isBlank() || "[]".equals(text)) return List.of();
        String value = text.trim();
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length()-1);
        List<String> result = new ArrayList<>();
        for (String name : value.split(",\\s*")) if (!name.isBlank()) result.add(name.trim());
        return result;
    }
    private static List<String> parseMemberSnapshot(String raw) {
        List<String> names = new ArrayList<>();
        Matcher m = MEMBER_ENTRY.matcher(raw);
        while (m.find()) names.add(m.group(2).trim() + "  ·  " + m.group(1).trim());
        if (names.isEmpty()) {
            int colon = raw.indexOf(':'); if (colon >= 0) return splitNames(raw.substring(colon + 1));
        }
        return names;
    }
    private static ServerMessage generic(String raw) { return genericType(ServerMessage.Type.GENERIC, raw); }
    private static ServerMessage genericType(ServerMessage.Type type, String raw) {
        return message(type, raw, null, null, null, null, null, null, null, null, null, null, null);
    }
    private static ServerMessage message(ServerMessage.Type type, String raw, String room, Long version,
                                         String editor, Long token, List<String> queue, String code,
                                         List<String> members, String session, String user, String role, String display) {
        return new ServerMessage(type, raw, room, version, editor, token, queue, code, members, session, user, role, display);
    }
}
