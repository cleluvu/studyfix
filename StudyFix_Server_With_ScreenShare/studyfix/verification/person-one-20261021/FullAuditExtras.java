package studyfix;

import static studyfix.FullAudit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Second audit batch: protocol, failures, reconnect-free lifecycles, races and integrations. */
public final class FullAuditExtras {
    static void persistence()throws Exception {
        Path path;String id,second;Room.Snapshot old;int oldPort;
        Fixture fixture=new Fixture();path=fixture.db;id=fixture.id;second=fixture.second;oldPort=fixture.s.port();
        try(Fixture f=fixture){f.seed(BASE);long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 abandoned-draft","[Bản nháp]");f.b.cmd("/request","editor=");old=f.snap();}
        try(Server restarted=new Server(0,path)) {
            restarted.start();
            test("F12 F13","Restart reloads durable data only; old sockets/editor/queue/token/draft absent",()->{
                var now=restarted.rooms.get(id).snapshot();require(now.code().equals(old.code())&&now.version()==old.version()&&now.history().size()==old.history().size(),"Persistent data mismatch");
                require(now.members().isEmpty()&&now.queue().isEmpty()&&now.editor()==null&&now.token()==0&&field(restarted.rooms.get(id),"draft")==null,"Online state restored");
                try(ServerSocket probe=new ServerSocket(oldPort)){require(probe.isBound(),"Old listener retained");}
            });
            try(Peer owner=new Peer(restarted,"teacher","Teacher123!")) {
                test("F14","SQLite room IDs remain unique after server restart",()->{String r=owner.cmd("/create","Đã tạo và tham gia phòng:");String next=r.substring(r.lastIndexOf(':')+1).trim();require(!next.equals(id)&&!next.equals(second)&&restarted.rooms.size()==3,"Reused room ID");});
            }
        }
    }
    static void lineEndings()throws Exception {
        try(Fixture f=new Fixture()) {
            test("F18","Actual TCP/SQLite turns preserve LF/CRLF/CR/EOF, Unicode and indentation",()->{
                for(String code:List.of("one\ntwo\n","one\r\ntwo\r\n","one\rtwo\r","one\r\ntwo","one\n😀 Việt")){
                    f.seed(code);long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1     mới 😀","[Bản nháp]");
                    String expected="    mới 😀"+code.substring(3);f.a.cmd("/finish "+t,"[COMMITTED]");require(f.snap().code().equals(expected),"Ending or Unicode changed");
                    var stored=f.s.store().loadRooms().stream().filter(r->r.id().equals(f.id)).findFirst().orElseThrow();require(stored.code().equals(expected)&&stored.history().getLast().getSourceCode().equals(expected),"SQLite changed code bytes");
                }
            });
        }
    }
    static void heartbeat()throws Exception {
        try(Fixture f=new Fixture();Peer queued=new Peer(f.s,"student1","Student123!")){
            f.seed(BASE);long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 timeout-draft","[Bản nháp]");
            queued.cmd("/join "+f.id,"--- CODE CHUNG ---");queued.cmd("/request","editor=");f.b.cmd("/request","editor=");
            Map<String,AtomicInteger> left=new ConcurrentHashMap<>();f.s.gateway().addDepartureListener((session,identity,room,reason)->left.computeIfAbsent(session,x->new AtomicInteger()).incrementAndGet());
            var before=f.snap();long last=(Long)field(f.s.clients.get(f.a.session),"lastHeartbeat");long start=System.nanoTime();f.a.heartbeat.cancel(false);queued.heartbeat.cancel(false);
            test("G02 G03 G04 G06 G08 G09","Real 20s timeout: pending draft discarded, queued dead peer removed, healthy idle clients survive and continue",()->{
                until(()->!f.s.clients.containsKey(f.a.session)&&!f.s.clients.containsKey(queued.session)&&f.b.session.equals(f.snap().editor()),26000);
                long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-last);
                require(elapsed>=19500&&elapsed<26000,"Timeout outside configured bound");f.sameData(before);require(field(f.room(),"draft")==null,"Timed-out draft retained");
                require(f.s.clients.containsKey(f.b.session)&&left.get(f.a.session).get()==1&&left.get(queued.session).get()==1,"Duplicate cleanup or healthy idle session lost");
                until(()->f.s.monitor.getQueue().size()==f.s.clients.size(),3000);f.b.cmd("/chat survived-timeout 😀","survived-timeout 😀");
                long bt=f.snap().token();f.select(f.b,bt,"1");f.b.cmd("/setline "+bt+" 1 survivor-edit","[Bản nháp]");f.b.cmd("/finish "+bt,"[COMMITTED]");
                require(f.snap().version()==before.version()+1,"Survivor cannot edit");
                System.out.println("METRIC heartbeat_since_last_ping_ms="+elapsed+" wait_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
            });
        }
        try(Fixture f=new Fixture()){
            test("G05","Abrupt RST with draft discards uncommitted work and transfers rights",()->{long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 rst-draft","[Bản nháp]");f.b.cmd("/request","editor=");var old=f.snap();f.a.socket.setSoLinger(true,0);f.a.close();until(()->!f.s.clients.containsKey(f.a.session)&&f.b.session.equals(f.snap().editor()),4000);f.sameData(old);require(field(f.room(),"draft")==null,"Draft survived reset");});
        }
    }
    static byte[] frame(String message)throws IOException {var bytes=new ByteArrayOutputStream();Protocol.send(new DataOutputStream(bytes),message);return bytes.toByteArray();}
    static void framing()throws Exception {
        try(Fixture f=new Fixture()){
            test("H01","Fragmented header/payload writes and coalesced frames parsed in order",()->{
                try(Socket socket=new Socket("127.0.0.1",f.s.port())){socket.setTcpNoDelay(true);socket.setSoTimeout(5000);OutputStream out=socket.getOutputStream();DataInputStream in=new DataInputStream(socket.getInputStream());
                    byte[] bytes=frame("/login student1 Student123!");for(byte b:bytes){out.write(b);out.flush();}require(Protocol.receive(in).startsWith("[LOGIN_OK]"),"Fragmented login failed");
                    var combined=new ByteArrayOutputStream();combined.write(frame("/ping"));combined.write(frame("/ping"));out.write(combined.toByteArray());out.flush();require(Protocol.receive(in).equals("/pong")&&Protocol.receive(in).equals("/pong"),"Combined frames reordered");
                }
            });
            test("H02","Negative/oversized lengths, short payload, and socket EOF inside frame close only that session",()->{
                until(()->f.s.clients.size()==5,3000);
                for(int length:new int[]{-1,Protocol.MAX_BYTES+1,12}){
                    try(Socket socket=new Socket("127.0.0.1",f.s.port())){socket.setSoTimeout(5000);var out=new DataOutputStream(socket.getOutputStream());out.writeInt(length);if(length==12){out.write(new byte[]{1,2});socket.shutdownOutput();}out.flush();try{require(socket.getInputStream().read()==-1,"Bad frame not closed");}catch(SocketException expected){}}
                }
                try(Socket socket=new Socket("127.0.0.1",f.s.port())){new DataOutputStream(socket.getOutputStream()).writeInt(100);socket.getOutputStream().write(1);}
                until(()->f.s.clients.size()==5,4000);f.owner.barrier();require(f.s.isRunning(),"Malformed client stopped server");
            });
            test("H03","Slow socket fills bounded queue; server logs disconnection and other clients keep progressing",()->{
                try(Socket slow=new Socket()){
                    slow.setReceiveBufferSize(1024);slow.connect(new InetSocketAddress("127.0.0.1",f.s.port()));slow.setSoTimeout(5000);var in=new DataInputStream(slow.getInputStream());var out=new DataOutputStream(slow.getOutputStream());
                    Protocol.send(out,"/login student1 Student123!");String reply=Protocol.receive(in);String id=reply.split("session=")[1].split(" ")[0];Protocol.send(out,"/join "+f.id);while(!Protocol.receive(in).contains("--- CODE CHUNG ---")){}
                    int sent=0;long start=System.nanoTime();for(int i=0;i<300;i++){try{f.s.gateway().sendToUser(f.a.session,id,f.id,"Q".repeat(60000));sent++;}catch(IOException|SecurityException e){break;}}
                    until(()->!f.s.clients.containsKey(id),4000);f.a.cmd("/chat after-slow-reader","after-slow-reader");require(f.s.snapshot().log().stream().anyMatch(x->x.contains("đầy hàng đợi gửi")&&x.contains(id)),"Overflow not logged");
                    System.out.println("METRIC slow_reader_accepted_responses="+sent+" each_utf8_bytes=60000 elapsed_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
                }
            });
        }
    }
    static void concurrency()throws Exception {
        try(Fixture f=new Fixture()){
            test("H04","Eight simultaneously released requesters produce one editor and seven unique queue entries",()->{
                List<Peer> peers=new ArrayList<>();ExecutorService pool=Executors.newFixedThreadPool(8);
                try{
                    for(int i=0;i<8;i++){Peer p=new Peer(f.s,"student1","Student123!");peers.add(p);p.cmd("/join "+f.id,"--- CODE CHUNG ---");}
                    CyclicBarrier barrier=new CyclicBarrier(8);List<Future<?>> futures=new ArrayList<>();
                    for(Peer p:peers)futures.add(pool.submit(()->{try{barrier.await(5,TimeUnit.SECONDS);p.send("/request");}catch(Exception e){throw new RuntimeException(e);}}));
                    for(Future<?> future:futures)future.get(6,TimeUnit.SECONDS);until(()->f.snap().queue().size()==7,4000);
                    Set<String> actual=new HashSet<>(f.snap().queue());actual.add(f.snap().editor());require(actual.size()==8&&peers.stream().allMatch(p->actual.contains(p.session)),"Duplicate/missing requester");
                }finally{for(Peer p:peers)p.close();pool.shutdownNow();pool.awaitTermination(3,TimeUnit.SECONDS);}
                until(()->f.snap().editor()==null&&f.snap().queue().isEmpty(),4000);
            });
            test("H05","Finish/leave race over one framed socket, 8 iterations; final data is one legal atomic outcome",()->{
                ExecutorService pool=Executors.newFixedThreadPool(2);
                try{for(int i=0;i<8;i++){
                    f.seed(BASE);long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 race-result","[Bản nháp]");var old=f.snap();CyclicBarrier start=new CyclicBarrier(2);
                    Future<?> finish=pool.submit(()->{try{start.await();f.a.send("/finish "+t);}catch(Exception e){throw new RuntimeException(e);}});
                    Future<?> leave=pool.submit(()->{try{start.await();f.a.send("/leave");}catch(Exception e){throw new RuntimeException(e);}});
                    finish.get(5,TimeUnit.SECONDS);leave.get(5,TimeUnit.SECONDS);f.a.barrier();until(()->!f.room().isMember(f.a.session),3000);var now=f.snap();
                    require((now.version()==old.version()&&now.code().equals(BASE))||(now.version()==old.version()+1&&now.code().startsWith("race-result\n")),"Non-atomic race outcome");
                    var disk=f.s.store().loadRooms().stream().filter(r->r.id().equals(f.id)).findFirst().orElseThrow();require(disk.version()==now.version()&&disk.code().equals(now.code())&&disk.history().size()==now.history().size(),"Race RAM/SQLite mismatch");require(now.editor()==null&&field(f.room(),"draft")==null,"Orphan turn");f.a.cmd("/join "+f.id,"--- CODE CHUNG ---");
                }}finally{pool.shutdownNow();pool.awaitTermination(3,TimeUnit.SECONDS);}
            });
            test("H07","Concurrent holder finish and non-holder select accept only the authorized turn",()->{
                long t=f.request(f.a);f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 authorized","[Bản nháp]");long v=f.snap().version();int from=f.b.events.size();
                ExecutorService pool=Executors.newFixedThreadPool(2);
                try{var x=pool.submit(()->{try{f.a.send("/finish "+t);}catch(Exception e){throw new RuntimeException(e);}});var y=pool.submit(()->{try{f.b.send("/select "+v+" "+t+" 1");}catch(Exception e){throw new RuntimeException(e);}});x.get(5,TimeUnit.SECONDS);y.get(5,TimeUnit.SECONDS);f.b.await(from,"Từ chối");until(()->f.snap().version()==v+1,3000);require(f.snap().history().size()==v+2,"Duplicate concurrent revision");}finally{pool.shutdownNow();pool.awaitTermination(3,TimeUnit.SECONDS);}
            });
            test("H08","Two rooms each complete 20 turns concurrently without deadlock or cross-room content",()->{
                ExecutorService pool=Executors.newFixedThreadPool(2);long beforeA=f.snap().version(),beforeB=f.s.rooms.get(f.second).snapshot().version();
                try{
                    Future<?> one=pool.submit(()->roomTurns(f.owner,f.s.rooms.get(f.id),"ROOM_A",20));Future<?> two=pool.submit(()->roomTurns(f.other,f.s.rooms.get(f.second),"ROOM_B",20));one.get(20000,TimeUnit.MILLISECONDS);two.get(20000,TimeUnit.MILLISECONDS);
                    require(f.snap().version()==beforeA+20&&f.s.rooms.get(f.second).snapshot().version()==beforeB+20&&f.snap().code().startsWith("ROOM_A19")&&f.s.rooms.get(f.second).snapshot().code().startsWith("ROOM_B19"),"Concurrent room version/code mismatch");
                }finally{pool.shutdownNow();pool.awaitTermination(3,TimeUnit.SECONDS);}
                System.out.println("METRIC concurrent_rooms=2 turns_per_room=20 committed_turns=40");
            });
        }
    }
    static void roomTurns(Peer peer,Room room,String prefix,int count){try{for(int i=0;i<count;i++){peer.cmd("/request","editor=");var old=room.snapshot();require(old.editor().equals(peer.session),"Wrong owner");peer.cmd("/select "+old.version()+" "+old.token()+" 1","[Bản nháp]");peer.cmd("/setline "+old.token()+" 1 "+prefix+i,"[Bản nháp]");peer.cmd("/finish "+old.token(),"[COMMITTED]");}}catch(Exception e){throw new RuntimeException(e);}}
    static void integrations()throws Exception {
        try(Fixture f=new Fixture()){
            test("I03 I06","Immutable snapshots; authenticated gateway identity/membership/owner/edit rights/events/departure callbacks",()->{
                var gateway=f.s.gateway();require(gateway.isMember(f.a.session,f.id)&&!gateway.isMember(f.other.session,f.id)&&gateway.isTeacher(f.owner.session,f.id)&&!gateway.isTeacher(f.a.session,f.id),"Gateway authorization");
                var old=gateway.codeSnapshot(f.a.session,f.id);for(List<?> list:List.of(old.members(),old.queue(),old.history())){try{list.clear();throw new AssertionError("Mutable snapshot collection");}catch(UnsupportedOperationException expected){}}
                int from=f.a.events.size();gateway.sendToUser(f.owner.session,f.a.session,f.id,"DIRECT_EVENT");f.a.await(from,"DIRECT_EVENT");from=f.b.events.size();gateway.sendToRoom(f.a.session,f.id,"ROOM_EVENT");f.b.await(from,"ROOM_EVENT");
                try{gateway.codeSnapshot(f.other.session,f.id);throw new AssertionError("Outside snapshot allowed");}catch(SecurityException expected){}
                try{gateway.sendToUser(f.a.session,f.other.session,f.id,"FORBIDDEN_EVENT");throw new AssertionError("Cross-room send allowed");}catch(SecurityException expected){}
                try{gateway.sendToRoom(f.other.session,f.id,"FORBIDDEN_EVENT");throw new AssertionError("Outside actor allowed");}catch(SecurityException expected){}
                long t=f.request(f.a);require(gateway.canEdit(f.a.session,f.id,t)&&!gateway.canEdit(f.b.session,f.id,t),"Edit permission mismatch");f.select(f.a,t,"1");f.a.cmd("/setline "+t+" 1 immutable-old-snapshot","[Bản nháp]");f.a.cmd("/finish "+t,"[COMMITTED]");require(old.version()+1==f.snap().version()&&!old.code().equals(f.snap().code()),"Snapshot mutated in place");
                Map<String,AtomicInteger> leaves=new ConcurrentHashMap<>();StudyFixGateway.DepartureListener listener=(session,identity,room,reason)->{require(identity.userId()!=null,"Callback identity");leaves.computeIfAbsent(session,x->new AtomicInteger()).incrementAndGet();};gateway.addDepartureListener(listener);
                f.a.cmd("/leave","Đã rời phòng");f.b.close();until(()->leaves.containsKey(f.a.session)&&leaves.containsKey(f.b.session),3000);require(leaves.get(f.a.session).get()==1&&leaves.get(f.b.session).get()==1,"Duplicate callback");gateway.removeDepartureListener(listener);
            });
            test("A05","Second server on same port fails clearly, first server remains responsive",()->{Path other=Files.createTempDirectory(ROOT,"port-conflict-").resolve("other.db");try(Server ignored=new Server(f.s.port(),other)){throw new AssertionError("Second listener succeeded");}catch(BindException expected){}f.owner.barrier();require(f.s.isRunning(),"First server affected");});
            test("A06 I05","Stop cleans listener/client/monitor/workers/SQLite; same port starts a new server",()->{
                int port=f.s.port();f.s.close();require(f.s.clients.isEmpty()&&f.s.monitor.isShutdown(),"Resources retained");ExecutorService workers=(ExecutorService)field(f.s,"workers");require(workers.isTerminated(),"Worker executor running");require(((Connection)field(f.s.store(),"db")).isClosed(),"Database connection retained");
                try(Server again=new Server(port,Files.createTempDirectory(ROOT,"same-port-").resolve("restart.db"))){again.start();try(Peer p=new Peer(again,"teacher","Teacher123!")){p.barrier();}}
            });
        }
    }
    static void consoleFiles()throws Exception {
        test("E17","Actual Java console rejects missing/oversized/invalid rangefile and load paths without sending updates",()->{
            Path dir=Files.createTempDirectory(ROOT,"console-paths-");Path large=dir.resolve("too large.java");Files.writeString(large,"x".repeat(32001));Path log=dir.resolve("console.log");
            try(ServerSocket fake=new ServerSocket(0)){
                Process p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstudyfix.port="+fake.getLocalPort(),"-cp",System.getProperty("java.class.path"),"studyfix.Client").redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try{
                    var keys=new OutputStreamWriter(p.getOutputStream(),StandardCharsets.UTF_8);keys.write("127.0.0.1\nstudent1\nStudent123!\n");keys.flush();fake.setSoTimeout(5000);
                    try(Socket socket=fake.accept()){socket.setSoTimeout(5000);var in=new DataInputStream(socket.getInputStream());var out=new DataOutputStream(socket.getOutputStream());require(Protocol.receive(in).startsWith("/login "),"Console did not login");Protocol.send(out,"[LOGIN_OK] role=STUDENT");
                        for(String path:List.of(dir.resolve("missing.java").toString(),large.toString(),"bad<path")){keys.write("/rangefile 1 "+path+"\n/load 0 1 "+path+"\n");}keys.write("/chat AUDIT_FILE_ERRORS_DONE\n");keys.flush();
                        for(;;){String c=Protocol.receive(in);if(c.equals("/ping")){Protocol.send(out,"/pong");continue;}require(!c.startsWith("/setrange ")&&!c.startsWith("/update "),"Bad file sent as update");if(c.equals("/chat AUDIT_FILE_ERRORS_DONE"))break;}
                        keys.write("/quit\n");keys.flush();require(p.waitFor(5,TimeUnit.SECONDS),"Console did not stop");String output=Files.readString(log);require(output.contains("Không đọc được đoạn code")&&output.contains("Không đọc được file")&&output.contains("32000"),"Local error not reported");
                    }
                }finally{if(p.isAlive())p.destroyForcibly().waitFor();}
            }
        });
    }
    public static void main(String[] args)throws Exception {
        Path results=ROOT.resolve("results.tsv");if(Files.exists(results))for(String line:Files.readAllLines(results)){String[] fields=line.split("\t",3);if(fields.length==3)RESULTS.put(fields[0],new String[]{fields[1],fields[2]});}
        try {
            test("PERSISTENCE_GROUP","Persistence batch setup",FullAuditExtras::persistence);
            test("ENDINGS_GROUP","Line ending batch setup",FullAuditExtras::lineEndings);
            test("FRAMES_GROUP","Frame batch setup",FullAuditExtras::framing);
            test("RACES_GROUP","Concurrent request batch setup",FullAuditExtras::concurrency);
            test("GATEWAY_GROUP","Gateway/stop batch setup",FullAuditExtras::integrations);
            consoleFiles();
            test("HEARTBEAT_GROUP","Heartbeat batch setup",FullAuditExtras::heartbeat);
        }finally{HB.shutdownNow();HB.awaitTermination(3,TimeUnit.SECONDS);}
        System.out.println("METRIC peak_observed_connections="+PEAK+" elapsed_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-START)+" transport=127.0.0.1");
        System.out.println("FULL_AUDIT_EXTRAS_COMPLETED");
    }
}
