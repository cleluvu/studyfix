package vn.studyfix.client.model;

import java.util.List;

/** Client-side view of server-owned state. This object never grants permissions. */
public final class RoomState {
    private String roomId;
    private long version = -1;
    private String editorName = "Chưa xác định";
    private long token = -1;
    private List<String> queue = List.of();
    private List<MemberInfo> members = List.of();
    private String sharedCode = "";
    private String lastDraftStatus = "";
    private String lastHistory = "";

    public String roomId() { return roomId; }
    public long version() { return version; }
    public String editorName() { return editorName; }
    public long token() { return token; }
    public List<String> queue() { return queue; }
    public List<MemberInfo> members() { return members; }
    public String sharedCode() { return sharedCode; }
    public String lastDraftStatus() { return lastDraftStatus; }
    public String lastHistory() { return lastHistory; }
    public boolean inRoom() { return roomId != null; }
    public void setRoomId(String id) { roomId = id; }
    public void setVersion(long value) { version = value; }
    public void setEditorName(String value) { editorName = value; }
    public void setToken(long value) { token = value; }
    public void setQueue(List<String> value) { queue = List.copyOf(value); }
    public void setMembers(List<MemberInfo> value) { members = List.copyOf(value); }
    public void setSharedCode(String value) { sharedCode = value == null ? "" : value; }
    public void setLastDraftStatus(String value) { lastDraftStatus = value == null ? "" : value; }
    public void setLastHistory(String value) { lastHistory = value == null ? "" : value; }
    public void clearRoom() {
        roomId = null; version = -1; editorName = "Chưa xác định"; token = -1;
        queue = List.of(); members = List.of(); sharedCode = ""; lastDraftStatus = ""; lastHistory = "";
    }
    public boolean hasEditToken() { return token >= 0 && editorName != null && !editorName.isBlank() && !"không có".equalsIgnoreCase(editorName); }
}
