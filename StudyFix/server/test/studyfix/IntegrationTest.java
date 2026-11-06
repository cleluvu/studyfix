package studyfix;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Test bằng TCP và SQLite thật trên máy, không cần thư viện kiểm thử ngoài. */
public final class IntegrationTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++; System.out.println("PASS " + message);
    }
    private static void eventually(BooleanSupplier condition, long millis, String message) throws Exception {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(20);
        check(condition.getAsBoolean(), message);
    }
    private static Room.Snapshot room(Server server, String id) { return server.rooms.get(id).snapshot(); }
    private static final class Peer implements AutoCloseable {
        final Socket socket = new Socket();
        final DataOutputStream out;
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        String session;
        Peer(Server server, String username, String password) throws Exception {
            socket.connect(new InetSocketAddress("127.0.0.1", server.port()));
            out = new DataOutputStream(socket.getOutputStream());
            Thread reader = new Thread(() -> {
                try {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    while (true) received.add(Protocol.receive(in));
                } catch (IOException e) { received.add("[CLOSED]"); }
            }); reader.setDaemon(true); reader.start();
            if (username != null) {
                send("/login " + username + " " + password);
                String greeting = await("[LOGIN_OK]");
                session = greeting.split("session=")[1].split(" ")[0];
            }
            heartbeat.scheduleAtFixedRate(() -> { try { send("/ping"); } catch (IOException ignored) { } }, 0, 5, TimeUnit.SECONDS);
        }
        void send(String command) throws IOException { Protocol.send(out, command); }
        String await(String text) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            String last = "";
            while (System.nanoTime() < deadline) {
                String value = received.poll(200, TimeUnit.MILLISECONDS);
                if (value != null) { last = value; if (value.contains(text)) return value; }
            }
            throw new AssertionError("Không nhận được '" + text + "'; cuối=" + last);
        }
        void barrier() throws Exception { send("/ping"); await("/pong"); received.clear(); }
        @Override public void close() throws IOException { heartbeat.shutdownNow(); socket.close(); }
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "integration-");
        Path database = dir.resolve("studyfix.db");
        System.out.println("TEST_DATABASE=" + database.toAbsolutePath());
        String firstRoom;
        String teacherId;
        Instant savedAt;
        List<String> departures = new CopyOnWriteArrayList<>();
        Server server = new Server(0, database); server.start();
        try (server;
             Peer teacher = new Peer(server, "teacher", "Teacher123!");
             Peer student = new Peer(server, "student1", "Student123!");
             Peer second = new Peer(server, "student2", "Student234!");
             Peer otherTeacher = new Peer(server, "teacher2", "Teacher234!");
             Peer anonymous = new Peer(server, null, null)) {
            server.gateway().addDepartureListener((session, identity, id, reason) -> departures.add(session + ":" + id));
            teacherId = server.gateway().identity(teacher.session).userId();
            anonymous.send("/create"); anonymous.await("Bạn cần đăng nhập");
            anonymous.send("/name Teacher"); anonymous.await("Bạn cần đăng nhập");
            anonymous.send("/login teacher wrong"); anonymous.await("[LOGIN_FAILED]");
            check(true, "Chặn chưa đăng nhập, giả tên và sai mật khẩu");
            student.send("/create"); student.await("Chỉ giáo viên");
            teacher.send("/create"); String created = teacher.await("Đã tạo và tham gia phòng:");
            firstRoom = created.substring(created.lastIndexOf(':') + 1).trim();
            teacher.await("--- CODE CHUNG ---");
            student.send("/join " + firstRoom); student.await("--- CODE CHUNG ---");
            second.send("/join " + firstRoom); second.await("--- CODE CHUNG ---");
            otherTeacher.send("/create"); String other = otherTeacher.await("Đã tạo và tham gia phòng:");
            String otherRoom = other.substring(other.lastIndexOf(':') + 1).trim(); otherTeacher.await("--- CODE CHUNG ---");
            check(server.gateway().isTeacher(teacher.session, firstRoom), "Chủ phòng được nhận diện qua userId");
            check(!server.gateway().isMember(otherTeacher.session, firstRoom), "Tách thành viên hai phòng");
            try { server.gateway().codeSnapshot(otherTeacher.session, firstRoom); throw new AssertionError("Leaked room"); }
            catch (SecurityException expected) { check(true, "Gateway chặn người ngoài phòng"); }
            student.send("/kick " + second.session); student.await("Chỉ giáo viên chủ phòng");
            otherTeacher.send("/kick " + second.session); otherTeacher.await("Session không hợp lệ");
            check(room(server, firstRoom).members().size() == 3, "Giáo viên phòng khác không quản lý được thành viên");
            student.send("/chat Xin chào Unicode 你好"); teacher.await("Xin chào Unicode 你好");
            check(room(server, otherRoom).version() == 0, "Chat không đổi code/phòng khác");
            teacher.send("/request"); teacher.await("editor=Giáo viên demo");
            long token = room(server, firstRoom).token();
            student.send("/update 0 " + token + " forged"); student.await("Chỉ giáo viên chủ phòng");
            teacher.send("/update 0 " + (token + 1) + " wrong-token"); teacher.await("Từ chối cập nhật");
            teacher.send("/update 0 " + token + " class Main { // tiếng Việt\n}");
            teacher.await("--- CODE CHUNG ĐÃ CẬP NHẬT ---");
            Room.Snapshot accepted = room(server, firstRoom);
            savedAt = accepted.history().get(1).getCreatedAt();
            check(accepted.version() == 1 && accepted.history().size() == 2, "Chỉ cập nhật hợp lệ tạo revision");
            check(accepted.history().get(1).getEditorId().equals(teacherId)
                    && accepted.history().get(1).getEditorName().equals("Giáo viên demo"), "Lưu đúng userId và tên người sửa");
            teacher.send("/update 0 " + token + " stale"); teacher.await("phiên bản cũ");
            teacher.send("/update 1 " + token + "   "); teacher.await("Code phải có nội dung");
            check(room(server, firstRoom).history().size() == 2, "Version cũ/code rỗng không thêm lịch sử");
            student.send("/history 0"); student.await("CODE SAU KHI SỬA (PHIÊN BẢN 0)");
            check(room(server, firstRoom).code().equals(accepted.code()), "Xem lịch sử không đổi code hiện tại");
            student.send("/history"); student.await("Người sửa: Giáo viên demo");
            check(room(server, otherRoom).history().size() == 1, "Lịch sử không lẫn giữa phòng");
            student.send("/history -1"); student.await("Phiên bản phải là số nguyên không âm");
            student.send("/history 9999"); student.await("Không tìm thấy phiên bản");
            teacher.send("/update 1 " + token + " " + "a".repeat(32001)); teacher.await("Code phải có nội dung");
            check(room(server, firstRoom).version() == 1, "Chặn version lịch sử sai và code quá lớn");
            // Cố ý làm câu SQL thứ hai lỗi sau khi code đã cập nhật trong giao dịch để kiểm tra hoàn tác.
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath()); Statement s = db.createStatement()) {
                s.execute("CREATE TRIGGER fail_revision BEFORE INSERT ON revisions BEGIN SELECT RAISE(ABORT,'injected disk failure'); END");
                teacher.send("/update 1 " + token + " must rollback"); teacher.await("[SAVE_FAILED]");
                try (ResultSet r = s.executeQuery("SELECT version,code FROM rooms WHERE room_id=" + firstRoom)) {
                    r.next(); check(r.getLong(1) == 1 && r.getString(2).equals(accepted.code()), "Rollback database khi ghi revision thất bại");
                }
                check(room(server, firstRoom).version() == 1 && room(server, firstRoom).history().size() == 2,
                        "Lỗi lưu không đổi RAM/không phát thành công");
                s.execute("DROP TRIGGER fail_revision");
            }
            student.send("/request"); second.send("/request");
            eventually(() -> room(server, firstRoom).queue().size() == 2, 3000, "Hai client cùng xin quyền được xếp hàng");
            String queued = room(server, firstRoom).queue().get(0);
            teacher.send("/leave"); teacher.await("Đã rời phòng:");
            eventually(() -> queued.equals(room(server, firstRoom).editor()), 3000, "Người giữ quyền rời phòng chuyển đúng FIFO");
            check(room(server, firstRoom).token() != token, "Thu hồi token lượt trước");
            Peer editor = queued.equals(student.session) ? student : second;
            Peer waiting = editor == student ? second : student;
            long newToken = room(server, firstRoom).token();
            waiting.send("/release " + newToken); waiting.await("Từ chối trả quyền");
            editor.send("/select 1 " + token + " 1-2"); editor.await("Từ chối");
            // Gửi hai lần cập nhật cùng phiên bản qua hai socket; chỉ một lần được thành công.
            editor.send("/select 1 " + newToken + " 1-2"); editor.await("[Bản nháp]");
            editor.send("/setrange " + newToken + " winner"); editor.await("[Bản nháp]");
            editor.send("/finish " + newToken);
            waiting.send("/finish " + newToken);
            editor.await("--- CODE CHUNG ĐÃ CẬP NHẬT ---"); waiting.await("Từ chối");
            check(room(server, firstRoom).version() == 2, "Cập nhật đồng thời chỉ chấp nhận người giữ quyền");
            // Cho thao tác nhường quyền hợp lệ chạy cùng lúc với người đang chờ rời phòng.
            editor.send("/release " + room(server, firstRoom).token());
            waiting.send("/leave"); waiting.await("Đã rời phòng:");
            eventually(() -> room(server, firstRoom).editor() == null && room(server, firstRoom).queue().isEmpty(),
                    3000, "Trả quyền đồng thời rời phòng không giữ editor/queue mồ côi");
            waiting.send("/join " + firstRoom); waiting.await("--- CODE CHUNG ---");
            editor.send("/request");
            eventually(() -> editor.session.equals(room(server, firstRoom).editor()), 3000, "Xin quyền lại sau khi trả quyền");
            waiting.send("/request");
            eventually(() -> room(server, firstRoom).queue().contains(waiting.session), 3000, "Xếp hàng lại sau race");
            editor.close();
            eventually(() -> waiting.session.equals(room(server, firstRoom).editor()), 3000, "Mất kết nối thu hồi/chuyển quyền");
            eventually(() -> Collections.frequency(departures, editor.session + ":" + firstRoom) == 1, 3000, "Callback rời phòng đúng một lần");
            // Ngắt socket không đọc dữ liệu khi hàng đợi đầy, tránh làm chặn khóa phòng.
            try (Socket slow = new Socket()) {
                slow.setReceiveBufferSize(1024); slow.connect(new InetSocketAddress("127.0.0.1", server.port()));
                DataOutputStream out = new DataOutputStream(slow.getOutputStream());
                DataInputStream in = new DataInputStream(slow.getInputStream());
                slow.setSoTimeout(5000); Protocol.send(out, "/login student1 Student123!");
                String greeting = Protocol.receive(in); String slowId = greeting.split("session=")[1].split(" ")[0];
                Protocol.send(out, "/join " + firstRoom);
                while (!Protocol.receive(in).contains("--- CODE CHUNG ---")) { }
                long started = System.nanoTime();
                for (int i = 0; i < 500; i++) {
                    try { server.gateway().sendToUser(waiting.session, slowId, firstRoom, "X".repeat(60000)); }
                    catch (IOException | SecurityException expected) { break; }
                }
                eventually(() -> !server.clients.containsKey(slowId), 4000, "Đóng client đọc chậm khi đầy hàng đợi");
                check(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5), "Client chậm không giữ khóa phòng");
                waiting.barrier(); waiting.send("/chat still responsive"); waiting.await("still responsive");
            }
            teacher.send("/join " + firstRoom); teacher.await("--- CODE CHUNG ---");
            teacher.send("/kick " + waiting.session); teacher.await("Đã đóng phiên thành viên:");
            eventually(() -> !room(server, firstRoom).members().stream().anyMatch(m -> m.startsWith(waiting.session)), 3000, "Chủ phòng quản lý thành viên");
            try (Peer timed = new Peer(server, "student2", "Student234!")) {
                timed.send("/join " + firstRoom); timed.await("--- CODE CHUNG ---");
                timed.send("/request"); timed.await("editor=Học sinh 2");
                timed.heartbeat.shutdownNow();
                System.out.println("Đang kiểm tra timeout heartbeat thực tế (20 giây)…");
                eventually(() -> !server.clients.containsKey(timed.session), 24000, "Timeout heartbeat đóng socket và dọn phiên");
                check(room(server, firstRoom).editor() == null, "Heartbeat timeout thu hồi quyền sửa");
            }
            eventually(() -> server.monitor.getQueue().size() == server.clients.size(), 3000, "Hủy và loại bỏ heartbeat task khi phiên kết thúc");
            try (Socket malformed = new Socket("127.0.0.1", server.port())) {
                malformed.setSoTimeout(3000);
                new DataOutputStream(malformed.getOutputStream()).writeInt(Protocol.MAX_BYTES + 1);
                check(malformed.getInputStream().read() == -1, "Đóng kết nối frame vượt giới hạn");
            }
        }
        check(!server.isRunning() && server.clients.isEmpty(), "Dừng server dọn kết nối");
        try (Server restarted = new Server(0, database)) {
            restarted.start();
            Room.Snapshot restored = room(restarted, firstRoom);
            check(restored.version() == 2 && restored.code().equals("winner") && restored.history().size() == 3, "Restart khôi phục code/version/lịch sử");
            check(restored.history().get(1).getCreatedAt().equals(savedAt)
                    && restored.history().get(1).getEditorId().equals(teacherId), "Restart giữ nguyên người sửa và thời gian");
            check(restored.members().isEmpty() && restored.editor() == null && restored.queue().isEmpty() && restored.token() == 0,
                    "Restart không phục hồi phiên online/quyền/hàng đợi");
            try (Peer teacher = new Peer(restarted, "teacher", "Teacher123!")) {
                teacher.send("/create"); String created = teacher.await("Đã tạo và tham gia phòng:");
                String id = created.substring(created.lastIndexOf(':') + 1).trim();
                check(!id.equals(firstRoom) && restarted.rooms.size() == 3, "Mã phòng mới không trùng sau restart");
            }
        }
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath()); Statement s = db.createStatement();
             ResultSet r = s.executeQuery("SELECT password_hash FROM users")) {
            Set<String> salts = new HashSet<>();
            while (r.next()) { String hash = r.getString(1); check(!hash.contains("123!") && hash.startsWith("600000:"), "Database chỉ lưu PBKDF2 hash"); salts.add(hash.split(":")[1]); }
            check(salts.size() == 4, "Salt riêng cho từng tài khoản");
        }
        System.out.println("ALL PASSED: " + checks + " checks (localhost only)");
    }
}
