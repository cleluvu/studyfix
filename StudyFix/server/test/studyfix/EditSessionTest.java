package studyfix;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Kiểm tra bản nháp bằng EditSession trực tiếp, TCP/SQLite thật và tiến trình JVM chạy Client thật. */
public final class EditSessionTest {
    private static final String BASE = "one\r\ntwo\nthree\nfour\nfive";
    private static final ScheduledExecutorService HEARTBEATS = Executors.newScheduledThreadPool(2);
    private static final Set<String> COMMANDS = new ConcurrentSkipListSet<>();
    private static int passed, failed;
    private static Path root;
    @FunctionalInterface private interface Test { void run() throws Exception; }

    private static void expect(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private static void run(String id, String name, Test test) {
        try { test.run(); passed++; System.out.println("PASS " + id + " " + name); }
        catch (Exception | AssertionError e) {
            failed++; System.out.println("FAIL " + id + " " + name + " | " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    private static void until(BooleanSupplier condition, long timeoutMs) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        expect(condition.getAsBoolean(), "State not reached within " + timeoutMs + "ms");
    }

    /** Chỉ một luồng đọc socket; dùng mốc giữa các lệnh để phân biệt thông báo cũ với phản hồi mới. */
    private static final class Peer implements AutoCloseable {
        final Socket socket = new Socket();
        final DataOutputStream out;
        final List<String> events = new CopyOnWriteArrayList<>();
        final AtomicInteger pongs = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
        final ScheduledFuture<?> heartbeat;
        int pings;
        String session;
        Peer(Server server, String username, String password) throws Exception {
            socket.connect(new InetSocketAddress("127.0.0.1", server.port()), 5000); socket.setTcpNoDelay(true);
            out = new DataOutputStream(socket.getOutputStream());
            Thread reader = new Thread(() -> {
                try {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    StringBuilder multipart = null; int next = 0, total = 0;
                    while (true) {
                        String message = Protocol.receive(in);
                        if (message.equals("/pong")) { pongs.incrementAndGet(); continue; }
                        if (message.startsWith("[HISTORY_PART ")) {
                            int end = message.indexOf("]\n"); String[] position = message.substring(14, end).split("/");
                            int index = Integer.parseInt(position[0]), count = Integer.parseInt(position[1]);
                            if (index == 1) { multipart = new StringBuilder(); next = 1; total = count; }
                            if (multipart == null || index != next++ || count != total) throw new IOException("Bad multipart sequence");
                            multipart.append(message.substring(end + 2));
                            if (index == total) { events.add(multipart.toString()); multipart = null; }
                        } else events.add(message);
                    }
                } catch (IOException | RuntimeException e) { closed.set(true); }
            }, "edit-session-test-reader");
            reader.setDaemon(true); reader.start();
            heartbeat = HEARTBEATS.scheduleAtFixedRate(() -> { try { send("/ping"); } catch (IOException ignored) { } }, 5, 5, TimeUnit.SECONDS);
            try {
                int start = events.size(); send("/login " + username + " " + password);
                session = await(start, "[LOGIN_OK]").split("session=")[1].split(" ")[0];
            } catch (Exception | AssertionError e) { close(); throw e; }
        }
        synchronized void send(String message) throws IOException {
            String verb = message.split("[ \\t]", 2)[0];
            if (verb.equals("/ping")) pings++;
            // Chỉ ghi tên lệnh vào log, không ghi thông tin xác thực hay toàn bộ yêu cầu đăng nhập.
            COMMANDS.add(verb); Protocol.send(out, message);
        }
        void barrier() throws Exception {
            int target; synchronized (this) { send("/ping"); target = pings; }
            until(() -> pongs.get() >= target || closed.get(), 5000);
            expect(!closed.get(), "Connection closed during command barrier");
        }
        String await(int start, String marker) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            do {
                for (int i = start; i < events.size(); i++) if (events.get(i).contains(marker)) return events.get(i);
                expect(!closed.get(), "Connection closed while awaiting " + marker);
                Thread.sleep(5);
            } while (System.nanoTime() < end);
            throw new AssertionError("Missing response marker: " + marker);
        }
        String command(String message, String marker) throws Exception {
            barrier(); int start = events.size(); send(message); return await(start, marker);
        }
        @Override public void close() throws IOException { heartbeat.cancel(false); socket.close(); }
    }

    private static final class Fixture implements AutoCloseable {
        final Path db;
        final Server server;
        final Peer owner, editor, waiter;
        final String roomId;
        final List<Peer> peers = new ArrayList<>();
        Fixture(String id) throws Exception {
            db = Files.createDirectory(root.resolve(id)).resolve("studyfix.db");
            server = new Server(0, db); server.start();
            try {
                owner = add("teacher", "Teacher123!"); editor = add("student1", "Student123!"); waiter = add("student2", "Student234!");
                String created = owner.command("/create", "Đã tạo và tham gia phòng:");
                roomId = created.substring(created.lastIndexOf(':') + 1).trim();
                editor.command("/join " + roomId, "--- CODE CHUNG ---"); waiter.command("/join " + roomId, "--- CODE CHUNG ---");
                long token = request(owner);
                owner.command("/update " + snapshot().version() + " " + token + " " + BASE, "--- CODE CHUNG ĐÃ CẬP NHẬT ---");
                owner.command("/release " + token, "editor=không có");
                System.out.println("FIXTURE " + id + " port=" + server.port() + " db=" + db);
            } catch (Exception | AssertionError e) { close(); throw e; }
        }
        Peer add(String name, String password) throws Exception { Peer p = new Peer(server, name, password); peers.add(p); return p; }
        Room.Snapshot snapshot() { return server.rooms.get(roomId).snapshot(); }
        long request(Peer p) throws Exception {
            p.command("/request", "editor="); until(() -> p.session.equals(snapshot().editor()), 3000); return snapshot().token();
        }
        void select(long token, String choice) throws Exception { editor.command("/select " + snapshot().version() + " " + token + " " + choice, "[Bản nháp]"); }
        void queue() throws Exception { waiter.command("/request", "editor="); expect(snapshot().queue().equals(List.of(waiter.session)), "Waiter not queued once"); }
        void unchanged(Room.Snapshot previous) throws Exception {
            Room.Snapshot now = snapshot();
            expect(now.code().equals(previous.code()) && now.version() == previous.version()
                    && now.history().equals(previous.history()), "Committed RAM/history changed unexpectedly");
            diskMatches(now);
        }
        void diskMatches(Room.Snapshot expected) throws Exception {
            // Dùng kết nối JDBC riêng để kiểm tra dữ liệu thật trong database, không chỉ đọc lại đối tượng trong RAM.
            try (Connection dbConnection = DriverManager.getConnection("jdbc:sqlite:" + db);
                 PreparedStatement query = dbConnection.prepareStatement("SELECT code,version,(SELECT count(*) FROM revisions WHERE room_id=?) FROM rooms WHERE room_id=?")) {
                query.setString(1, roomId); query.setString(2, roomId);
                try (ResultSet r = query.executeQuery()) {
                    expect(r.next() && r.getString(1).equals(expected.code()) && r.getLong(2) == expected.version()
                            && r.getInt(3) == expected.history().size(), "SQLite and RAM disagree");
                }
            }
        }
        @Override public void close() throws Exception {
            try { for (Peer p : peers) p.close(); } finally { server.close(); }
        }
    }

    private static void directDiscrete() {
        EditSession draft = new EditSession(BASE, 7, "1,3,5");
        expect(!draft.complete() && draft.build().equals(BASE), "Unprocessed selections should keep original content");
        draft.deleteLine(1); draft.setLine(3, "    ba 😀");
        expect(!draft.complete(), "One original selected line is still unprocessed"); draft.keepLine(5);
        expect(draft.complete() && draft.build().equals("two\n    ba 😀\nfour\nfive"), "Original line numbers shifted after delete");
        expect(draft.version() == 7 && draft.changes().contains("Dòng 3:") && draft.changes().contains("Giữ nguyên"), "Selection history missing");
    }
    private static void directRange() {
        EditSession range = new EditSession(BASE, 9, "2-3");
        range.setRange("A\r\nB 😀");
        expect(range.complete() && range.build().equals("one\r\nA\r\nB 😀\nfour\nfive"), "Replacement lost line boundary or Unicode");
        range.keepRange(); expect(range.build().equals(BASE), "Keeping range changed original code");
        range.deleteRange(); expect(range.build().equals("one\r\nfour\nfive"), "Range delete result");
    }
    private static void unfinished() throws Exception {
        try (Fixture f = new Fixture("DT03")) {
            long token = f.request(f.editor); f.select(token, "1,3,5");
            f.editor.command("/setline " + token + " 1 changed", "[Bản nháp]"); f.queue();
            Room.Snapshot before = f.snapshot(); String preview = f.editor.command("/draft " + token, "XEM TRƯỚC CODE");
            f.editor.command("/finish " + token, "Chưa xử lý đủ");
            expect(f.snapshot().equals(before) && f.editor.command("/draft " + token, "XEM TRƯỚC CODE").equals(preview), "Early finish destroyed draft or rights");
            f.unchanged(before);
        }
    }
    private static void previewOnly() throws Exception {
        try (Fixture f = new Fixture("DT04")) {
            long token = f.request(f.editor); f.select(token, "1-3"); Room.Snapshot before = f.snapshot();
            f.editor.command("/setrange " + token + " bản nháp 😀\nchưa lưu", "[Bản nháp]");
            expect(f.editor.command("/draft " + token, "XEM TRƯỚC CODE").endsWith("bản nháp 😀\nchưa lưu\nfour\nfive"), "Wrong draft preview");
            expect(f.waiter.command("/code", "--- CODE CHUNG ---").endsWith(BASE), "Another member sees uncommitted code");
            expect(f.snapshot().equals(before), "Preview changed committed room state"); f.unchanged(before);
        }
    }
    private static void oneCommit() throws Exception {
        try (Fixture f = new Fixture("DT05")) {
            long token = f.request(f.editor); f.select(token, "1,3,5");
            f.editor.command("/setline " + token + " 1 ONE", "[Bản nháp]"); f.editor.command("/delete " + token + " 3", "[Bản nháp]"); f.editor.command("/keep " + token + " 5", "[Bản nháp]"); f.queue();
            Room.Snapshot before = f.snapshot(); f.editor.command("/finish " + token, "[COMMITTED]"); Room.Snapshot after = f.snapshot();
            expect(after.version() == before.version() + 1 && after.history().size() == before.history().size() + 1
                    && after.code().equals("ONE\r\ntwo\nfour\nfive") && after.editor().equals(f.waiter.session)
                    && after.token() != token && after.queue().isEmpty(), "Finish not one commit followed by FIFO transfer");
            f.diskMatches(after); f.editor.command("/finish " + token, "Từ chối"); f.unchanged(after);
        }
    }
    private static void noChange() throws Exception {
        try (Fixture f = new Fixture("DT06")) {
            for (boolean keep : new boolean[]{true, false}) {
                long token = f.request(f.editor); f.select(token, "1");
                f.editor.command(keep ? "/keep " + token + " 1" : "/setline " + token + " 1 one", "[Bản nháp]");
                f.queue(); Room.Snapshot before = f.snapshot(); f.editor.command("/finish " + token, "[NO_CHANGE]");
                f.unchanged(before); expect(f.snapshot().editor().equals(f.waiter.session) && f.snapshot().token() != token, "No-op did not end the turn");
                f.waiter.command("/release " + f.snapshot().token(), "editor=không có");
            }
        }
    }
    private static void originalIndices() throws Exception {
        try (Fixture f = new Fixture("DT07")) {
            long token = f.request(f.editor); f.select(token, "1,3"); f.editor.command("/delete " + token + " 1", "[Bản nháp]");
            f.editor.command("/setline " + token + " 3 THREE 😀", "[Bản nháp]"); f.editor.command("/finish " + token, "[COMMITTED]");
            expect(f.snapshot().code().equals("two\nTHREE 😀\nfour\nfive"), "Line 3 referred to shifted line 4");
            expect(f.snapshot().history().getLast().getChanges().contains("Dòng 3:") && f.snapshot().history().getLast().getChanges().contains("three"), "History lost original selected index"); f.diskMatches(f.snapshot());
        }
    }
    private static void cancel() throws Exception {
        try (Fixture f = new Fixture("DT08")) {
            long token = f.request(f.editor); f.select(token, "2-3"); f.editor.command("/setrange " + token + " cancelled", "[Bản nháp]"); f.queue();
            Room.Snapshot before = f.snapshot(); f.editor.command("/cancel " + token, "[CANCELLED]"); f.unchanged(before);
            expect(f.snapshot().editor().equals(f.waiter.session), "Cancel did not hand over");
            f.waiter.command("/draft " + f.snapshot().token(), "Chưa có bản nháp");
        }
    }
    private static void disconnect() throws Exception {
        try (Fixture f = new Fixture("DT09")) {
            long token = f.request(f.editor); f.select(token, "2-3"); f.editor.command("/setrange " + token + " disconnected", "[Bản nháp]"); f.queue();
            Room.Snapshot before = f.snapshot(); f.editor.socket.setSoLinger(true, 0); f.editor.close();
            until(() -> !f.server.clients.containsKey(f.editor.session) && f.waiter.session.equals(f.snapshot().editor()), 5000);
            f.unchanged(before); f.waiter.command("/draft " + f.snapshot().token(), "Chưa có bản nháp");
        }
    }
    private static void retry() throws Exception {
        try (Fixture f = new Fixture("DT10")) {
            long token = f.request(f.editor); f.select(token, "2"); f.editor.command("/setline " + token + " 2 retry-value", "[Bản nháp]"); f.queue();
            Room.Snapshot before = f.snapshot(); String preview = f.editor.command("/draft " + token, "XEM TRƯỚC CODE");
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + f.db); Statement s = db.createStatement()) {
                s.execute("CREATE TRIGGER reject_draft BEFORE INSERT ON revisions BEGIN SELECT RAISE(ABORT,'edit-session test failure'); END");
                try {
                    f.editor.command("/finish " + token, "[SAVE_FAILED]");
                    expect(f.snapshot().equals(before), "Failed save changed editor/token/queue or committed state"); f.unchanged(before);
                    expect(f.editor.command("/draft " + token, "XEM TRƯỚC CODE").equals(preview), "Failed save lost draft");
                } finally { s.execute("DROP TRIGGER reject_draft"); }
            }
            f.editor.command("/finish " + token, "[COMMITTED]"); Room.Snapshot after = f.snapshot();
            expect(after.version() == before.version() + 1 && after.history().size() == before.history().size() + 1
                    && after.code().equals("one\r\nretry-value\nthree\nfour\nfive") && after.editor().equals(f.waiter.session), "Retry committed incorrectly");
            f.diskMatches(after); f.editor.command("/finish " + token, "Từ chối"); f.unchanged(after);
        }
    }
    private static void teacherPriorityTakeover() throws Exception {
        try (Fixture f = new Fixture("DT12")) {
            long studentToken = f.request(f.editor);
            Room.Snapshot before = f.snapshot();
            f.select(studentToken, "1-2");
            String notice = f.owner.command("/teacher-request", "[TEACHER_TAKEOVER]");
            Room.Snapshot after = f.snapshot();
            expect(after.editor().equals(f.owner.session), "Owner-teacher priority request was queued instead of taking control");
            expect(!after.queue().contains(f.owner.session), "Owner-teacher remains in the normal edit queue");
            expect(after.token() != studentToken, "Takeover must invalidate the previous edit token");
            expect(after.version() == before.version() && after.code().equals(before.code())
                    && after.history().equals(before.history()), "Uncommitted student draft must not be silently committed");
            until(() -> f.editor.events.stream().anyMatch(x -> x.contains("[TEACHER_TAKEOVER]")), 3000);
            expect(notice.contains("chưa lưu") && f.editor.events.stream().anyMatch(x -> x.contains("[TEACHER_TAKEOVER]")),
                    "Teacher and previous editor must receive the takeover/draft conflict notice");
            f.editor.command("/finish " + studentToken, "Từ chối: bạn không giữ lượt sửa hiện tại");
            expect(f.snapshot().editor().equals(f.owner.session), "Stale student token must not take control back");
            f.diskMatches(after);
        }
    }
    private static boolean contains(Path log, String marker) { try { return Files.readString(log, StandardCharsets.UTF_8).contains(marker); } catch (IOException e) { return false; } }
    private static void realRangefile() throws Exception {
        try (Fixture f = new Fixture("DT11")) {
            Path file = f.db.getParent().resolve("đoạn có khoảng trắng.java"), log = f.db.getParent().resolve("client-console.log");
            String content = "    LIVE_RANGEFILE_😀\r\n    dòng hai\r\n"; Files.writeString(file, content, StandardCharsets.UTF_8);
            String javaExecutable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
            Process client = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", javaExecutable).toString(),
                    "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstudyfix.port=" + f.server.port(),
                    "-cp", System.getProperty("java.class.path"), "studyfix.Client").redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                Writer keyboard = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8);
                keyboard.write("127.0.0.1\nstudent1\nStudent123!\n/join " + f.roomId + "\n/request\n"); keyboard.flush();
                until(() -> contains(log, "[LOGIN_OK]") && f.snapshot().editor() != null, 8000);
                Room.Snapshot before = f.snapshot(); long token = before.token(); String consoleSession = before.editor();
                expect(!consoleSession.equals(f.editor.session) && !consoleSession.equals(f.waiter.session), "Wrong editor session");
                keyboard.write("/select " + before.version() + " " + token + " 2-3\n/rangefile " + token + " " + file + "\n/draft " + token + "\n"); keyboard.flush();
                until(() -> contains(log, "XEM TRƯỚC CODE") && contains(log, "LIVE_RANGEFILE_😀"), 6000); f.unchanged(before);
                keyboard.write("/finish " + token + "\n"); keyboard.flush();
                until(() -> f.snapshot().version() == before.version() + 1 && contains(log, "[COMMITTED]"), 6000);
                Room.Snapshot after = f.snapshot(); String expected = "one\r\n" + content + "four\nfive";
                expect(after.code().equals(expected) && after.history().size() == before.history().size() + 1 && after.editor() == null,
                        "Actual console lost UTF-8, indentation, CRLF, range boundary or atomic finish");
                expect(after.history().getLast().getEditorId().equals(f.server.gateway().identity(consoleSession).userId()), "Revision not authored by actual console session");
                expect(f.waiter.command("/code", "--- CODE CHUNG ---").endsWith(expected), "Other member did not receive committed range"); f.diskMatches(after);
                COMMANDS.add("/rangefile"); keyboard.write("/quit\n"); keyboard.flush();
                expect(client.waitFor(5, TimeUnit.SECONDS) && client.exitValue() == 0, "Actual console did not exit");
                System.out.println("CONSOLE_EVIDENCE=" + log);
            } finally { if (client.isAlive()) client.destroyForcibly().waitFor(); }
        }
    }
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("build")); root = Files.createTempDirectory(Path.of("build"), "edit-session-").toAbsolutePath();
        System.out.println("EDIT_SESSION_DATABASE_ROOT=" + root);
        System.out.println("EDIT_SESSION_RUNTIME=" + Runtime.version() + " CODE_SOURCE=" + EditSession.class.getProtectionDomain().getCodeSource().getLocation());
        long start = System.nanoTime();
        try {
            run("DT01", "EditSession discrete choices require processing and use original line indices", EditSessionTest::directDiscrete);
            run("DT02", "EditSession range replace/keep/delete preserves boundaries and Unicode", EditSessionTest::directRange);
            run("DT03", "Incomplete selection cannot finish; draft, rights and SQLite remain unchanged", EditSessionTest::unfinished);
            run("DT04", "Draft preview differs from code shared with other members; RAM and SQLite unchanged", EditSessionTest::previewOnly);
            run("DT05", "Finish commits one version/revision then transfers FIFO; old token cannot duplicate save", EditSessionTest::oneCommit);
            run("DT06", "Keep and identical replacement finish without new version/history and transfer rights", EditSessionTest::noChange);
            run("DT07", "Deleting earlier line does not shift another original selected line", EditSessionTest::originalIndices);
            run("DT08", "Cancel discards draft, preserves committed data and transfers FIFO", EditSessionTest::cancel);
            run("DT09", "Abrupt socket disconnect discards draft and transfers FIFO", EditSessionTest::disconnect);
            run("DT10", "SQLite failure retains draft/rights; retry commits once with matching RAM/database", EditSessionTest::retry);
            run("DT11", "Actual Java Client rangefile sends multiline UTF-8/CRLF from path with spaces to real Server", EditSessionTest::realRangefile);
            run("DT12", "Owner-teacher takes edit priority immediately; stale student draft is not committed", EditSessionTest::teacherPriorityTakeover);
            Set<String> required = Set.of("/teacher-request", "/select", "/setline", "/delete", "/keep", "/setrange", "/rangefile", "/draft", "/finish", "/cancel");
            expect(COMMANDS.containsAll(required), "Required command coverage missing");
            System.out.println("EDIT_SESSION_COMMAND_COVERAGE=" + new TreeSet<>(required));
        } finally { HEARTBEATS.shutdownNow(); HEARTBEATS.awaitTermination(3, TimeUnit.SECONDS); }
        System.out.println("EDIT_SESSION_RESULT passed=" + passed + " failed=" + failed + " elapsed_ms=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        if (failed != 0) throw new AssertionError("EditSession contract failed: " + failed);
    }
}
