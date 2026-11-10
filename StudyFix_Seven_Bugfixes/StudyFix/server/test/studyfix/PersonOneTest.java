package studyfix;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Kiểm tra các chức năng lõi hiện có bằng TCP có khung dữ liệu và SQLite riêng, giữ nguyên nghiệp vụ. */
public final class PersonOneTest {
    private static String javaExecutable() {
        String exe = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", exe).toString();
    }
    static int passed, failed;
    static void check(boolean ok, String label) {
        System.out.println((ok ? "PASS " : "FAIL ") + label);
        if (ok) passed++; else failed++;
    }
    static void eventually(BooleanSupplier c) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!c.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        if (!c.getAsBoolean()) throw new AssertionError("State did not settle");
    }
    static final class Peer implements AutoCloseable {
        final Socket socket;
        final DataOutputStream out;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        String session;
        Peer(Server s, String name, String password) throws Exception {
            socket = new Socket("127.0.0.1", s.port());
            out = new DataOutputStream(socket.getOutputStream());
            Thread t = new Thread(() -> {
                try {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    while (true) messages.add(Protocol.receive(in));
                } catch (IOException e) { messages.add("[CLOSED]"); }
            }); t.setDaemon(true); t.start();
            if (name != null) {
                send("/login " + name + " " + password);
                session = await("[LOGIN_OK]").split("session=")[1].split(" ")[0];
            }
            heartbeat.scheduleAtFixedRate(() -> {try {send("/ping");} catch (IOException ignored) {}}, 5, 5, TimeUnit.SECONDS);
        }
        void send(String command) throws IOException {Protocol.send(out, command);}
        String await(String needle) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < end) {
                String m = messages.poll(100, TimeUnit.MILLISECONDS);
                if (m != null && m.contains(needle)) return m;
            }
            throw new AssertionError("Missing response: " + needle);
        }
        String command(String cmd, String response) throws Exception {
            send("/ping"); await("/pong"); messages.clear();
            send(cmd); return await(response);
        }
        public void close() throws IOException {heartbeat.shutdownNow(); socket.close();}
    }
    static Room.Snapshot snap(Server s, String id) {return s.rooms.get(id).snapshot();}
    static long request(Peer p, Server s, String id) throws Exception {
        p.send("/request"); eventually(() -> p.session.equals(snap(s,id).editor()));
        return snap(s,id).token();
    }
    static void select(Peer p, Server s, String id, long token, String lines) throws Exception {
        p.command("/select " + snap(s,id).version() + " " + token + " " + lines, "[Bản nháp]");
    }
    static void seed(Peer p, Server s, String id, String code) throws Exception {
        long t = request(p,s,id);
        p.command("/update " + snap(s,id).version() + " " + t + " " + code, "ĐÃ CẬP NHẬT");
        p.command("/release " + t, "editor=không có");
    }
    static void consoleLive(Server s, String id, Path dir) throws Exception {
        Path file=dir.resolve("đoạn có dấu.java"); String content="console thật 😀\r\nline two";
        Files.writeString(file,content);
        Path output=dir.resolve("console-live.log");
        Process client=new ProcessBuilder(javaExecutable(),
                "-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstudyfix.port="+s.port(),
                "-cp",System.getProperty("java.class.path"),"studyfix.Client")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            Writer keyboard=new OutputStreamWriter(client.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8);
            keyboard.write("127.0.0.1\nstudent1\nStudent123!\n/join "+id+"\n/request\n"); keyboard.flush();
            eventually(()->snap(s,id).editor()!=null);
            long t=snap(s,id).token(),v=snap(s,id).version();
            keyboard.write("/select "+v+" "+t+" 1-2\n/rangefile "+t+" "+file+"\n/finish "+t+"\n"); keyboard.flush();
            eventually(()->snap(s,id).version()==v+1);
            check(snap(s,id).code().startsWith(content+"\n"),"F05 actual Java console /rangefile commits exact Unicode CRLF through real server");
            keyboard.write("/quit\n"); keyboard.flush();
            check(client.waitFor(5,TimeUnit.SECONDS) && client.exitValue()==0,"F06 actual console /quit exits cleanly");
        } finally {if(client.isAlive()) client.destroyForcibly().waitFor();}
    }
    static boolean waitForOutput(Path output, String marker, long timeoutMillis) throws Exception {
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while(System.nanoTime()<end) {
            try { if(Files.readString(output, java.nio.charset.StandardCharsets.UTF_8).contains(marker)) return true; }
            catch(java.nio.charset.MalformedInputException ignored) { /* Tiến trình con đang ghi dở ký tự UTF-8. */ }
            catch(IOException ignored) { }
            Thread.sleep(10);
        }
        return false;
    }
    static void consoleStop(Server s, Path dir) throws Exception {
        Path output=dir.resolve("console-server-stop.log");
        Process client=new ProcessBuilder(javaExecutable(),
                "-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstudyfix.port="+s.port(),
                "-cp",System.getProperty("java.class.path"),"studyfix.Client")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            Writer keyboard=new OutputStreamWriter(client.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8);
            keyboard.write("127.0.0.1\nstudent1\nStudent123!\n"); keyboard.flush();
            eventually(()->{try{return Files.readString(output, java.nio.charset.StandardCharsets.UTF_8).contains("Đã kết nối");}catch(IOException e){return false;}});
            s.close();
            check(client.waitFor(5,TimeUnit.SECONDS),"E05 actual Java console exits when real server stops");
            check(waitForOutput(output,"Server đã đóng kết nối.",2000)
                    || waitForOutput(output,"Mất kết nối với server (SocketException).",2000),
                    "E06 actual console reports an explicit server disconnect reason");
        } finally {if(client.isAlive()) client.destroyForcibly().waitFor();}
    }
    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        Path dir = Files.createTempDirectory(Path.of("build"), "person-one-").toAbsolutePath();
        Path database = dir.resolve("studyfix.db");
        System.out.println("TEST_DATABASE=" + database);
        Server s = new Server(0,database); s.start();
        String id; Room.Snapshot persisted;
        int port=s.port();
        try (s; Peer teacher=new Peer(s,"teacher","Teacher123!");
             Peer a=new Peer(s,"student1","Student123!"); Peer b=new Peer(s,"student2","Student234!");
             Peer other=new Peer(s,"teacher2","Teacher234!"); Peer anon=new Peer(s,null,null)) {
            anon.command("/code","cần đăng nhập");
            anon.command("/login nonexistent invalid","[LOGIN_FAILED]");
            anon.command("/login teacher invalid","[LOGIN_FAILED]");
            check(true,"A01 unauthenticated access, unknown account, wrong password rejected");
            a.command("/create","Chỉ giáo viên");
            a.command("/login teacher Teacher123!","không thể đổi danh tính");
            check(s.gateway().identity(a.session).role()==Identity.Role.STUDENT,"A02 authoritative role and immutable identity");
            String created=teacher.command("/create","Đã tạo và tham gia phòng:"); id=created.substring(created.lastIndexOf(':')+1).trim();
            a.command("/join "+id,"--- CODE CHUNG ---"); b.command("/join "+id,"--- CODE CHUNG ---");
            String x=other.command("/create","Đã tạo và tham gia phòng:"); String otherId=x.substring(x.lastIndexOf(':')+1).trim();
            a.command("/join "+id,"/leave trước");
            check(snap(s,id).members().size()==3,"B01 joining twice does not duplicate session");
            b.command("/leave","Đã rời phòng"); b.command("/join "+id,"--- CODE CHUNG ---");
            check(snap(s,id).members().size()==3,"B02 leave and rejoin restores one membership");
            try(Peer duplicate=new Peer(s,"student1","Student123!")) {
                duplicate.command("/join "+id,"--- CODE CHUNG ---");
                check(s.gateway().identity(duplicate.session).userId().equals(s.gateway().identity(a.session).userId())
                        && !duplicate.session.equals(a.session) && snap(s,id).members().size()==4,
                        "A03 documented multi-session policy: same user, distinct sessions/members");
            }
            eventually(()->snap(s,id).members().size()==3);
            other.command("/ping","/pong"); other.messages.clear();
            a.command("/chat isolation-marker","isolation-marker");
            Thread.sleep(100);
            check(other.messages.stream().noneMatch(m->m.contains("isolation-marker")),"B05 chat event never reaches other room");
            other.command("/ping","/pong");
            check(snap(s,otherId).version()==0 && snap(s,otherId).queue().isEmpty(),"B03 second room code/history/queue isolated");
            anon.command("/login student2 Student234!","[LOGIN_OK]");
            anon.command("/history","chưa tham gia phòng"); anon.command("/select 0 1 1","chưa tham gia phòng");
            check(true,"B04 outsider cannot read history or edit");
            seed(teacher,s,id,"one\r\ntwo\nthree\nfour\nfive");
            long t=request(a,s,id);
            teacher.send("/request"); eventually(()->snap(s,id).queue().size()==1);
            b.send("/request"); eventually(()->snap(s,id).queue().size()==2);
            b.command("/request","đã ở trong hàng đợi");
            check(snap(s,id).queue().equals(List.of(teacher.session,b.session)),"C01 FIFO and duplicate requests");
            a.command("/update 1 "+t+" forbidden","Chỉ giáo viên chủ phòng");
            other.command("/join "+id,"/leave trước");
            check(true,"A04 student full update rejected");
            Room.Snapshot original=snap(s,id);
            for(String invalid:List.of("1,1","0","999","4-2","1-99","1,,2","1-2,4","2147483648")) {
                a.command("/select 1 "+t+" "+invalid,"Chọn dòng hợp lệ");
                check(snap(s,id).equals(original),"C02 invalid selection preserves state: "+invalid);
            }
            a.command("/select 0 "+t+" 1","version cũ");
            a.command("/select 1 "+(t-1)+" 1","Từ chối");
            b.command("/select 1 "+t+" 1","Từ chối");
            a.command("/select nonsense","Sai cú pháp");
            check(true,"C03 stale version/token, wrong editor and syntax rejected");
            select(a,s,id,t,"1,3,5");
            a.command("/delete "+t+" 1","[Bản nháp]");
            a.command("/finish "+t,"Chưa xử lý đủ");
            a.command("/release "+t,"Còn bản nháp");
            a.command("/setline "+t+" 2 outside","không nằm");
            a.command("/setline "+t+" 3 bad\nline","không nhận");
            a.command("/setline "+t+" 3 ba 😀","[Bản nháp]");
            a.command("/keep "+t+" 5","[Bản nháp]");
            check(snap(s,id).equals(original),"C04 draft, incomplete finish, release and invalid edits leave committed state unchanged");
            String preview=a.command("/draft "+t,"XEM TRƯỚC CODE");
            String expected="two\nba 😀\nfour\nfive";
            check(preview.endsWith(expected),"C05 draft preview uses original positions after preceding deletion");
            check(a.command("/code","--- CODE CHUNG ---").endsWith(original.code()),"C06 /code retains committed snapshot");
            Instant before=Instant.now();
            a.command("/finish "+t,"[COMMITTED]");
            Room.Snapshot committed=snap(s,id);
            check(committed.version()==2 && committed.history().size()==3 && committed.code().equals(expected)
                    && teacher.session.equals(committed.editor()),"C07 finish makes one revision then transfers FIFO");
            CodeRevision rev=committed.history().get(2);
            check(rev.getEditorId().equals(s.gateway().identity(a.session).userId()) && rev.getEditorName().equals("Học sinh 1")
                    && !rev.getCreatedAt().isBefore(before) && rev.getPreviousVersion()==1 && rev.getSourceCode().equals(expected),
                    "D01 author/name/time/previous version/exact full code");
            check(rev.getChanges().contains("Dòng 1:") && rev.getChanges().contains("one")
                    && rev.getChanges().contains("Dòng 3:") && rev.getChanges().contains("three") && rev.getChanges().contains("ba 😀")
                    && rev.getChanges().contains("Dòng 5:") && rev.getChanges().contains("Giữ nguyên"),"D02 selected regions before/after/keep recorded");
            long tt=committed.token();
            select(teacher,s,id,tt,"1-2"); teacher.command("/setrange "+tt+" short","[Bản nháp]");
            teacher.command("/finish "+tt,"[COMMITTED]");
            check(snap(s,id).code().equals("short\nfour\nfive") && b.session.equals(snap(s,id).editor()),"C08 shrink contiguous range and FIFO");
            long bt=snap(s,id).token(); select(b,s,id,bt,"1-2");
            b.command("/setrange "+bt+" a\r\nb\nc\nd","[Bản nháp]"); b.command("/finish "+bt,"[COMMITTED]");
            check(snap(s,id).code().equals("a\r\nb\nc\nd\nfive"),"C09 expand range preserves CRLF/LF and boundary");
            t=request(a,s,id); select(a,s,id,t,"1,2"); a.command("/setline "+t+" 1 ","[Bản nháp]");
            a.command("/delete "+t+" 2","[Bản nháp]"); a.command("/finish "+t,"[COMMITTED]");
            check(snap(s,id).code().equals("\r\nc\nd\nfive"),"C10 blank line differs from deletion");
            t=request(a,s,id); select(a,s,id,t,"2"); Room.Snapshot noOp=snap(s,id);
            a.command("/keep "+t+" 2","[Bản nháp]"); a.command("/finish "+t,"[NO_CHANGE]");
            check(snap(s,id).version()==noOp.version() && snap(s,id).history().equals(noOp.history()) && snap(s,id).editor()==null,
                    "C11 no-change finish releases without version/history");
            t=request(a,s,id); select(a,s,id,t,"1-2"); a.command("/setrange "+t+" cancelled","[Bản nháp]");
            Room.Snapshot cancel=snap(s,id); a.command("/cancel "+t,"[CANCELLED]");
            check(snap(s,id).code().equals(cancel.code()) && snap(s,id).version()==cancel.version() && snap(s,id).history().equals(cancel.history()),"C12 cancel discards entire draft");
            t=request(a,s,id); select(a,s,id,t,"2"); a.command("/setline "+t+" 2 retry","[Bản nháp]");
            Room.Snapshot failedSave=snap(s,id);
            try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+database); Statement sql=db.createStatement()) {
                sql.execute("CREATE TRIGGER fail_turn BEFORE INSERT ON revisions BEGIN SELECT RAISE(ABORT,'verification failure'); END");
                a.command("/finish "+t,"[SAVE_FAILED]");
                check(snap(s,id).equals(failedSave),"D03 failed save retains RAM/version/history/editor/token/queue");
                try(ResultSet r=sql.executeQuery("SELECT code,version,(SELECT count(*) FROM revisions WHERE room_id="+id+") FROM rooms WHERE room_id="+id)) {
                    r.next(); check(r.getString(1).equals(failedSave.code()) && r.getLong(2)==failedSave.version()
                            && r.getInt(3)==failedSave.history().size(),"D04 failed second SQL statement rolls back entire transaction");
                }
                check(a.command("/draft "+t,"XEM TRƯỚC CODE").contains("retry"),"D05 failed save retains draft for retry");
                sql.execute("DROP TRIGGER fail_turn");
            }
            a.command("/finish "+t,"[COMMITTED]");
            check(snap(s,id).version()==failedSave.version()+1,"D06 retry succeeds exactly once after database recovers");
            String stable=snap(s,id).code();
            t=request(a,s,id); select(a,s,id,t,"1-2"); a.command("/setrange "+t+" valid","[Bản nháp]");
            String validPreview=a.command("/draft "+t,"XEM TRƯỚC CODE");
            a.command("/setrange "+t+" "+"x".repeat(32001),"vượt 32000");
            check(a.command("/draft "+t,"XEM TRƯỚC CODE").equals(validPreview),"C13 oversized range preserves previous valid draft");
            a.command("/cancel "+t,"[CANCELLED]");
            t=request(a,s,id); select(a,s,id,t,"2");
            a.command("/setline "+t+" 2 "+"x".repeat(32000),"vượt 32000");
            check(a.command("/draft "+t,"XEM TRƯỚC CODE").endsWith(stable),"C14 combined draft limit rolls back individual replacement");
            a.command("/cancel "+t,"[CANCELLED]");
            long ownerToken=request(teacher,s,id);
            teacher.command("/update "+snap(s,id).version()+" "+ownerToken+" "+"x".repeat(32001),"tối đa 32000");
            teacher.command("/release "+ownerToken,"editor=không có");
            check(snap(s,id).code().equals(stable),"C15 oversized teacher load preserves code");
            other.command("/leave","Đã rời phòng"); other.command("/join "+id,"--- CODE CHUNG ---");
            long ot=request(other,s,id);
            other.command("/update "+snap(s,id).version()+" "+ot+" forbidden-owner","Chỉ giáo viên chủ phòng");
            other.command("/release "+ot,"editor=không có");
            check(snap(s,id).code().equals(stable),"A05 other teacher cannot load owner room even holding edit token");
            other.command("/leave","Đã rời phòng"); other.command("/join "+otherId,"--- CODE CHUNG ---");
            long keep=request(b,s,id); select(b,s,id,keep,"1-2");
            Room.Snapshot kept=snap(s,id); b.command("/keeprange "+keep,"[Bản nháp]"); b.command("/finish "+keep,"[NO_CHANGE]");
            check(snap(s,id).version()==kept.version() && snap(s,id).history().equals(kept.history()),"C16 keeping entire range creates no revision");
            consoleLive(s,id,dir); stable=snap(s,id).code();
            long dt=request(b,s,id); select(b,s,id,dt,"1-2");
            String afterDelete=String.join("",CodeDiff.lines(stable).subList(2,CodeDiff.lines(stable).size()));
            b.command("/deleterange "+dt,"[Bản nháp]"); b.command("/finish "+dt,"[COMMITTED]");
            check(snap(s,id).code().equals(afterDelete),"C17 deleting contiguous range removes its lines");
            seed(teacher,s,id,stable);
            long same=request(b,s,id); select(b,s,id,same,"2");
            Room.Snapshot identical=snap(s,id);
            b.command("/setline "+same+" 2 line two","[Bản nháp]"); b.command("/finish "+same,"[NO_CHANGE]");
            check(snap(s,id).version()==identical.version() && snap(s,id).history().equals(identical.history()),"C18 replacing with identical text creates no revision");
            t=request(a,s,id); select(a,s,id,t,"2"); a.command("/setline "+t+" 2 lost","[Bản nháp]");
            b.send("/request"); eventually(()->snap(s,id).queue().contains(b.session));
            a.command("/leave","Đã rời phòng");
            check(b.session.equals(snap(s,id).editor()) && snap(s,id).code().equals(stable),"E01 leave discards draft and transfers FIFO");
            b.command("/draft "+snap(s,id).token(),"Chưa có bản nháp");
            b.command("/release "+snap(s,id).token(),"editor=không có");
            a.command("/join "+id,"--- CODE CHUNG ---");
            t=request(a,s,id); select(a,s,id,t,"2"); a.command("/setline "+t+" 2 disconnected","[Bản nháp]");
            b.send("/request"); eventually(()->snap(s,id).queue().contains(b.session)); a.close();
            eventually(()->b.session.equals(snap(s,id).editor()));
            check(snap(s,id).code().equals(stable),"E02 abrupt disconnect discards draft without save");
            b.command("/draft "+snap(s,id).token(),"Chưa có bản nháp");
            b.command("/release "+snap(s,id).token(),"editor=không có");
            teacher.command("/history 1","CODE SAU KHI SỬA");
            check(snap(s,id).code().equals(stable),"D07 viewing old revision does not alter current code");
            check(s.gateway().codeSnapshot(b.session,id).code().equals(stable) && s.gateway().isMember(b.session,id),"F01 gateway immutable snapshot and membership");
            s.gateway().sendToUser(teacher.session,b.session,id,"gateway-private"); b.await("gateway-private");
            s.gateway().sendToRoom(teacher.session,id,"gateway-room"); b.await("gateway-room");
            try{s.gateway().sendToUser(other.session,b.session,otherId,"forbidden");check(false,"F02 gateway cross-room event denied");}
            catch(SecurityException ok){check(true,"F02 gateway private/room events and cross-room restriction");}
            try{s.gateway().codeSnapshot(other.session,id); check(false,"F03 outsider snapshot denied");}
            catch(SecurityException ok){check(true,"F03 outsider snapshot denied");}
            t=request(b,s,id); select(b,s,id,t,"2"); b.command("/setline "+t+" 2 restart-draft","[Bản nháp]");
            teacher.send("/request"); eventually(()->snap(s,id).queue().contains(teacher.session));
            persisted=snap(s,id);
            check(s.snapshot().connections()>=4 && s.snapshot().rooms().size()==2,"F04 admin data snapshot has live rooms/connections");
            System.out.println("METRIC peak connections=6; simultaneous rooms=2; max command data=32001 ASCII bytes; loopback only");
        }
        try(ServerSocket probe=new ServerSocket(port)) {check(true,"E03 stopped server releases listening port");}
        check(s.monitor.isShutdown() && s.clients.isEmpty(),"E04 stop removes clients and heartbeat scheduler");
        try(Server restart=new Server(0,database)) {
            restart.start(); Room.Snapshot restored=snap(restart,id);
            check(restored.code().equals(persisted.code()) && restored.version()==persisted.version()
                    && restored.history().size()==persisted.history().size(),"D08 restart restores committed code/version/history");
            check(restored.members().isEmpty() && restored.queue().isEmpty() && restored.editor()==null && restored.token()==0,
                    "D09 restart clears online/editor/token/queue");
            try(Peer p=new Peer(restart,"teacher","Teacher123!")) {
                p.command("/join "+id,"--- CODE CHUNG ---"); long t=request(p,restart,id);
                p.command("/draft "+t,"Chưa có bản nháp");
                check(true,"D10 restart does not restore unfinished draft");
            }
            consoleStop(restart,dir);
        }
        System.out.println("PERSON_ONE_RESULT passed="+passed+" failed="+failed+" elapsed_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
        if(failed>0) throw new AssertionError("Contract failures: "+failed);
    }
}
