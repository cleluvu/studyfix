package vn.studyfix.client.protocol;

import java.util.List;

public record ServerMessage(Type type, String raw, String roomId, Long version,
                            String editorName, Long token, List<String> queue,
                            String code, List<String> memberNames,
                            String sessionId, String userId, String role, String displayName) {
    public enum Type { LOGIN_OK, LOGIN_FAILED, PONG, ROOM_STATE, SHARED_CODE, NUMBERED_CODE,
        MEMBERS, CHAT, SYSTEM, HISTORY, DRAFT, SCREEN_TICKET, SCREEN_EVENT, GENERIC }
    public ServerMessage {
        queue = queue == null ? List.of() : List.copyOf(queue);
        memberNames = memberNames == null ? List.of() : List.copyOf(memberNames);
    }
    public boolean hasRoomState() { return roomId != null && version != null; }
}
