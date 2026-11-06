package studyfix;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Kiểm tra lại lỗi dọn JDBC sau commit và bản chụp trạng thái cuối của giao diện quản lý. */
public final class RecoveryTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++; System.out.println("PASS " + message);
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        Peer(Server server, String user, String password) throws Exception {
            socket = new Socket("127.0.0.1", server.port()); socket.setSoTimeout(8000);
            in = new DataInputStream(socket.getInputStream()); out = new DataOutputStream(socket.getOutputStream());
            send("/login " + user + " " + password); await("[LOGIN_OK]");
        }
        void send(String command) throws IOException { Protocol.send(out, command); }
        String await(String marker) throws IOException {
            for (;;) {
                String reply = Protocol.receive(in);
                if (reply.contains("[SAVE_FAILED]") && !marker.equals("[SAVE_FAILED]")) throw new AssertionError(reply);
                if (reply.contains(marker)) return reply;
            }
        }
        String create() throws Exception {
            send("/create"); String reply = await("Đã tạo và tham gia phòng:");
            String id = reply.substring(reply.lastIndexOf(':') + 1).trim(); await("--- CODE CHUNG ---"); return id;
        }
        void request() throws Exception { send("/request"); await("editor="); }
        void expectClosed() throws Exception {
            try { for (;;) Protocol.receive(in); }
            catch (EOFException | SocketException expected) { check(true, "Admin Stop closes an active client socket"); }
        }
        @Override public void close() throws IOException { socket.close(); }
    }
    private static void inject(Store store, boolean reset, boolean rollback, AtomicInteger resets) throws Exception {
        Connection real = (Connection) field(store, "db");
        Connection proxy = (Connection) java.lang.reflect.Proxy.newProxyInstance(RecoveryTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (target, method, args) -> {
                    if (method.getName().equals("setAutoCommit") && Boolean.TRUE.equals(args[0])) {
                        resets.incrementAndGet();
                        if (reset) throw new SQLException("Injected reset failure after transaction");
                    }
                    if (rollback && method.getName().equals("rollback")) throw new SQLException("Injected rollback failure");
                    try { return method.invoke(real, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        Field f = Store.class.getDeclaredField("db"); f.setAccessible(true); f.set(store, proxy);
    }
    private static void agrees(Server server, String id, long version, String code) throws Exception {
        Room.Snapshot ram = server.rooms.get(id).snapshot();
        Store.StoredRoom disk = server.store().loadRooms().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
        check(ram.version() == version && disk.version() == version && ram.code().equals(code) && disk.code().equals(code),
                "RAM and SQLite agree for room " + id + " version " + version);
        check(ram.history().size() == version + 1 && disk.history().size() == version + 1
                && disk.history().get((int) version).getSourceCode().equals(code), "Revision committed exactly once");
    }
    private static void jdbcTest(Path path) throws Exception {
        try (Server server = new Server(0, path)) {
            server.start();
            try (Peer teacher = new Peer(server, "teacher", "Teacher123!")) {
                String id = teacher.create(); teacher.request(); long token = server.rooms.get(id).snapshot().token();
                AtomicInteger resets = new AtomicInteger(); inject(server.store(), true, false, resets);
                teacher.send("/update 0 " + token + " committed"); teacher.await("--- CODE CHUNG ĐÃ CẬP NHẬT ---");
                check(resets.get() == 1, "Injected post-commit reset failure was exercised");
                agrees(server, id, 1, "committed");
                try (Statement s = ((Connection) field(server.store(), "db")).createStatement(); ResultSet r = s.executeQuery("PRAGMA foreign_keys")) {
                    check(r.next() && r.getInt(1) == 1, "Replacement connection preserves foreign-key enforcement");
                }
                teacher.send("/update 1 " + token + " continued"); teacher.await("--- CODE CHUNG ĐÃ CẬP NHẬT ---");
                agrees(server, id, 2, "continued");
                teacher.send("/leave"); teacher.await("Đã rời phòng:");
                inject(server.store(), true, false, new AtomicInteger());
                String second = teacher.create(); agrees(server, second, 0, Room.INITIAL_CODE);
                check(server.rooms.size() == 2, "Post-commit cleanup failure does not hide a newly created room");
                teacher.request(); long nextToken = server.rooms.get(second).snapshot().token();
                try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + path); Statement s = db.createStatement()) {
                    s.execute("CREATE TRIGGER fail_revision BEFORE INSERT ON revisions BEGIN SELECT RAISE(ABORT,'injected insert failure'); END");
                    AtomicInteger unsafeReset = new AtomicInteger(); inject(server.store(), false, true, unsafeReset);
                    teacher.send("/update 0 " + nextToken + " must-not-commit"); teacher.await("[SAVE_FAILED]");
                    check(unsafeReset.get() == 0, "Failed rollback never enables autocommit on the dirty connection");
                    agrees(server, second, 0, Room.INITIAL_CODE);
                    s.execute("DROP TRIGGER fail_revision");
                }
                teacher.send("/update 0 " + nextToken + " after-rollback"); teacher.await("--- CODE CHUNG ĐÃ CẬP NHẬT ---");
                agrees(server, second, 1, "after-rollback");
            }
        }
    }
    private static void onEdt(EdtAction action) throws Exception {
        SwingUtilities.invokeAndWait(() -> { try { action.run(); } catch (Exception e) { throw new RuntimeException(e); } });
    }
    @FunctionalInterface private interface EdtAction { void run() throws Exception; }
    private static void waitEdt(EdtCondition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean[] result = {false};
        do {
            onEdt(() -> result[0] = condition.test());
            if (result[0]) return;
            Thread.sleep(30);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for admin state");
    }
    @FunctionalInterface private interface EdtCondition { boolean test() throws Exception; }
    private static void adminTest(Path path) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) { System.out.println("SKIP admin shutdown UI: headless"); return; }
        String oldPort = System.getProperty("studyfix.port"), oldDb = System.getProperty("studyfix.db");
        System.setProperty("studyfix.port", "0"); System.setProperty("studyfix.screen.port", "0"); System.setProperty("studyfix.assignment.port", "0"); System.setProperty("studyfix.web.port", "0"); System.setProperty("studyfix.db", path.toString());
        Object[] admin = {null}; Server[] server = {null};
        try {
            onEdt(() -> { var c = ServerAdmin.class.getDeclaredConstructor(); c.setAccessible(true); admin[0] = c.newInstance(); });
            waitEdt(() -> { server[0] = (Server) field(admin[0], "server"); return server[0] != null && server[0].isRunning(); });
            try (Peer teacher = new Peer(server[0], "teacher", "Teacher123!"); Peer student = new Peer(server[0], "student1", "Student123!")) {
                String id = teacher.create(); student.send("/join " + id); student.await("--- CODE CHUNG ---");
                teacher.request(); long token = server[0].rooms.get(id).snapshot().token();
                teacher.send("/update 0 " + token + " retained-history"); teacher.await("--- CODE CHUNG ĐÃ CẬP NHẬT ---");
                student.send("/request"); student.await("hàng đợi=[Học sinh 1]");
                waitEdt(() -> {
                    Server.Snapshot latest = (Server.Snapshot) field(admin[0], "latest");
                    return latest != null && latest.connections() == 2 && latest.rooms().size() == 1
                            && latest.rooms().get(0).members().size() == 2 && latest.rooms().get(0).queue().size() == 1;
                });
                check(true, "Admin renders live members, editor and waiting queue before Stop");
                onEdt(() -> {
                    DefaultTableModel table = (DefaultTableModel) field(admin[0], "rows");
                    check(table.findColumn("Mã phòng") >= 0 && table.findColumn("ID nội bộ") >= 0,
                            "Admin distinguishes public room code and internal ID");
                    check(String.valueOf(table.getValueAt(0, table.findColumn("Giáo viên tạo phòng"))).contains("teacher"),
                            "Admin resolves room owner to a readable account");
                    check(((JTextArea) field(admin[0], "code")).getText().contains("retained-history"),
                            "Admin automatically displays newest Master revision");
                });
                Server.Snapshot[] old = {null};
                onEdt(() -> { old[0] = (Server.Snapshot) field(admin[0], "latest"); ((JButton) field(admin[0], "stop")).doClick(); });
                waitEdt(() -> {
                    Server.Snapshot latest = (Server.Snapshot) field(admin[0], "latest");
                    return latest != null && !latest.running() && latest.connections() == 0;
                });
                onEdt(() -> {
                    DefaultTableModel rows = (DefaultTableModel) field(admin[0], "rows");
                    check(rows.getRowCount() == 1 && rows.getValueAt(0, rows.findColumn("Người giữ quyền")) == null
                            && ((List<?>) rows.getValueAt(0, rows.findColumn("Thành viên (session | tên)"))).isEmpty()
                            && ((List<?>) rows.getValueAt(0, rows.findColumn("Hàng đợi"))).isEmpty(),
                            "Admin final table clears editor, members and queue");
                    check(((JLabel) field(admin[0], "status")).getText().contains("Kết nối: 0")
                            && !((JButton) field(admin[0], "stop")).isEnabled(), "Admin stopped status reports zero connections");
                    check(((JTextArea) field(admin[0], "activity")).getText().contains("Server đã dừng"), "Admin final log includes shutdown");
                    Server.Snapshot latest = (Server.Snapshot) field(admin[0], "latest");
                    check(latest.rooms().get(0).version() == 1 && latest.rooms().get(0).history().size() == 2,
                            "Final refresh preserves durable code and history");
                    var render = ServerAdmin.class.getDeclaredMethod("render", Server.Snapshot.class); render.setAccessible(true); render.invoke(admin[0], old[0]);
                    check(((Server.Snapshot) field(admin[0], "latest")).connections() == 0, "Queued pre-stop refresh cannot overwrite the final snapshot");
                });
                teacher.expectClosed(); student.expectClosed();
            }
        } finally {
            if (admin[0] != null) onEdt(() -> { var close = ServerAdmin.class.getDeclaredMethod("shutdown", boolean.class); close.setAccessible(true); close.invoke(admin[0], true); });
            if (oldPort == null) System.clearProperty("studyfix.port"); else System.setProperty("studyfix.port", oldPort);
            if (oldDb == null) System.clearProperty("studyfix.db"); else System.setProperty("studyfix.db", oldDb);
        }
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build"), "recovery-").toAbsolutePath();
        System.out.println("RECOVERY_DATABASE_DIR=" + dir);
        jdbcTest(dir.resolve("jdbc.db")); adminTest(dir.resolve("admin.db"));
        System.out.println("RECOVERY ALL PASSED: " + checks + " checks");
    }
}
