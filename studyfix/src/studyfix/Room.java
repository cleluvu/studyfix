package studyfix;

import java.io.IOException;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class Room {
    private final String roomId;
    private final String ownerId;
    private final Server server;
    private final Map<String, ClientHandler> members = new LinkedHashMap<>();
    private final Deque<String> queue = new ArrayDeque<>();
    private String editor;
    private EditSession draft;
    private long token = 0;
    private long version = 0;
    // Mỗi bản ghi bất biến lưu tên và code tại thời điểm được chấp nhận.
    private final NavigableMap<Long, CodeRevision> history = new TreeMap<>();
    static final String INITIAL_CODE = "public class Main {\n"
            + "    public static int sum(int[] a) {\n"
            + "        int total = 0;\n"
            + "        for (int i = 0; i <= a.length; i++) {\n"
            + "            total += a[i];\n"
            + "        }\n"
            + "        return total;\n"
            + "    }\n}\n";

    private String code;
    Room(Server server, Store.StoredRoom stored) {
        this.server = server;
        this.roomId = stored.id();
        this.ownerId = stored.owner();
        this.code = stored.code();
        this.version = stored.version();
        for (CodeRevision revision : stored.history()) history.put(revision.getVersion(), revision);
    }
    public String getRoomId() { return roomId; }
    public synchronized boolean isMember(String sessionId) { return members.containsKey(sessionId); }
    public boolean isOwner(Identity identity) {
        return identity != null && identity.role() == Identity.Role.TEACHER && ownerId.equals(identity.userId());
    }
    public synchronized boolean canEdit(String sessionId, long suppliedToken) {
        return members.containsKey(sessionId) && sessionId.equals(editor) && suppliedToken == token;
    }
    public record Snapshot(String roomId, String ownerId, String code, long version,
                           String editor, long token, List<String> members, List<String> queue,
                           List<CodeRevision> history) { }
    public synchronized Snapshot snapshot() {
        List<String> people = new ArrayList<>();
        members.forEach((id, client) -> people.add(id + " | " + client.getDisplayName()));
        return new Snapshot(roomId, ownerId, code, version, editor, token,
                List.copyOf(people), List.copyOf(queue), List.copyOf(history.values()));
    }
    public synchronized void addMember(ClientHandler client) {
        members.put(client.getMemberId(), client);
        broadcast("[Hệ thống] " + client.getDisplayName()
                + " đã vào phòng " + roomId + ".");
        broadcast("[Thành viên] " + getMembers());
    }
    public synchronized void removeMember(String id) {
        ClientHandler removed = members.remove(id);
        if (removed == null) { return; }
        queue.remove(id);
        if (id.equals(editor)) { nextEditor(); }
        broadcast("[Hệ thống] " + removed.getDisplayName()
                + " đã rời phòng " + roomId + ".");
        broadcast("[Thành viên] " + getMembers());
        broadcast(state());
    }
    public synchronized String getMembers() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : members.values()) {
            names.add(client.getDisplayName());
        }
        return String.join(", ", names);
    }
    private String nameOf(String memberId) {
        ClientHandler client = members.get(memberId);
        return client == null ? memberId : client.getDisplayName();
    }
    private String state() {
        List<String> waitingNames = new ArrayList<>();
        for (String memberId : queue) { waitingNames.add(nameOf(memberId)); }
        return "[Phòng " + roomId + "] version=" + version + ", editor="
                + (editor == null ? "không có" : nameOf(editor)) + ", token=" + token
                + ", hàng đợi=" + waitingNames;
    }
    public synchronized void showCode(ClientHandler client) throws IOException {
        if (!isMember(client.getMemberId())) { client.send("Bạn chưa tham gia phòng."); return; }
        client.send(state() + "\n--- CODE CHUNG ---\n" + code);
    }
    public synchronized void showNumberedCode(ClientHandler client) throws IOException {
        if (!isMember(client.getMemberId())) { client.send("Bạn chưa tham gia phòng."); return; }
        StringBuilder out = new StringBuilder(state() + "\n--- SỐ DÒNG THEO VERSION HIỆN TẠI ---\n");
        List<String> lines = CodeDiff.lines(code);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replaceFirst("[\\r\\n]+$", "");
            out.append(i + 1).append(" | ").append(line).append('\n');
        }
        client.sendLong(out.toString());
    }
    public synchronized void requestEdit(ClientHandler client) throws IOException {
        String id = client.getMemberId();
        if (!client.isConnected() || !members.containsKey(id)) { client.send("Bạn chưa tham gia phòng."); return; }
        if (id.equals(editor)) { client.send("Bạn đang giữ quyền sửa. " + state()); return; }
        if (editor == null) { editor = id; token++; broadcast(state()); }
        else if (queue.contains(id)) { client.send("Bạn đã ở trong hàng đợi. " + state()); }
        else { queue.addLast(id); broadcast(state()); }
    }
    // Chỉ được gọi khi giữ khóa của Room.
    private void nextEditor() {
        draft = null; // Hủy bản nháp khi kết thúc lượt hoặc mất kết nối.
        editor = null;
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (members.containsKey(id) && members.get(id).isConnected()) { editor = id; break; }
        }
        token++; // Vô hiệu hóa quyền của lượt sửa trước.
    }
    public synchronized void releaseEdit(ClientHandler client, long suppliedToken) throws IOException {
        if (!client.isConnected() || !client.getMemberId().equals(editor) || suppliedToken != token) {
            client.send("Từ chối trả quyền: bạn không giữ lượt sửa hiện tại. " + state()); return;
        }
        if (draft != null) { client.send("Còn bản nháp. Dùng /finish token hoặc /cancel token."); return; }
        nextEditor();
        broadcast(state());
    }
    public synchronized void updateCode(ClientHandler client, long expectedVersion,
                                        long suppliedToken, String newCode) throws IOException {
        if (!isOwner(client.identity())) { client.send("Chỉ giáo viên chủ phòng được nạp toàn bộ code. Học sinh dùng /select."); return; }
        if (draft != null) { client.send("Phải hoàn tất hoặc hủy bản nháp trước khi nạp code."); return; }
        if (!client.isConnected() || !canEdit(client.getMemberId(), suppliedToken)) {
            client.send("Từ chối cập nhật: bạn không giữ lượt sửa hiện tại. " + state()); return;
        }
        if (expectedVersion != version) {
            client.send("Từ chối cập nhật: phiên bản cũ. Nhập /code để xem bản mới. " + state()); return;
        }
        if (newCode.trim().isEmpty() || newCode.getBytes(StandardCharsets.UTF_8).length > 32000) {
            client.send("Code phải có nội dung và tối đa 32000 byte UTF-8."); return;
        }
        CodeRevision revision = new CodeRevision(version + 1,
                client.identity().userId(), client.getDisplayName(), newCode,
                java.time.Instant.now(), version, "Nạp toàn bộ code bởi giáo viên.\n" + CodeDiff.display(CodeDiff.between(code, newCode)));
        try { server.store().saveRevision(roomId, version, revision); }
        catch (java.sql.SQLException e) {
            server.log("Lưu code thất bại phòng " + roomId + ": " + e.getMessage());
            client.send("[SAVE_FAILED] Không lưu được code; version và lịch sử chưa thay đổi.");
            return;
        }
        code = newCode;
        version = revision.getVersion();
        history.put(version, revision);
        server.log("Cập nhật phòng " + roomId + " version=" + version + " userId=" + revision.getEditorId());
        broadcast(state() + "\n--- CODE CHUNG ĐÃ CẬP NHẬT ---\n" + code);
    }
    /** Console and future UI both use these server-authoritative operations. */
    public synchronized void selectLines(ClientHandler client, long expected, long suppliedToken,
                                         String selection) throws IOException {
        if (!validEditor(client, suppliedToken)) return;
        if (draft != null) { client.send("Đã có bản nháp; hoàn tất hoặc hủy trước khi chọn lại."); return; }
        if (expected != version) { client.send("Từ chối: version cũ. Xem /code."); return; }
        try {
            draft = new EditSession(code, version, selection);
            client.sendLong(draft.status());
        } catch (IllegalArgumentException e) { client.send(e.getMessage()); }
    }
    private boolean validEditor(ClientHandler client, long suppliedToken) throws IOException {
        if (!client.isConnected() || !canEdit(client.getMemberId(), suppliedToken)) {
            client.send("Từ chối: bạn không giữ lượt sửa hiện tại. " + state()); return false;
        }
        return true;
    }
    private boolean validDraft(ClientHandler client, long suppliedToken) throws IOException {
        if (!validEditor(client, suppliedToken)) return false;
        if (draft == null) { client.send("Chưa có bản nháp. Dùng /select version token 2,5,8 hoặc 4-7."); return false; }
        if (draft.version() != version) { client.send("Version gốc đã thay đổi; hủy bản nháp và chọn lại."); return false; }
        return true;
    }
    public synchronized void editDraft(ClientHandler client, long suppliedToken, String action,
                                       int line, String text) throws IOException {
        if (!validDraft(client, suppliedToken)) return;
        try {
            switch (action) {
                case "setline" -> draft.setLine(line, text);
                case "delete" -> draft.deleteLine(line);
                case "keep" -> draft.keepLine(line);
                case "setrange" -> draft.setRange(text);
                case "deleterange" -> draft.deleteRange();
                case "keeprange" -> draft.keepRange();
                default -> throw new IllegalArgumentException("Thao tác bản nháp không hợp lệ.");
            }
            client.sendLong(draft.status());
        } catch (IllegalArgumentException e) { client.send(e.getMessage()); }
    }
    public synchronized void showDraft(ClientHandler client, long suppliedToken) throws IOException {
        if (validDraft(client, suppliedToken)) client.sendLong(draft.status() + "\n--- XEM TRƯỚC CODE (CHƯA LƯU) ---\n" + draft.build());
    }
    public synchronized void cancelDraft(ClientHandler client, long suppliedToken) throws IOException {
        if (!validEditor(client, suppliedToken)) return;
        nextEditor();
        broadcast("[CANCELLED] Đã hủy lượt sửa; code và lịch sử không thay đổi.\n" + state());
    }
    public synchronized void finishDraft(ClientHandler client, long suppliedToken) throws IOException {
        if (!validDraft(client, suppliedToken)) return;
        if (!draft.complete()) { client.send("Chưa xử lý đủ lựa chọn; sửa, xóa hoặc giữ nguyên từng dòng trước khi hoàn tất."); return; }
        String newCode = draft.build();
        if (newCode.getBytes(StandardCharsets.UTF_8).length > 32000) { client.send("Code vượt 32000 byte UTF-8."); return; }
        if (newCode.equals(code)) {
            nextEditor();
            broadcast("[NO_CHANGE] Không có thay đổi; không tăng version, không tạo lịch sử.\n" + state());
            return;
        }
        CodeRevision revision = new CodeRevision(version + 1, client.identity().userId(),
                client.getDisplayName(), newCode, java.time.Instant.now(), version, draft.changes());
        try { server.store().saveRevision(roomId, version, revision); }
        catch (java.sql.SQLException e) {
            server.log("Lưu lượt sửa thất bại phòng " + roomId + ": " + e.getMessage());
            client.send("[SAVE_FAILED] Chưa lưu; giữ bản nháp và quyền sửa để thử lại."); return;
        }
        code = newCode; version = revision.getVersion(); history.put(version, revision);
        nextEditor();
        server.log("Hoàn tất lượt sửa phòng " + roomId + " version=" + version + " userId=" + revision.getEditorId());
        broadcast("[COMMITTED] Đã lưu toàn bộ lượt sửa.\n" + state() + "\n--- CODE CHUNG ĐÃ CẬP NHẬT ---\n" + code);
    }
    public synchronized void showHistory(ClientHandler client) throws IOException {
        if (!members.containsKey(client.getMemberId())) {
            client.send("Bạn chưa tham gia phòng.");
            return;
        }
        StringBuilder page = new StringBuilder("[Lịch sử phòng " + roomId + "] "
                + history.size() + " phiên bản; hiện tại=" + version
                + "\nThời gian hiển thị UTC+07:00. Xem code: /history số_phiên_bản\n");
        for (CodeRevision revision : history.values()) page.append(revision.summary()).append("\n");
        client.sendLong(page.toString());
    }

    public synchronized void showRevision(ClientHandler client, long revisionVersion)
            throws IOException {
        if (!members.containsKey(client.getMemberId())) {
            client.send("Bạn chưa tham gia phòng.");
            return;
        }
        CodeRevision revision = history.get(revisionVersion);
        if (revision == null) {
            client.send("Không tìm thấy phiên bản " + revisionVersion
                    + " trong phòng " + roomId + ".");
            return;
        }
        client.sendLong("[Xem lịch sử phòng " + roomId + "]\n"
                + "Đây là bản chỉ xem; code hiện tại vẫn ở phiên bản " + version + ".\n"
                + revision.detail());
    }

    public synchronized void broadcast(String message) {
        for (ClientHandler client : members.values()) {
            try { client.send(message); }
            catch (IOException e) {
                // Handler sẽ dọn thành viên và chuyển quyền trong finally.
                client.closeConnection();
            }
        }
    }
}
