package studyfix;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Audit-only harness. Never writes production source or opens a production database. */
public final class FullAudit {
    static final Path ROOT = Path.of("verification/person-one-20261021").toAbsolutePath();
    static final ScheduledExecutorService HB = Executors.newScheduledThreadPool(2);
    static final Map<String,String[]> RESULTS = new TreeMap<>();
    static final AtomicInteger PEAK = new AtomicInteger();
    static final long START = System.nanoTime();
    @FunctionalInterface interface Action { void run() throws Exception; }
    static synchronized void result(String ids, String status, String evidence) {
        for (String id : ids.split(" ")) {
            String[] previous = RESULTS.get(id);
            if (previous != null && previous[0].equals("FAIL")) continue;
            RESULTS.put(id,new String[]{status,evidence.replace('\t',' ').replace('\n',' ')});
            System.out.println("AUDIT " + id + " " + status + " " + evidence);
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(ROOT.resolve("results.tsv")))) {
            for (var e : RESULTS.entrySet()) out.println(e.getKey()+"\t"+e.getValue()[0]+"\t"+e.getValue()[1]);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    static void test(String ids,String evidence,Action action) {
        try { action.run(); result(ids,"PASS",evidence); }
        catch (Throwable e) { result(ids,"FAIL",evidence+"; "+e.getClass().getSimpleName()+": "+e.getMessage()); }
    }
    static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static void until(BooleanSupplier condition,long ms) throws Exception {
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);
        while(!condition.getAsBoolean() && System.nanoTime()<end) Thread.sleep(10);
        require(condition.getAsBoolean(),"Condition not reached within "+ms+"ms");
    }
    static Object field(Object object,String name) throws Exception {
        var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    static final class Peer implements AutoCloseable {
        final Socket socket=new Socket(); final DataOutputStream out;
        final List<String> events=new CopyOnWriteArrayList<>();
        final AtomicInteger pongs=new AtomicInteger(); final AtomicBoolean closed=new AtomicBoolean();
        final ScheduledFuture<?> heartbeat; int pings; String session; final Server server;
        Peer(Server server,String user,String password) throws Exception {
            this.server=server; socket.setTcpNoDelay(true); socket.connect(new InetSocketAddress("127.0.0.1",server.port()));
            out=new DataOutputStream(socket.getOutputStream());
            Thread reader=new Thread(()->{
                try {
                    DataInputStream in=new DataInputStream(socket.getInputStream());
                    StringBuilder parts=null; int next=1,total=0;
                    for(;;) {
                        String value=Protocol.receive(in);
                        if(value.equals("/pong")) { pongs.incrementAndGet(); continue; }
                        if(value.startsWith("[HISTORY_PART ")) {
                            int end=value.indexOf("]\n"); String[] pos=value.substring(14,end).split("/");
                            int index=Integer.parseInt(pos[0]);
                            if(index==1) { parts=new StringBuilder();next=1;total=Integer.parseInt(pos[1]); }
                            if(parts==null || index!=next++) throw new IOException("Multipart sequence invalid");
                            parts.append(value.substring(end+2));
                            if(index==total) {events.add(parts.toString());parts=null;}
                        } else events.add(value);
                    }
                } catch(Exception e) { closed.set(true); }
            },"audit-reader"); reader.setDaemon(true);reader.start();
            if(user!=null) {
                int from=events.size(); send("/login "+user+" "+password);
                String reply=await(from,"[LOGIN_OK]"); session=reply.split("session=")[1].split(" ")[0];
            }
            heartbeat=HB.scheduleAtFixedRate(()->{try{send("/ping");}catch(IOException ignored){}},5,5,TimeUnit.SECONDS);
            PEAK.accumulateAndGet(server.clients.size(),Math::max);
        }
        synchronized void send(String command) throws IOException {
            if(command.equals("/ping")) pings++;
            Protocol.send(out,command);
        }
        void barrier() throws Exception {
            int target; synchronized(this){send("/ping");target=pings;}
            until(()->pongs.get()>=target || closed.get(),5000); require(!closed.get(),"Socket closed before barrier");
        }
        String await(int from,String marker) throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
            do {
                for(int i=from;i<events.size();i++) if(events.get(i).contains(marker))return events.get(i);
                if(closed.get())throw new AssertionError("Closed while awaiting response marker "+marker);
                Thread.sleep(5);
            }while(System.nanoTime()<end);
            throw new AssertionError("Missing response marker "+marker);
        }
        String cmd(String command,String marker) throws Exception {barrier();int from=events.size();send(command);return await(from,marker);}
        public void close() throws IOException {heartbeat.cancel(false);socket.close();}
    }
    static final class Fixture implements AutoCloseable {
        final Path db; final Server s; final Peer owner,a,b,other,outside; final String id,second;
        Fixture() throws Exception {
            db=Files.createTempDirectory(ROOT,"fixture-").resolve("audit.db");s=new Server(0,db);s.start();
            owner=new Peer(s,"teacher","Teacher123!"); a=new Peer(s,"student1","Student123!");
            b=new Peer(s,"student2","Student234!"); other=new Peer(s,"teacher2","Teacher234!");outside=new Peer(s,"student2","Student234!");
            id=create(owner);a.cmd("/join "+id,"--- CODE CHUNG ---");b.cmd("/join "+id,"--- CODE CHUNG ---");second=create(other);
            System.out.println("FIXTURE db="+db+" port="+s.port()+" sessions="+s.clients.size());
        }
        String create(Peer p)throws Exception {String r=p.cmd("/create","Đã tạo và tham gia phòng:");p.barrier();return r.substring(r.lastIndexOf(':')+1).trim();}
        Room room(){return s.rooms.get(id);} Room.Snapshot snap(){return room().snapshot();}
        long request(Peer p)throws Exception {p.cmd("/request","editor=");until(()->p.session.equals(snap().editor()),3000);return snap().token();}
        void select(Peer p,long t,String selection)throws Exception {p.cmd("/select "+snap().version()+" "+t+" "+selection,"[Bản nháp]");}
        void seed(String code)throws Exception {long t=request(owner);owner.cmd("/update "+snap().version()+" "+t+" "+code,"ĐÃ CẬP NHẬT");owner.cmd("/release "+t,"editor=không có");}
        void sameData(Room.Snapshot before) {Room.Snapshot after=snap();require(after.code().equals(before.code())&&after.version()==before.version()&&after.history().equals(before.history()),"Committed data changed");}
        public void close()throws Exception {for(Peer p:List.of(owner,a,b,other,outside))p.close();s.close();}
    }
    static void authAndRooms()throws Exception {
        try(Fixture f=new Fixture();Peer anon=new Peer(f.s,null,null)) {
            test("B01 C01","Real logins, two teacher-owned rooms, server-side member identities",()->{
                require(f.s.gateway().identity(f.owner.session).role()==Identity.Role.TEACHER,"Owner role");
                require(f.s.gateway().identity(f.a.session).role()==Identity.Role.STUDENT,"Student role");require(f.snap().members().size()==3,"Member count");
            });
            test("B02","Wrong password and unknown account rejected",()->{anon.cmd("/login teacher wrong-audit-value","[LOGIN_FAILED]");anon.cmd("/login no_such_audit_user wrong-audit-value","[LOGIN_FAILED]");});
            test("B03 B04","Unauthenticated room/code and self-declared teacher role rejected",()->{anon.cmd("/create","cần đăng nhập");anon.cmd("/code","cần đăng nhập");anon.cmd("/role TEACHER","cần đăng nhập");anon.cmd("/name teacher","cần đăng nhập");require(f.s.clients.values().stream().filter(c->c.identity()==null).count()==1,"Identity spoofed");});
            test("B05","Identity cannot change on an authenticated connection",()->{f.a.cmd("/login teacher ignored-value","không thể đổi danh tính");require(f.s.gateway().identity(f.a.session).role()==Identity.Role.STUDENT,"Role changed");});
            test("B06","Same account has distinct session IDs/members; disconnect removes only its session",()->{
                try(Peer duplicate=new Peer(f.s,"student1","Student123!")){duplicate.cmd("/join "+f.id,"--- CODE CHUNG ---");require(f.s.gateway().identity(duplicate.session).userId().equals(f.s.gateway().identity(f.a.session).userId()),"userId differs");require(!duplicate.session.equals(f.a.session)&&f.snap().members().size()==4,"Session duplicated");long t=f.request(f.a);duplicate.cmd("/select "+f.snap().version()+" "+t+" 1","Từ chối");f.a.cmd("/release "+t,"editor=không có");}
                until(()->f.snap().members().size()==3,3000);
            });
            test("B07 B08","Server rejects student create and full-code update even when holding the edit token",()->{f.outside.cmd("/create","Chỉ giáo viên");long t=f.request(f.a);var old=f.snap();f.a.cmd("/update "+old.version()+" "+t+" forbidden","Chỉ giáo viên chủ phòng");f.sameData(old);f.a.cmd("/release "+t,"editor=không có");});
            test("B09","Other teacher cannot kick or load code in a room it does not own",()->{
                f.other.cmd("/leave","Đã rời phòng");f.other.cmd("/join "+f.id,"--- CODE CHUNG ---");
                f.other.cmd("/kick "+f.a.session,"Chỉ giáo viên chủ phòng");long t=f.request(f.other);f.other.cmd("/update "+f.snap().version()+" "+t+" denied","Chỉ giáo viên chủ phòng");f.other.cmd("/release "+t,"editor=không có");f.other.cmd("/leave","Đã rời phòng");f.other.cmd("/join "+f.second,"--- CODE CHUNG ---");
            });
            test("B10","Authenticated outsider cannot read code/lines/history or operate a draft",()->{for(String cmd:List.of("/code","/lines","/history","/select 0 1 1","/update 0 1 x"))f.outside.cmd(cmd,"chưa tham gia phòng");});
            test("B11","SQLite contains independently salted PBKDF2 hashes, not plaintext (values never logged)",()->{
                try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+f.db);Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT password_hash FROM users")){Set<String> salts=new HashSet<>();int count=0;while(r.next()){String[] hash=r.getString(1).split(":");require(hash.length==3&&hash[0].equals("600000")&&Base64.getDecoder().decode(hash[1]).length==16,"Hash format");salts.add(hash[1]);count++;}require(count==4&&salts.size()==4,"Independent salts");}
            });
            test("B12","Missing, malformed and oversized login parameters leave the server responsive",()->{for(String c:List.of("/login","/login only_username","/login "+"u".repeat(65)+" p","/login u "+"p".repeat(257)))anon.cmd(c,"Cách dùng");anon.barrier();f.owner.barrier();});
            test("C02 C03","Invalid/missing room ID rejected; rejoin while already in a room does not duplicate members",()->{f.outside.cmd("/join no-such-room","Không tìm thấy phòng");f.outside.cmd("/join","Không tìm thấy phòng");f.a.cmd("/join "+f.id,"/leave trước");require(f.snap().members().size()==3,"Duplicate membership");});
            test("C04 C05","Leave/rejoin counts and correctly scoped identity notifications verified with ordered barriers",()->{
                f.owner.barrier();f.other.barrier();int first=f.owner.events.size(),otherStart=f.other.events.size();
                f.a.cmd("/leave","Đã rời phòng");f.owner.await(first,"Học sinh 1 đã rời phòng");require(f.snap().members().size()==2,"Leave count");
                first=f.owner.events.size();f.a.cmd("/join "+f.id,"--- CODE CHUNG ---");f.owner.await(first,"Học sinh 1 đã vào phòng");f.other.barrier();
                require(f.snap().members().size()==3&&f.other.events.subList(otherStart,f.other.events.size()).stream().noneMatch(x->x.contains("Học sinh 1 đã")),"Room notification leaked");
            });
            test("C06 C07 F09","Two independent rooms: Unicode/emoji chat both ways; code/history/editor/queue remain scoped",()->{
                f.seed("room-one\nline2\n");f.other.cmd("/request","editor=");long ot=f.s.rooms.get(f.second).snapshot().token();f.other.cmd("/update 0 "+ot+" room-two","ĐÃ CẬP NHẬT");
                f.other.barrier();int start=f.other.events.size();int bStart=f.b.events.size();f.a.cmd("/chat Xin chào 😀 ROOM_ONE_MARKER","ROOM_ONE_MARKER");f.b.await(bStart,"Xin chào 😀 ROOM_ONE_MARKER");
                int aStart=f.a.events.size();f.b.cmd("/chat Chào lại 🧑‍💻 ROOM_ONE_REPLY","ROOM_ONE_REPLY");f.a.await(aStart,"ROOM_ONE_REPLY");f.other.barrier();
                require(f.other.events.subList(start,f.other.events.size()).stream().noneMatch(x->x.contains("ROOM_ONE_")),"Chat leaked");
                long at=f.request(f.a);f.b.cmd("/request","editor=");require(f.snap().queue().equals(List.of(f.b.session))&&f.s.rooms.get(f.second).snapshot().queue().isEmpty(),"Queue leaked");
                require(f.snap().code().startsWith("room-one")&&f.s.rooms.get(f.second).snapshot().code().equals("room-two"),"Code leaked");
                String history=f.a.cmd("/history 1","CODE SAU KHI SỬA");require(!history.contains("room-two"),"History leaked");f.a.cmd("/release "+at,"editor=");f.b.cmd("/release "+f.snap().token(),"editor=không có");f.other.cmd("/release "+ot,"editor=không có");
            });
            test("C08","Outside-room, empty and oversized chat rejected",()->{f.outside.cmd("/chat outside","chưa tham gia phòng");f.a.cmd("/chat","Cách dùng");f.a.cmd("/chat "+"x".repeat(2001),"tối đa 2000");});
            test("C10","Members/code/numbered lines match the authoritative room snapshot",()->{require(f.a.cmd("/members","Thành viên phòng").contains(f.owner.session),"Missing session");require(f.a.cmd("/code","--- CODE CHUNG ---").endsWith(f.snap().code()),"Code differs");require(f.a.cmd("/lines","SỐ DÒNG").contains("1 | room-one"),"Line numbering");});
            test("C09","One client exiting leaves server and other clients operational",()->{f.b.close();until(()->!f.s.clients.containsKey(f.b.session),3000);f.a.cmd("/chat still-running","still-running");f.owner.barrier();require(f.s.isRunning(),"Server stopped");});
        }
    }
    static final String BASE="one\ntwo\nthree\nfour\nfive\n";
    static void queueTests()throws Exception {
        try(Fixture f=new Fixture()) {
            long t=f.request(f.a);
            test("D01 D02 D03 D04","Exactly one editor; ordered FIFO and idempotent repeated requests",()->{
                f.b.cmd("/request","editor=");f.owner.cmd("/request","editor=");f.b.cmd("/request","đã ở trong hàng đợi");f.a.cmd("/request","đang giữ quyền");
                require(f.snap().editor().equals(f.a.session)&&f.snap().queue().equals(List.of(f.b.session,f.owner.session)),"Wrong FIFO/editor");
            });
            test("D05 D07 D08 D12","Release without draft advances FIFO, invalid/old tokens rejected, empty queue clears editor",()->{
                f.b.cmd("/select "+f.snap().version()+" "+t+" 1","Từ chối");f.a.cmd("/release "+t,"editor=");
                require(f.snap().editor().equals(f.b.session),"FIFO skipped");f.a.cmd("/select "+f.snap().version()+" "+t+" 1","Từ chối");
                f.b.cmd("/release "+f.snap().token(),"editor=");f.owner.cmd("/release "+f.snap().token(),"editor=không có");require(f.snap().editor()==null,"Editor retained");
            });
            test("D06 D13","Draft blocks release, and owner cannot load code over an active turn",()->{
                long at=f.request(f.a);f.select(f.a,at,"1");var old=f.snap();f.a.cmd("/release "+at,"Còn bản nháp");
                f.owner.cmd("/update "+old.version()+" "+at+" no-overwrite","hoàn tất hoặc hủy bản nháp");f.sameData(old);require(f.snap().editor().equals(f.a.session),"Lost editor");f.a.cmd("/cancel "+at,"[CANCELLED]");
            });
            test("D09 D11","Leaving/disconnected queued sessions removed or skipped when transferring rights",()->{
                long at=f.request(f.a);f.b.cmd("/request","editor=");f.owner.cmd("/request","editor=");f.b.cmd("/leave","Đã rời phòng");require(f.snap().queue().equals(List.of(f.owner.session)),"Queue retains leaver");
                f.a.cmd("/release "+at,"editor=");f.owner.cmd("/release "+f.snap().token(),"editor=không có");f.b.cmd("/join "+f.id,"--- CODE CHUNG ---");
                at=f.request(f.a);try(Peer q=new Peer(f.s,"student1","Student123!")){q.cmd("/join "+f.id,"--- CODE CHUNG ---");q.cmd("/request","editor=");f.owner.cmd("/request","editor=");q.socket.setSoLinger(true,0);q.close();f.a.cmd("/release "+at,"editor=");until(()->f.owner.session.equals(f.snap().editor()),3000);}
                f.owner.cmd("/release "+f.snap().token(),"editor=không có");
            });
            test("D10","Holder leaves with unfinished draft: discard draft and transfer to next member",()->{
                long at=f.request(f.a);f.select(f.a,at,"1");f.a.cmd("/setline "+at+" 1 uncommitted","[Bản nháp]");f.b.cmd("/request","editor=");var old=f.snap();f.a.cmd("/leave","Đã rời phòng");until(()->f.b.session.equals(f.snap().editor()),3000);f.sameData(old);require(field(f.room(),"draft")==null,"Draft retained");f.b.cmd("/release "+f.snap().token(),"editor=không có");f.a.cmd("/join "+f.id,"--- CODE CHUNG ---");
            });
            test("C11 D10","Owner kick discards holder draft and removes session/queue before handing over",()->{
                long at=f.request(f.a);f.select(f.a,at,"1");f.a.cmd("/setline "+at+" 1 kick-draft","[Bản nháp]");f.b.cmd("/request","editor=");var old=f.snap();f.owner.cmd("/kick "+f.a.session,"Đã đóng phiên thành viên");until(()->!f.s.clients.containsKey(f.a.session)&&f.b.session.equals(f.snap().editor()),3000);f.sameData(old);require(field(f.room(),"draft")==null&&!f.snap().queue().contains(f.a.session),"Kick retained draft/queue");
            });
        }
    }
    static void draftTests()throws Exception {
        try(Fixture f=new Fixture()) {
            f.seed(BASE);
            test("E01 E02","Single/discrete/range selection works; zero/negative/duplicate/overflow/reversed/malformed selections rejected",()->{
                long t=f.request(f.a);for(String selection:List.of("0","-1","99","1,1","4-2","1,,2","1-2,4","2147483648")){f.a.cmd("/select "+f.snap().version()+" "+t+" "+selection,"Chọn dòng hợp lệ");require(field(f.room(),"draft")==null,"Invalid draft created");}
                for(String selection:List.of("1","1,3,5","2-4")){f.select(f.a,t,selection);f.a.cmd("/cancel "+t,"[CANCELLED]");t=f.request(f.a);}f.a.cmd("/release "+t,"editor=không có");
            });
            test("E03 E04 E05 E06 E09 E11","Draft edits preserve committed code and indentation/Unicode; reselect/outside/newline edits rejected",()->{
                long t=f.request(f.a);f.select(f.a,t,"1");var old=f.snap();f.a.cmd("/select "+old.version()+" "+t+" 2","Đã có bản nháp");f.a.cmd("/setline "+t+" 2 outside","không nằm");
                f.a.cmd("/setline "+t+" 1     Xin chào 😀","[Bản nháp]");String draft=f.a.cmd("/draft "+t,"XEM TRƯỚC CODE");require(draft.endsWith("    Xin chào 😀\ntwo\nthree\nfour\nfive\n"),"Indentation/Unicode lost");
                f.a.cmd("/setline "+t+" 1 bad\nline","không nhận");require(f.a.cmd("/draft "+t,"XEM TRƯỚC CODE").equals(draft),"Bad edit changed draft");
                f.a.cmd("/setline "+t+" 1     Changed 🧑‍💻","[Bản nháp]");require(f.a.cmd("/draft "+t,"XEM TRƯỚC CODE").contains("    Changed 🧑‍💻"),"Revision of draft missing");require(f.a.cmd("/code","--- CODE CHUNG ---").endsWith(BASE),"Premature code publish");f.sameData(old);f.a.cmd("/cancel "+t,"[CANCELLED]");
            });
            test("E07","Blank selected line is distinct from a deleted line",()->{long t=f.request(f.a);f.select(f.a,t,"1,2");f.a.cmd("/setline "+t+" 1 ","[Bản nháp]");f.a.cmd("/delete "+t+" 2","[Bản nháp]");require(f.a.cmd("/draft "+t,"XEM TRƯỚC CODE").endsWith("\nthree\nfour\nfive\n"),"Blank/delete confused");f.a.cmd("/cancel "+t,"[CANCELLED]");});
            test("E08 E10","Each selected line must be processed; early finish preserves draft and rights",()->{long t=f.request(f.a);f.select(f.a,t,"1,3,5");f.a.cmd("/keep "+t+" 1","[Bản nháp]");var old=f.snap();f.a.cmd("/finish "+t,"Chưa xử lý đủ");require(f.snap().editor().equals(f.a.session)&&field(f.room(),"draft")!=null,"Lost unfinished turn");f.sameData(old);f.a.cmd("/cancel "+t,"[CANCELLED]");});
            test("E12 E21 F02 F03 F05","Original line indices survive deletion; one atomic revision has author/time/selected before-after/keep and full code",()->{
                long t=f.request(f.a);f.select(f.a,t,"1,3,5");var old=f.snap();f.a.cmd("/delete "+t+" 1","[Bản nháp]");f.a.cmd("/setline "+t+" 3 THREE 😀","[Bản nháp]");f.a.cmd("/keep "+t+" 5","[Bản nháp]");
                f.b.cmd("/request","editor=");Instant before=Instant.now();f.a.cmd("/finish "+t,"[COMMITTED]");var now=f.snap();
                require(now.version()==old.version()+1&&now.history().size()==old.history().size()+1&&now.code().equals("two\nTHREE 😀\nfour\nfive\n")&&now.editor().equals(f.b.session),"Atomic turn result/FIFO incorrect");
                var r=now.history().getLast();require(r.getEditorId().equals(f.s.gateway().identity(f.a.session).userId())&&r.getEditorName().equals("Học sinh 1")&&!r.getCreatedAt().isBefore(before)&&r.getPreviousVersion()==old.version()&&r.getSourceCode().equals(now.code()),"Revision metadata");
                require(r.getChanges().contains("Dòng 1:")&&r.getChanges().contains("one")&&r.getChanges().contains("Dòng 3:")&&r.getChanges().contains("THREE 😀")&&r.getChanges().contains("Dòng 5:")&&r.getChanges().contains("Giữ nguyên"),"Selected history details");f.b.cmd("/release "+now.token(),"editor=không có");
            });
            test("E13 E15 F04","Range shrink/expand preserves the next original line and history uses original range numbers",()->{
                for(String replace:List.of("short","a\r\nb\nc\nd")){f.seed(BASE);long t=f.request(f.a);f.select(f.a,t,"2-3");f.a.cmd("/setrange "+t+" "+replace,"[Bản nháp]");f.a.cmd("/finish "+t,"[COMMITTED]");var now=f.snap();require(now.code().equals("one\n"+replace+"\nfour\nfive\n"),"Range boundary joined");require(now.history().getLast().getChanges().contains("Đoạn dòng 2-3:")&&now.history().getLast().getChanges().contains("two\nthree"),"Wrong history range");}
            });
            test("E14 E22","Delete range is committed; keeping or restoring identical content ends turn without a revision",()->{
                f.seed(BASE);long t=f.request(f.a);f.select(f.a,t,"2-3");f.a.cmd("/deleterange "+t,"[Bản nháp]");f.a.cmd("/finish "+t,"[COMMITTED]");require(f.snap().code().equals("one\nfour\nfive\n"),"Delete range");
                for(String selection:List.of("1-2","1")){t=f.request(f.a);f.select(f.a,t,selection);var old=f.snap();if(selection.contains("-"))f.a.cmd("/keeprange "+t,"[Bản nháp]");else f.a.cmd("/setline "+t+" 1 one","[Bản nháp]");f.b.cmd("/request","editor=");f.a.cmd("/finish "+t,"[NO_CHANGE]");f.sameData(old);require(f.snap().editor().equals(f.b.session),"No-op failed to transfer");f.b.cmd("/release "+f.snap().token(),"editor=không có");}
            });
            test("E18","Oversized single edit/combined draft/range/load rejected without losing valid preview",()->{
                f.seed(BASE);long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 valid","[Bản nháp]");String valid=f.a.cmd("/draft "+t,"XEM TRƯỚC CODE");f.a.cmd("/setline "+t+" 1 "+"x".repeat(32000),"vượt 32000");require(f.a.cmd("/draft "+t,"XEM TRƯỚC CODE").equals(valid),"Combined limit corrupted draft");f.a.cmd("/cancel "+t,"[CANCELLED]");
                t=f.request(f.a);f.select(f.a,t,"1-2");f.a.cmd("/setrange "+t+" valid","[Bản nháp]");valid=f.a.cmd("/draft "+t,"XEM TRƯỚC CODE");f.a.cmd("/setrange "+t+" "+"x".repeat(32001),"vượt 32000");require(f.a.cmd("/draft "+t,"XEM TRƯỚC CODE").equals(valid),"Oversize range corrupted draft");f.a.cmd("/cancel "+t,"[CANCELLED]");
                t=f.request(f.owner);var old=f.snap();f.owner.cmd("/update "+old.version()+" "+t+" "+"x".repeat(32001),"tối đa 32000");f.sameData(old);f.owner.cmd("/release "+t,"editor=không có");
            });
            test("E19 E20","Missing drafts have clear responses; stale tokens/versions cannot mutate an active draft",()->{
                long t=f.request(f.a);f.a.cmd("/finish "+t,"Chưa có bản nháp");f.a.cmd("/cancel "+t,"[CANCELLED]");t=f.request(f.a);f.a.cmd("/select "+(f.snap().version()-1)+" "+t+" 1","version cũ");f.select(f.a,t,"1");var old=f.snap();
                for(String c:List.of("/draft "+(t+99),"/setline "+(t+99)+" 1 stale","/finish "+(t+99),"/cancel "+(t+99)))f.a.cmd(c,"Từ chối");f.sameData(old);require(field(f.room(),"draft")!=null,"Stale operation removed draft");f.a.cmd("/cancel "+t,"[CANCELLED]");
            });
            test("E23 F06 F07 F08","Cancel preserves code/history and transfers queue; history reads are read-only and bad versions rejected",()->{
                long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 cancelled","[Bản nháp]");f.b.cmd("/request","editor=");var old=f.snap();f.a.cmd("/cancel "+t,"[CANCELLED]");f.sameData(old);require(f.snap().editor().equals(f.b.session),"Cancel FIFO");var before=f.snap();f.a.cmd("/history 0","CODE SAU KHI SỬA");require(before.equals(f.snap()),"History changed state");f.a.cmd("/history -1","số nguyên không âm");f.a.cmd("/history abc","số nguyên không âm");f.a.cmd("/history 999999","Không tìm thấy phiên bản");f.b.cmd("/release "+f.snap().token(),"editor=không có");
            });
            test("F01","Initial revision has version zero and explicit initialization label",()->{var r=f.snap().history().getFirst();require(r.getVersion()==0&&r.getEditorId().equals("SYSTEM")&&r.detail().contains("Code khởi tạo"),"Initial revision identity");});
            test("F16 F17 H06","Injected SQLite revision error rolls back code/version/history, retains draft/rights, retry commits exactly once",()->{
                long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 retry-after-failure","[Bản nháp]");f.b.cmd("/request","editor=");var old=f.snap();
                try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+f.db);Statement sql=db.createStatement()){
                    sql.execute("CREATE TRIGGER audit_fail BEFORE INSERT ON revisions BEGIN SELECT RAISE(ABORT,'audit failure'); END");f.a.cmd("/finish "+t,"[SAVE_FAILED]");require(old.equals(f.snap()),"RAM/editor/queue changed after failed save");
                    try(ResultSet r=sql.executeQuery("SELECT code,version,(SELECT count(*) FROM revisions WHERE room_id="+f.id+") FROM rooms WHERE room_id="+f.id)){r.next();require(r.getString(1).equals(old.code())&&r.getLong(2)==old.version()&&r.getInt(3)==old.history().size(),"SQLite partially committed");}
                    require(f.a.cmd("/draft "+t,"XEM TRƯỚC CODE").contains("retry-after-failure"),"Draft lost");sql.execute("DROP TRIGGER audit_fail");
                }
                f.a.cmd("/finish "+t,"[COMMITTED]");f.a.cmd("/finish "+t,"Từ chối");require(f.snap().version()==old.version()+1&&f.snap().history().size()==old.history().size()+1,"Duplicate retry/history");f.b.cmd("/release "+f.snap().token(),"editor=không có");
            });
        }
    }
    static void furtherTests() throws Exception { /* Added in the next audit batch. */ }
    public static void main(String[] args)throws Exception {
        Files.createDirectories(ROOT);
        try {
            test("AUTH_GROUP","Authentication and room scenario setup",FullAudit::authAndRooms);
            test("QUEUE_GROUP","Queue scenario setup",FullAudit::queueTests);
            test("DRAFT_GROUP","Draft scenario setup",FullAudit::draftTests);
            furtherTests();
        } finally {HB.shutdownNow();HB.awaitTermination(3,TimeUnit.SECONDS);}
        System.out.println("METRIC peak_observed_connections="+PEAK+" elapsed_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-START)+" transport=127.0.0.1");
        System.out.println("FULL_AUDIT_COMPLETED");
    }
}
