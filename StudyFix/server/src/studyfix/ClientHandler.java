package studyfix;

import studyfix.screenshare.ScreenShareRelay;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// Mỗi kết nối có một đối tượng và một luồng xử lý riêng.
public class ClientHandler implements Runnable {
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(20);
    private volatile long lastHeartbeat = System.nanoTime();

    private final Socket socket;
    private final Server server;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String memberId;
    private volatile Room currentRoom;
    private volatile Identity identity;
    private final BlockingQueue<String> outbound = new ArrayBlockingQueue<>(64);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Thread writer;
    private volatile String closeReason = "Client đóng kết nối";
    private int failedLogins;

    public ClientHandler(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        socket.setTcpNoDelay(true);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.memberId = UUID.randomUUID().toString();
    }

    public String getMemberId() { return memberId; }
    public Identity identity() { return identity; }
    /** Current room identifier for the in-process web gateway; never exposed as a credential. */
    public String getCurrentRoomId() { Room room = currentRoom; return room == null ? null : room.getRoomId(); }
    public boolean isConnected() { return !closed.get(); }
    public String getDisplayName() { return identity == null ? "Chưa đăng nhập" : identity.displayName(); }
    public void send(String message) throws IOException {
        if (message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > Protocol.MAX_BYTES)
            throw new IOException("Thông điệp vượt giới hạn 64 KiB");
        enqueue(message);
    }
    public void sendLong(String message) throws IOException {
        // One queue entry per response: writer emits every part before the next event.
        enqueue(message);
    }
    private void enqueue(String message) throws IOException {
        if (closed.get()) throw new IOException("Kết nối đã đóng");
        if (!outbound.offer(message)) {
            closeReason = "Client đọc chậm: đầy hàng đợi gửi; đóng kết nối để đồng bộ lại";
            server.log(closeReason + " session=" + memberId);
            closeConnection();
            throw new IOException(closeReason);
        }
    }
    private void writeLoop() {
        writer = Thread.currentThread();
        try {
            while (!closed.get()) {
                String message = outbound.take();
                for (String part : Protocol.historyFrames(message)) Protocol.send(out, part);
            }
        }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException e) { closeReason = "Không gửi được dữ liệu"; }
        finally { closeConnection(); }
    }
    public void closeConnection() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
        Thread thread = writer;
        if (thread != null) thread.interrupt();
    }

    private boolean requireOutsideRoom() throws IOException {
        if (currentRoom != null) {
            send("Bạn đang ở phòng " + currentRoom.getRoomId() + ". Nhập /leave trước.");
            return false;
        }
        return true;
    }

    private synchronized void leaveRoom() {
        if (currentRoom != null) {
            Room previous = currentRoom;
            currentRoom = null;
            previous.removeMember(memberId);
            server.departed(memberId, identity, previous.getRoomId(), closeReason);
        }
    }

    synchronized void leaveCurrentRoom() {
        closeReason="Rời phòng theo yêu cầu";
        leaveRoom();
    }

    synchronized void leaveDeletedRoom(Room room) {
        if(currentRoom!=room)return;
        closeReason="Chủ phòng đã xóa phòng";
        leaveRoom();
        try {send("[ROOM_DELETED] "+room.getRoomId());}
        catch(IOException ignored) { /* A disconnected member is already detached. */ }
    }

    synchronized Room createAndJoinRoom(String code) throws java.sql.SQLException {
        if(!isConnected()||identity==null)throw new SecurityException("Phiên đăng nhập đã đóng.");
        if(identity.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ giáo viên được tạo phòng.");
        if(currentRoom!=null)throw new IllegalStateException("Bạn đang ở trong một phòng. Hãy rời phòng hiện tại trước.");
        Room room=server.createRoom(identity,code);
        currentRoom=room;closeReason="Client đóng kết nối";room.addMember(this);return room;
    }
    private synchronized Room joinExistingRoom(String code) {
        if(!isConnected()||identity==null)throw new SecurityException("Phiên đăng nhập đã đóng.");
        if(currentRoom!=null)throw new IllegalStateException("Hãy rời phòng hiện tại trước.");
        Room room=server.findRoom(code);if(room==null)return null;
        synchronized(room) {
            if(room.isExpired())return null;
            currentRoom=room;closeReason="Client đóng kết nối";room.addMember(this);return room;
        }
    }
    private boolean handleDraftCommand(String raw) throws IOException {
        String verb = raw.split("[ \t]", 2)[0];
        if (!java.util.Set.of("/select", "/setline", "/delete", "/keep", "/setrange",
                "/deleterange", "/keeprange", "/draft", "/finish", "/cancel").contains(verb)) return false;
        if (currentRoom == null) { send("Bạn chưa tham gia phòng."); return true; }
        try {
            if (verb.equals("/select")) {
                String[] p = raw.trim().split("\\s+");
                if (p.length != 4) throw new IllegalArgumentException();
                currentRoom.selectLines(this, Long.parseLong(p[1]), Long.parseLong(p[2]), p[3]);
            } else if (verb.equals("/setline") || verb.equals("/setrange")) {
                String pattern = verb.equals("/setline")
                        ? "/setline[ \\t]+(-?\\d+)[ \\t]+([0-9]+)[ \\t]([\\s\\S]*)"
                        : "/setrange[ \\t]+(-?\\d+)[ \\t]([\\s\\S]*)";
                var m = java.util.regex.Pattern.compile(pattern).matcher(raw);
                if (!m.matches()) throw new IllegalArgumentException();
                currentRoom.editDraft(this, Long.parseLong(m.group(1)), verb.substring(1),
                        verb.equals("/setline") ? Integer.parseInt(m.group(2)) : 0,
                        m.group(verb.equals("/setline") ? 3 : 2));
            } else {
                String[] p = raw.trim().split("\\s+");
                boolean single = verb.equals("/delete") || verb.equals("/keep");
                if (p.length != (single ? 3 : 2)) throw new IllegalArgumentException();
                long suppliedToken = Long.parseLong(p[1]);
                switch (verb) {
                    case "/finish" -> currentRoom.finishDraft(this, suppliedToken);
                    case "/cancel" -> currentRoom.cancelDraft(this, suppliedToken);
                    case "/draft" -> currentRoom.showDraft(this, suppliedToken);
                    default -> currentRoom.editDraft(this, suppliedToken, verb.substring(1),
                            single ? Integer.parseInt(p[2]) : 0, "");
                }
            }
        } catch (IllegalArgumentException e) {
            send("Sai cú pháp. /select version token 2,5,8|4-7; /setline token dòng nội_dung; "
                    + "/delete token dòng; /keep token dòng; /setrange token nội_dung; "
                    + "/deleterange token; /keeprange token; /draft token; /finish token; /cancel token");
        }
        return true;
    }

    @Override
    public void run() {
        server.log("Client kết nối: " + memberId);
        lastHeartbeat = System.nanoTime();
        ScheduledFuture<?> monitor = null;
        try {
            server.executeWriter(this::writeLoop);
            monitor = server.monitor.scheduleAtFixedRate(() -> {
                if (!socket.isClosed()
                        && System.nanoTime() - lastHeartbeat >= TIMEOUT_NANOS) {
                    closeReason = "Hết hạn heartbeat 20 giây";
                    server.log(closeReason + ": " + memberId);
                    closeConnection();
                }
            }, 1, 1, TimeUnit.SECONDS);
            while (true) {
                String raw = Protocol.receive(in);
                String command = raw.trim();
                if (command.equals("/quit")) { closeReason = "Client thoát"; break; }
                if (command.equals("/ping")) {
                    lastHeartbeat = System.nanoTime(); send("/pong"); continue;
                }
                if (command.equals("/login") || command.startsWith("/login ")) {
                    if (identity != null) { send("Phiên đã đăng nhập; không thể đổi danh tính."); continue; }
                    String[] login = raw.split("\\s+", 3);
                    if (login.length != 3 || login[1].length() > 64 || login[2].length() > 256) {
                        send("Cách dùng: /login username password"); continue;
                    }
                    try { identity = server.store().login(login[1], login[2].toCharArray()); }
                    catch (java.sql.SQLException e) { send("[LOGIN_FAILED] Database không sẵn sàng."); continue; }
                    if (identity == null) {
                        send("[LOGIN_FAILED] Sai tài khoản hoặc mật khẩu.");
                        if (++failedLogins >= 5) { closeReason = "Quá nhiều lần đăng nhập sai"; break; }
                    } else {
                        server.log("Đăng nhập session=" + memberId + " userId=" + identity.userId());
                        send("[LOGIN_OK] session=" + memberId + " userId=" + identity.userId()
                                + " role=" + identity.role() + " | " + getDisplayName());
                    }
                    continue;
                }
                if (identity == null) {
                    send("Bạn cần đăng nhập: /login username password");
                    continue;
                }
                if (command.equals("/create") || raw.startsWith("/create ")) {
                    if (!requireOutsideRoom()) { continue; }
                    if (identity.role() != Identity.Role.TEACHER) { send("Chỉ giáo viên được tạo phòng."); continue; }
                    Room room;
                    try { room = createAndJoinRoom(command.equals("/create")?null:raw.substring(8)); }
                    catch (IllegalArgumentException | IllegalStateException e) {send(e.getMessage());continue;}
                    catch (java.sql.SQLException e) { server.log("Lỗi tạo phòng: " + e); send("[SAVE_FAILED] Không tạo được phòng."); continue; }
                    String id = room.getRoomId();
                    send("Đã tạo và tham gia phòng: " + room.getRoomCode());
                    room.showCode(this);
                } else if (command.equals("/join") || command.startsWith("/join ")) {
                    if (!requireOutsideRoom()) { continue; }
                    String id = command.substring(5).trim();
                    Room room;
                    try {room=joinExistingRoom(id);}catch(IllegalArgumentException|IllegalStateException e){send(e.getMessage());continue;}
                    if (room == null) {
                        send("Không tìm thấy phòng. Cách dùng: /join mã_phòng");
                    } else {
                        send("Đã tham gia phòng: " + room.getRoomCode());
                        room.showCode(this);
                    }
                } else if (command.startsWith("/kick ")) {
                    Room room = currentRoom;
                    if (room == null || !room.isOwner(identity)) { send("Chỉ giáo viên chủ phòng được mời thành viên ra."); continue; }
                    String target = command.substring(6).trim();
                    ClientHandler victim = server.clients.get(target);
                    synchronized (room) {
                        if (victim == null || !room.isMember(target) || victim.currentRoom != room || victim == this) {
                            send("Session không hợp lệ; xem /members."); continue;
                        }
                        victim.closeReason = "Giáo viên mời ra khỏi phòng";
                        victim.closeConnection();
                    }
                    send("Đã đóng phiên thành viên: " + target);
                } else if (command.equals("/members")) {
                    send(currentRoom == null ? "Bạn chưa tham gia phòng."
                            : "Thành viên phòng " + currentRoom.getRoomId() + ": " + currentRoom.snapshot().members());
                } else if (command.equals("/leave")) {
                    if (currentRoom == null) {
                        send("Bạn chưa tham gia phòng.");
                    } else {
                        String id = currentRoom.getRoomId();
                        closeReason = "Rời phòng theo yêu cầu";
                        leaveRoom();
                        send("Đã rời phòng: " + id);
                    }
                } else if (command.equals("/history") || command.startsWith("/history ")) {
                    if (currentRoom == null) {
                        send("Bạn chưa tham gia phòng.");
                        continue;
                    }
                    String[] parts = command.split("\\s+");
                    if (parts.length == 1) {
                        currentRoom.showHistory(this);
                    } else if (parts.length == 2) {
                        try {
                            long revisionVersion = Long.parseLong(parts[1]);
                            if (revisionVersion < 0) { throw new NumberFormatException(); }
                            currentRoom.showRevision(this, revisionVersion);
                        } catch (NumberFormatException e) {
                            send("Phiên bản phải là số nguyên không âm. Cách dùng: /history [version]");
                        }
                    } else {
                        send("Cách dùng: /history hoặc /history số_phiên_bản");
                    }
                } else if (command.equals("/screen-ticket") || command.startsWith("/screen-ticket ")) {
                    if (currentRoom == null) {
                        send("Bạn chưa tham gia phòng.");
                        continue;
                    }
                    String[] parts = command.split("\\s+");
                    if (parts.length != 2) {
                        send("Cách dùng: /screen-ticket publish|view");
                        continue;
                    }
                    ScreenShareRelay.Mode mode;
                    if (parts[1].equalsIgnoreCase("publish")) {
                        mode = ScreenShareRelay.Mode.PUBLISH;
                    } else if (parts[1].equalsIgnoreCase("view")) {
                        mode = ScreenShareRelay.Mode.VIEW;
                    } else {
                        send("Cách dùng: /screen-ticket publish|view");
                        continue;
                    }
                    try {
                        String roomId = currentRoom.getRoomId();
                        String ticket = server.screenShare().issueTicket(memberId, roomId, mode);
                        send("[SCREEN_TICKET] mode=" + mode + " room=" + roomId
                                + " port=" + server.screenSharePort() + " ticket=" + ticket);
                        server.log("Cấp ticket màn hình mode=" + mode + " room=" + roomId
                                + " session=" + memberId);
                    } catch (SecurityException e) {
                        send("Không cấp được ticket màn hình: phiên không còn là thành viên phòng.");
                    } catch (RuntimeException e) {
                        server.log("Lỗi cấp ticket màn hình: " + e.getMessage());
                        send("Không cấp được ticket màn hình.");
                    }
                } else if (handleDraftCommand(raw)) {
                    // Đã xử lý yêu cầu bản nháp.
                } else if (command.equals("/code") || command.equals("/lines") || command.equals("/request")
                        || command.equals("/teacher-request") || command.startsWith("/release") || raw.startsWith("/update")) {
                    if (currentRoom == null) { send("Bạn chưa tham gia phòng."); continue; }
                    if (command.equals("/code")) { currentRoom.showCode(this); }
                    else if (command.equals("/lines")) { currentRoom.showNumberedCode(this); }
                    else if (command.equals("/request")) { currentRoom.requestEdit(this); }
                    else if (command.equals("/teacher-request")) { currentRoom.requestEditPriority(this); }
                    else if (command.startsWith("/release")) {
                        String[] parts = command.split("\\s+");
                        try {
                            if (parts.length != 2 || !parts[0].equals("/release")) { throw new IllegalArgumentException(); }
                            currentRoom.releaseEdit(this, Long.parseLong(parts[1]));
                        } catch (IllegalArgumentException e) { send("Cách dùng: /release token"); }
                    } else {
                        // Consume exactly one separator after token; the rest is the exact code.
                        java.util.regex.Matcher update = java.util.regex.Pattern.compile(
                                "/update[ \\t]+(-?\\d+)[ \\t]+(-?\\d+)[ \\t]([\\s\\S]*)").matcher(raw);
                        try {
                            if (!update.matches()) { throw new IllegalArgumentException(); }
                            currentRoom.updateCode(this, Long.parseLong(update.group(1)), Long.parseLong(update.group(2)), update.group(3));
                        } catch (IllegalArgumentException e) { send("Cách dùng: /update version token nội_dung_code"); }
                    }
                } else if (command.equals("/chat") || raw.startsWith("/chat ")) {
                    if (currentRoom == null) {
                        send("Bạn chưa tham gia phòng.");
                        continue;
                    }
                    String text = raw.substring(5).trim();
                    if (text.isEmpty()) {
                        send("Cách dùng: /chat nội_dung");
                    } else if (text.length() > 2000) {
                        send("Tin chat tối đa 2000 ký tự Java.");
                    } else {
                        currentRoom.chat(this, text);
                    }
                } else {
                    send("Lệnh: /create, /join mã_phòng, /members, /kick sessionId (chủ phòng), /leave, /chat nội_dung, /code, /lines, /history [version], /request, /release token, /select version token 2,5,8|4-7, /setline token dòng nội_dung, /delete token dòng, /keep token dòng, /setrange token nội_dung, /draft token, /finish token, /cancel token, /update (chỉ chủ phòng), /screen-ticket publish|view, /quit");
                }
            }
        } catch (EOFException e) {
            System.out.println("Client đã đóng kết nối: " + memberId);
        } catch (IOException | RejectedExecutionException e) {
            if (!closed.get()) server.log("Lỗi phiên " + memberId + ": " + e.getClass().getSimpleName());
        } finally {
            if (monitor != null) monitor.cancel(false);
            closeConnection();
            leaveRoom();
            server.clients.remove(memberId);
        }
    }
}
