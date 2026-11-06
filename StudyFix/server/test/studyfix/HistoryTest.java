package studyfix;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

/** Kiểm tra lại lịch sử, nâng cấp dữ liệu, TCP nhiều phần và thành phần Swing thật. */
public final class HistoryTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++; System.out.println("PASS " + message);
    }
    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        Peer(Server server) throws Exception {
            socket = new Socket("127.0.0.1", server.port()); socket.setSoTimeout(8000);
            in = new DataInputStream(socket.getInputStream()); out = new DataOutputStream(socket.getOutputStream());
            send("/login teacher Teacher123!"); await("[LOGIN_OK]");
        }
        void send(String message) throws Exception { Protocol.send(out, message); }
        String await(String text) throws Exception {
            for (;;) { String s = Protocol.receive(in); if (s.contains(text)) return s; }
        }
        String history(long version) throws Exception {
            send("/history " + version);
            String first = Protocol.receive(in);
            if (!first.startsWith("[HISTORY_PART ")) return first;
            int end = first.indexOf("]\n");
            int count = Integer.parseInt(first.substring(14, end).split("/")[1]);
            StringBuilder result = new StringBuilder(first.substring(end+2));
            check(first.startsWith("[HISTORY_PART 1/"), "First history part");
            for (int i=2; i<=count; i++) {
                String part = Protocol.receive(in);
                check(part.startsWith("[HISTORY_PART " + i + "/" + count + "]\n"), "Ordered history part " + i);
                check(part.getBytes(StandardCharsets.UTF_8).length <= Protocol.MAX_BYTES, "UTF-8 frame bound " + i);
                result.append(part.substring(part.indexOf("]\n")+2));
            }
            return result.toString();
        }
        @Override public void close() throws Exception { socket.close(); }
    }
    // Ghép lại chính xác văn bản mới từ các dòng +/- có số dòng, kể cả ký tự xuống dòng.
    private static void diffCheck(String old, String next) {
        String diff = CodeDiff.between(old, next);
        List<String> before = CodeDiff.lines(old), after = new ArrayList<>();
        Set<Integer> removed = new HashSet<>(); Map<Integer,String> added = new TreeMap<>();
        for (String row : diff.split("\n")) {
            if (!row.startsWith("- [") && !row.startsWith("+ [")) continue;
            int bracket = row.indexOf("] "); int line = Integer.parseInt(row.substring(3, bracket));
            String value = row.substring(bracket+2); int marker = value.lastIndexOf(" ⟦");
            String ending = value.substring(marker+2, value.length()-1);
            value = value.substring(0, marker) + switch (ending) {
                case "LF" -> "\n"; case "CRLF" -> "\r\n"; case "CR" -> "\r";
                case "không xuống dòng" -> ""; default -> throw new AssertionError(ending);
            };
            if (row.charAt(0)=='-') {
                if (!before.get(line-1).equals(value)) throw new AssertionError("Wrong deletion");
                removed.add(line);
            } else added.put(line, value);
        }
        for (int i=0; i<before.size(); i++) if (!removed.contains(i+1)) after.add(before.get(i));
        for (var e : added.entrySet()) after.add(e.getKey()-1, e.getValue());
        if (!String.join("", after).equals(next)) throw new AssertionError("Diff reconstruction: " + diff);
    }
    private static void diffTests() {
        String fixed = Room.INITIAL_CODE.replace("<=", "<");
        String diff = CodeDiff.between(Room.INITIAL_CODE, fixed);
        check(diff.contains("- [4]         for (int i = 0; i <= a.length; i++)")
                && diff.contains("+ [4]         for (int i = 0; i < a.length; i++)"), "Loop condition exact line 4 diff");
        String[][] cases = {{"a\nb\nc\n", "x\na\nb\nc\n"}, {"a\nb\nc", "a\nc"},
            {"a\nb\nc\nd\ne", "A\nb\nc\nd\nE"}, {"x\nx\n\nx\n", "x\n\nx\nx\n"},
            {"\r\n Tiếng Việt 😀\r\n\r\n", "\n Tiếng Việt 🧑‍💻\n\n"},
            {"a\n", "a"}, {"a", "a\n"}, {"", "\n"}, {"a\r", "a\r\n"}};
        for (String[] c : cases) diffCheck(c[0], c[1]);
        check(true, "Insert/delete/multiple hunks/duplicates/blanks/Vietnamese/emoji/CRLF/LF/EOF exact reconstruction");
        check(CodeDiff.between("x", "x").equals("Không có thay đổi nội dung"), "Identical content message");
        Random random = new Random(21); String[] lines = {"a\n", "\n", "a\r\n", "😀 Việt\n", "b\r"};
        for (int i=0; i<1000; i++) {
            StringBuilder a=new StringBuilder(), b=new StringBuilder();
            for (int j=0; j<random.nextInt(20); j++) a.append(lines[random.nextInt(lines.length)]);
            for (int j=0; j<random.nextInt(20); j++) b.append(lines[random.nextInt(lines.length)]);
            diffCheck(a.toString(), b.toString());
        }
        check(true, "1000 randomized exact diff reconstructions");
        String middle="same\n".repeat(3000);
        diffCheck("old\n"+middle+"end", "new\n"+middle+"END");
        check(!CodeDiff.between("old\n"+middle+"end", "new\n"+middle+"END").contains("] same"),
                "Sparse diff preserves thousands of duplicate unchanged lines between hunks");
        for (int i=0; i<20; i++) {
            String a="a\n".repeat(600)+"old\n"+"b\n".repeat(600)+"tail";
            String b="z\n"+"a\n".repeat(600-i)+"new\n"+"b\n".repeat(600+i)+"TAIL\n";
            diffCheck(a,b);
        }
        check(true, "Large sparse Myers insert/delete/replace exact reconstructions");
        long start = System.nanoTime();
        diffCheck("a\n".repeat(16000), "b\n".repeat(16000));
        check(System.nanoTime()-start < TimeUnit.SECONDS.toNanos(5), "32000-byte short-line worst case bounded");
        String unicode = ("Tiếng Việt 😀🧑‍💻\r\n").repeat(10000);
        var frames = Protocol.historyFrames(unicode); StringBuilder joined = new StringBuilder();
        for (String frame : frames) {
            if (frame.getBytes(StandardCharsets.UTF_8).length > Protocol.MAX_BYTES) throw new AssertionError("Oversize");
            joined.append(frame.substring(frame.indexOf("]\n")+2));
        }
        check(frames.size()>1 && joined.toString().equals(unicode), "Byte-based Unicode multipart exact reconstruction");
    }
    private static void migrationTests(Path dir) throws Exception {
        Path path=dir.resolve("legacy.db");
        try (Connection db=DriverManager.getConnection("jdbc:sqlite:"+path); Statement s=db.createStatement()) {
            s.execute("CREATE TABLE users(user_id TEXT PRIMARY KEY,username TEXT UNIQUE,password_hash TEXT,display_name TEXT,role TEXT)");
            s.execute("INSERT INTO users VALUES('old-user','legacy','preserved-hash','Tác giả cũ','TEACHER')");
            s.execute("CREATE TABLE rooms(room_id INTEGER PRIMARY KEY AUTOINCREMENT,owner_id TEXT,code TEXT,version INTEGER)");
            s.execute("INSERT INTO rooms VALUES(9,'old-user','c',4)");
            s.execute("CREATE TABLE revisions(room_id INTEGER,version INTEGER,editor_id TEXT,editor_name TEXT,created_at TEXT,code TEXT,PRIMARY KEY(room_id,version))");
            s.execute("INSERT INTO revisions VALUES(9,0,'SYSTEM','Hệ thống','2020-01-01T00:00:00Z','a')");
            s.execute("INSERT INTO revisions VALUES(9,1,'old-user','Tên lúc sửa','2020-01-02T00:00:00Z','b')");
            s.execute("INSERT INTO revisions VALUES(9,4,'unknown-legacy','Không rõ','2020-01-04T00:00:00Z','c')");
            s.execute("CREATE TRIGGER fail_migration BEFORE UPDATE ON revisions BEGIN SELECT RAISE(ABORT,'migration failure'); END");
        }
        try (Store ignored=new Store(path)) { throw new AssertionError("Expected migration failure"); }
        catch (SQLException expected) { check(true, "Injected migration failure reported"); }
        try (Connection db=DriverManager.getConnection("jdbc:sqlite:"+path); Statement s=db.createStatement()) {
            int count=0; try (ResultSet r=s.executeQuery("PRAGMA table_info(revisions)")) { while(r.next()) count++; }
            check(count==6, "Migration failure rolls back schema and backfill together"); s.execute("DROP TRIGGER fail_migration");
        }
        for (int i=0;i<3;i++) try (Store store=new Store(path)) {
            var room=store.loadRooms().getFirst(); var h=room.history();
            check(room.id().equals("9") && room.version()==4 && room.code().equals("c") && h.size()==3, "Migration preserves room/code/history pass " + i);
            check(h.get(0).getChanges().equals(CodeDiff.INITIAL)
                    && h.get(1).getChanges().equals(CodeDiff.between("a","b"))
                    && h.get(2).getChanges().equals(CodeDiff.MISSING), "Backfill consecutive/missing revisions pass " + i);
            check(h.get(1).getEditorId().equals("old-user") && h.get(1).getEditorName().equals("Tên lúc sửa")
                    && h.get(1).getCreatedAt().equals(Instant.parse("2020-01-02T00:00:00Z"))
                    && h.get(2).getEditorId().equals("unknown-legacy"), "Migration preserves author/time pass " + i);
        }
        try (Connection db=DriverManager.getConnection("jdbc:sqlite:"+path); Statement s=db.createStatement();
             ResultSet r=s.executeQuery("SELECT password_hash FROM users WHERE user_id='old-user'")) {
            check(r.next() && r.getString(1).equals("preserved-hash"), "Migration preserves existing account");
        }
    }
    private static void tcpTests(Path path) throws Exception {
        String id; CodeRevision last;
        try (Server server=new Server(0,path)) {
            server.start();
            try (Peer p=new Peer(server)) {
                p.send("/create"); String created=p.await("Đã tạo và tham gia phòng:");
                id=created.substring(created.lastIndexOf(':')+1).trim(); p.await("--- CODE CHUNG ---");
                p.send("/request"); p.await("editor=Giáo viên demo"); Room room=server.rooms.get(id);
                long token=room.snapshot().token();
                String fixed=Room.INITIAL_CODE.replace("<=", "<");
                List<String> codes=new ArrayList<>(List.of(fixed, fixed,
                        "\r\n  // Tiếng Việt 😀\r\n\r\nx\r\n", "\n  // Tiếng Việt 😀\n\nx\n", "\n  // Tiếng Việt 😀\n\nx"));
                codes.add("a\n".repeat(16000)); codes.add("b\n".repeat(16000));
                for (int i=0;i<codes.size();i++) {
                    p.send("/update "+i+" "+token+" "+codes.get(i)); p.await("--- CODE CHUNG ĐÃ CẬP NHẬT ---");
                    var snap=room.snapshot(); last=snap.history().get(i+1);
                    check(snap.version()==i+1 && last.getSourceCode().equals(codes.get(i))
                            && last.getPreviousVersion()==i, "TCP exact source/version " + (i+1));
                    check(last.getChanges().equals("Nạp toàn bộ code bởi giáo viên.\n" + CodeDiff.display(CodeDiff.between(i==0?Room.INITIAL_CODE:codes.get(i-1),codes.get(i)))), "TCP stored server diff " + (i+1));
                    if(i==1) check(last.getChanges().endsWith("Không có thay đổi nội dung"), "Teacher reload no-op policy: accepted save creates version");
                }
                last=room.snapshot().history().getLast();
                p.send("/ping"); p.await("/pong");
                String detail=p.history(last.getVersion());
                check(detail.endsWith(last.detail()) && detail.getBytes(StandardCharsets.UTF_8).length>Protocol.MAX_BYTES,
                        "Real TCP multipart complete detail and full code");
                var before=room.snapshot(); p.history(0); var after=room.snapshot();
                check(before.equals(after), "History read preserves entire room state including permissions");
                p.send("/ping"); p.await("/pong"); p.send("/chat still connected 😀"); p.await("still connected 😀");
                check(true, "Ping/chat survive long history");
                p.send("/update 7 "+(token+1)+" rejected"); p.await("Từ chối cập nhật");
                check(room.snapshot().history().size()==8, "Rejected update adds no history");
            }
        }
        try (Server server=new Server(0,path)) {
            server.start(); var snap=server.rooms.get(id).snapshot(); var loaded=snap.history().getLast();
            check(loaded.detail().equals(last.detail()), "Restart persists exact diff/code/author/time/versions");
            try(Peer p=new Peer(server)) {
                p.send("/join "+id); p.await("--- CODE CHUNG ---");
                check(p.history(7).endsWith(last.detail()), "Restart long history TCP");
            }
        }
    }
    private static Object field(Object object,String name) throws Exception {
        var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void swingTest(Path database) throws Exception {
        if(java.awt.GraphicsEnvironment.isHeadless()) { System.out.println("SKIP ServerAdmin UI: headless"); return; }
        System.setProperty("studyfix.port","0"); System.setProperty("studyfix.screen.port","0"); System.setProperty("studyfix.assignment.port","0"); System.setProperty("studyfix.web.port","0"); System.setProperty("studyfix.db",database.toAbsolutePath().toString());
        Object[] admin={null};
        SwingUtilities.invokeAndWait(()->{
            try { var c=ServerAdmin.class.getDeclaredConstructor(); c.setAccessible(true); admin[0]=c.newInstance(); }
            catch(Exception e) { throw new RuntimeException(e); }
        });
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10); boolean[] ready={false};
            while(!ready[0] && System.nanoTime()<deadline) {
                SwingUtilities.invokeAndWait(()->{try { ready[0]=((JList<?>)field(admin[0],"history")).getModel().getSize()>0; } catch(Exception e){throw new RuntimeException(e);} });
                if(!ready[0]) Thread.sleep(50);
            }
            check(ready[0], "ServerAdmin starts and loads database off EDT");
            SwingUtilities.invokeAndWait(()->{
                try {
                    JList<?> history=(JList<?>)field(admin[0],"history"); history.setSelectedIndex(1);
                    JTextArea text=(JTextArea)field(admin[0],"code");
                    check(!text.isEditable() && text.getText().contains("Phiên bản 0 → 1")
                            && text.getText().contains("Dòng 4:") && text.getText().contains("Trước:")
                            && text.getText().contains("Sau:") && text.getText().endsWith(Room.INITIAL_CODE.replace("<=","<")),
                            "ServerAdmin selecting revision displays metadata/diff/full code read-only");
                    history.setSelectedIndex(0); check(text.getText().contains(CodeDiff.INITIAL), "ServerAdmin initial revision label");
                } catch(Exception e) { throw new RuntimeException(e); }
            });
        } finally {
            SwingUtilities.invokeAndWait(()->{try {var m=ServerAdmin.class.getDeclaredMethod("shutdown",boolean.class);m.setAccessible(true);m.invoke(admin[0],true);}catch(Exception e){throw new RuntimeException(e);} });
        }
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory(Path.of("build"),"history-").toAbsolutePath();
        System.out.println("TEST_DATABASE_DIR="+dir);
        diffTests(); migrationTests(dir); tcpTests(dir.resolve("history.db")); swingTest(dir.resolve("history.db"));
        System.out.println("HISTORY ALL PASSED: "+checks+" checks");
    }
}
