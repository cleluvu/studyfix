package studyfix;

import studyfix.assignment.grading.GradingSandbox;
import studyfix.assignment.model.GradingResult;
import studyfix.assignment.protocol.AssignmentJson;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP/TCP/SQLite tests. The injected grader below is explicitly a test double. */
public final class ClassroomIntegrationTest {
    static int checks;
    static final HttpClient HTTP=HttpClient.newHttpClient();
    record Response(int status,Map<String,Object> body) { }
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") static Map<String,Object> obj(Object x){return x instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Object x){return x instanceof List<?> l?(List<Map<String,Object>>)l:List.of();}
    static Response call(String base,String path,String token,Map<String,Object> body)throws Exception{
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(stringify(body),StandardCharsets.UTF_8));
        HttpResponse<String> r=HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(r.statusCode(),object(r.body()));
    }
    static String login(String base,String user,String password)throws Exception{
        Response r=call(base,"/api/auth/login",null,mapOf("username",user,"password",password));
        check(r.status==200,"login "+user);return text(r.body,"token","");
    }
    static Map<String,Object> view(String base,String token)throws Exception{
        Response r=call(base,"/api/classroom",token,null);check(r.status==200,"classroom read");return obj(r.body.get("classroom"));
    }
    static Response action(String base,String token,String op,Map<String,Object> data)throws Exception{
        Map<String,Object> state=view(base,token),body=new LinkedHashMap<>(data);
        body.put("action",op);body.putIfAbsent("expectedPhase",state.get("phase"));body.putIfAbsent("round",state.get("round"));body.putIfAbsent("expectedVersion",state.get("masterVersion"));
        return call(base,"/api/classroom",token,body);
    }
    static Map<String,Object> ok(String base,String token,String op,Map<String,Object> data)throws Exception{
        Response r=action(base,token,op,data);check(r.status==200,op+" succeeds: "+r.body);return obj(r.body.get("classroom"));
    }
    static void join(String base,String token,String room)throws Exception{
        call(base,"/api/rooms/current/command",token,mapOf("action","JOIN_ROOM","roomId",room));
        waitFor(()->call(base,"/api/rooms/current",token,null).status==200,"join room");
    }
    static void waitFor(Callable<Boolean> test,String message)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end){if(test.call())return;Thread.sleep(40);}throw new AssertionError(message);
    }
    static String room(String base,String token)throws Exception{
        return text(obj(call(base,"/api/rooms/current",token,null).body.get("room")),"roomId","");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");
        System.setProperty("studyfix.web.root",Path.of("../web-client/static").toAbsolutePath().toString());
        Path temp=Files.createTempDirectory(Path.of("build"),"classroom-test-");Path db=temp.resolve("core.db"),learning=temp.resolve("learning.db");
        CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
        List<String> snapshots=new CopyOnWriteArrayList<>();
        GradingSandbox testDouble=(id,code,tests,max)->{
            snapshots.add(code);started.countDown();if(!finish.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test latch timed out");
            return new GradingResult("WRONG_ANSWER",0,max,0,tests.size(),"TEST DOUBLE result",2,List.of());
        };
        String roomId,studentId,otherId,checkId,finalMaster;
        int round;
        try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,32,1,"unused-test-image");WebGatewayServer web=new WebGatewayServer(core,assignments,testDouble)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            check(call(base,"/api/classroom",null,null).status==401,"anonymous rejected");
            String teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!"),other=login(base,"student2","Student234!"),foreign=login(base,"teacher2","Teacher234!");
            call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM"));
            waitFor(()->call(base,"/api/rooms/current",teacher,null).status==200,"create room");roomId=room(base,teacher);
            join(base,student,roomId);join(base,other,roomId);
            call(base,"/api/rooms/current/command",student,mapOf("action","CHAT","text","Tin nhắn phiên học được lưu bền vững"));
            waitFor(()->rows(view(base,teacher).get("chat")).size()==1,"chat saved before broadcast");
            check(call(base,"/api/classroom",foreign,null).status==409,"nonmember rejected");
            check(action(base,student,"START",Map.of()).status==403,"student cannot start");
            ok(base,teacher,"START",Map.of());
            Map<String,Object> chosen=ok(base,teacher,"SELECT_EXERCISE",mapOf("exerciseId","DBG_TEXT001"));
            check(!stringify(chosen).contains("referenceSolution")&&!stringify(chosen).contains("expectedOutput"),"no private exercise fields");
            check(action(base,teacher,"MERGE",Map.of()).status==409,"invalid transition rejected");
            Map<String,Object> fork=ok(base,teacher,"FORK",Map.of());round=(int)number(fork,"round",0);
            check(rows(fork.get("branches")).size()==2,"two independent branches");
            Map<String,Object> a=rows(view(base,student).get("branches")).getFirst(),b=rows(view(base,other).get("branches")).getFirst();
            studentId=text(a,"userId","");otherId=text(b,"userId","");String starter=text(b,"code","");
            check(rows(view(base,student).get("branches")).size()==1,"student sees own branch only");
            check(call(base,"/api/classroom/branches/"+otherId,student,null).status==403,"branch IDOR rejected");
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());PreparedStatement p=c.prepareStatement("INSERT INTO users VALUES(?,?,?,?,?)")) {
                p.setString(1,UUID.randomUUID().toString());p.setString(2,"student3");p.setString(3,Passwords.hash("Student345!".toCharArray()));p.setString(4,"Late student");p.setString(5,"STUDENT");p.executeUpdate();
            }
            String late=login(base,"student3","Student345!");join(base,late,roomId);
            check(starter.equals(rows(view(base,late).get("branches")).getFirst().get("code")),"late student gets own branch from current Master");
            check(rows(view(base,teacher).get("branches")).size()==3,"late enrollment included in teacher grid");
            check(action(base,teacher,"MASTER_SAVE",mapOf("code","tamper")).status==409,"Master frozen during branching");
            // A raw TCP /request must not bypass the new browser permissions.
            call(base,"/api/rooms/current/command",student,mapOf("action","REQUEST_EDIT"));Thread.sleep(100);
            check(!core.rooms.get(roomId).hasActiveEditTurn(),"legacy TCP student edit rejected after activation");
            String codeA="public class Main { public static void main(String[] x){System.out.println(1);} } // student A";
            ok(base,student,"BRANCH_SAVE",mapOf("expectedVersion",0,"code",codeA,"userId",otherId));
            check(rows(view(base,teacher).get("branches")).stream().anyMatch(x->text(x,"codePreview","").contains("// student A")),"teacher grid receives real bounded code preview");
            check(text(rows(view(base,other).get("branches")).getFirst(),"code","").equals(starter),"student A cannot alter B even with forged userId");
            check(action(base,student,"BRANCH_SAVE",mapOf("expectedVersion",0,"code","stale")).status==409,"stale branch version rejected");
            check(action(base,student,"BRANCH_SAVE",mapOf("expectedVersion",1,"code","x".repeat(32001))).status==400,"source size limited");
            ok(base,student,"BRANCH_CHECK",mapOf("expectedVersion",1));check(started.await(3,TimeUnit.SECONDS),"worker starts");
            check(action(base,student,"BRANCH_CHECK",mapOf("expectedVersion",1)).status==409,"judge rate/pending limit");
            finalMaster=codeA+"\n// edited after submitting";
            ok(base,student,"BRANCH_SAVE",mapOf("expectedVersion",1,"code",finalMaster));finish.countDown();
            waitFor(()->"DONE".equals(obj(rows(view(base,student).get("branches")).getFirst().get("latestCheck")).get("status")),"test-double check completed");
            Map<String,Object> checked=rows(view(base,student).get("branches")).getFirst();
            check(Boolean.TRUE.equals(checked.get("resultStale")),"old result marked stale");check(snapshots.equals(List.of(codeA)),"grader received immutable snapshot");
            checkId=text(obj(checked.get("latestCheck")),"id","");
            check(call(base,"/api/classroom/checks/"+checkId,other,null).status==403,"check snapshot IDOR rejected");
            check(codeA.equals(obj(call(base,"/api/classroom/checks/"+checkId,student,null).body.get("check")).get("code")),"old check still shows submitted code");
            ok(base,teacher,"FREEZE",Map.of());
            check(action(base,student,"BRANCH_SAVE",mapOf("expectedVersion",2,"code","late")).status==409,"frozen branch rejects writes");
            check(action(base,student,"MERGE",mapOf("userId",studentId,"branchVersion",2)).status==403,"student cannot merge");
            check(action(base,teacher,"MERGE",mapOf("userId",studentId,"branchVersion",1)).status==409,"stale preview rejected");
            long masterBefore=core.rooms.get(roomId).snapshot().version();
            // Inject a database error after writing Master, proving rollback covers history + phase.
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());Statement s=c.createStatement()) {
                s.execute("CREATE TRIGGER reject_test_merge BEFORE INSERT ON classroom_events WHEN NEW.kind='MERGE' BEGIN SELECT RAISE(ABORT,'test merge failure'); END");
            }
            check(action(base,teacher,"MERGE",mapOf("userId",studentId,"branchVersion",2)).status==500,"failed transaction reported");
            check(core.rooms.get(roomId).snapshot().version()==masterBefore,"rollback leaves RAM Master unchanged");
            check("MERGING".equals(view(base,teacher).get("phase")),"rollback leaves phase unchanged");
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());Statement s=c.createStatement()) {
                try(ResultSet r=s.executeQuery("SELECT version FROM rooms WHERE room_id="+Long.parseLong(roomId))){check(r.next()&&r.getLong(1)==masterBefore,"rollback leaves DB Master unchanged");}
                s.execute("DROP TRIGGER reject_test_merge");
            }
            ok(base,teacher,"MERGE",mapOf("userId",studentId,"branchVersion",2));
            check(core.rooms.get(roomId).snapshot().code().equals(finalMaster),"selected branch becomes Master");
            check(core.rooms.get(roomId).snapshot().version()==masterBefore+1,"merge creates one revision");
            check(rows(view(base,teacher).get("events")).stream().anyMatch(e->"MERGE".equals(e.get("kind"))),"merge event persisted");
            ok(base,teacher,"FORK",Map.of());
            check(action(base,student,"BRANCH_SAVE",mapOf("round",round,"expectedVersion",0,"code","old round")).status==409,"old-round writes rejected");
            check(finalMaster.equals(rows(view(base,student).get("branches")).getFirst().get("code")),"next round starts from merged Master");
            check(codeA.equals(obj(call(base,"/api/classroom/checks/"+checkId,student,null).body.get("check")).get("code")),"snapshot accessible after next round");
            check(rows(obj(call(base,"/api/classroom/rounds/1",student,null).body.get("report")).get("branches")).size()==1,"historical reports preserve privacy");
            check(finalMaster.equals(obj(call(base,"/api/classroom/branches/"+studentId+"?round=1",teacher,null).body.get("branch")).get("code")),"previous round branch retained");
            // New login for the same student must recover the durable branch, not create a duplicate.
            String again=login(base,"student1","Student123!");join(base,again,roomId);
            check(rows(view(base,teacher).get("branches")).size()==3,"same-account reconnect does not duplicate branches");
            ok(base,again,"BRANCH_SAVE",mapOf("expectedVersion",0,"code",finalMaster+"\n// durable"));
            check(text(rows(view(base,student).get("branches")).getFirst(),"code","").endsWith("// durable"),"same account sees saved branch");
        } finally { finish.countDown(); }
        String previousDocker=System.getProperty("studyfix.assignment.docker");
        System.setProperty("studyfix.assignment.docker",temp.resolve("missing-docker-for-test.exe").toAbsolutePath().toString());
        try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,32,1,"unused-test-image");WebGatewayServer web=new WebGatewayServer(core,assignments)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            String teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");join(base,teacher,roomId);join(base,student,roomId);
            Map<String,Object> restored=view(base,student);
            check("BRANCHING".equals(restored.get("phase")),"phase restored after server restart");
            check(rows(restored.get("chat")).size()==1,"room chat survives restart");
            check(text(rows(restored.get("branches")).getFirst(),"code","").endsWith("// durable"),"branch restored after restart");
            check(core.rooms.get(roomId).snapshot().code().equals(finalMaster),"Master survives restart");
            ok(base,student,"BRANCH_CHECK",mapOf("expectedVersion",1));
            waitFor(()->"SANDBOX_UNAVAILABLE".equals(obj(obj(rows(view(base,student).get("branches")).getFirst().get("latestCheck")).get("result")).get("verdict")),"real Docker adapter reports unavailable instead of fake acceptance");
            check(true,"production sandbox failure is not ACCEPTED");
            call(base,"/api/rooms/current/command",student,mapOf("action","REQUEST_EDIT"));Thread.sleep(100);
            check(!core.rooms.get(roomId).hasActiveEditTurn(),"legacy edit protection survives restart");
            ok(base,teacher,"END",Map.of());
            check(action(base,student,"BRANCH_SAVE",mapOf("expectedVersion",1,"code","after end")).status==409,"ended session rejects edits");
            ok(base,teacher,"NEW_SESSION",Map.of());check("WAITING".equals(view(base,teacher).get("phase")),"next session starts waiting");
        } finally {if(previousDocker==null)System.clearProperty("studyfix.assignment.docker");else System.setProperty("studyfix.assignment.docker",previousDocker);}
        System.out.println("ClassroomIntegrationTest: "+checks+" checks PASS (HTTP/TCP/SQLite real; grading test double explicitly injected).");
        System.out.println("Temporary database: "+db.toAbsolutePath());
    }
}
