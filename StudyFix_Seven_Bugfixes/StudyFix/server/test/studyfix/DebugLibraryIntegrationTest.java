package studyfix;

import studyfix.assignment.grading.*;
import studyfix.assignment.model.*;
import studyfix.assignment.persistence.*;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

public final class DebugLibraryIntegrationTest {
    static Map<String,Object> saved(String base,String token,String id)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        while(System.nanoTime()<deadline){Response r=call(base,"/api/debug-library/"+id,token,null);check(r.status()==200,"read saved library item");Map<String,Object> item=obj(r.body().get("item"));if(!Set.of("QUEUED","RUNNING").contains(text(item,"status","")))return item;Thread.sleep(100);}
        throw new AssertionError("library validation timeout");
    }
    static Map<String,Object> save(String base,String token,String id,Map<String,Object> body)throws Exception {
        Response r=call(base,"/api/debug-library"+(id.isBlank()?"":"/"+id),token,body);check(r.status()==202,"save manual buggy code: "+r.body());return saved(base,token,text(obj(r.body().get("item")),"id",""));
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");System.setProperty("studyfix.web.root",Path.of("../web-client/static").toAbsolutePath().toString());
        Path root=Files.createTempDirectory(Path.of("build-debug-lessons"),"library-");Path db=root.resolve("core.db"),learning=root.resolve("learning.db");boolean docker=Arrays.asList(args).contains("--docker");
        AtomicInteger runs=new AtomicInteger();GradingSandbox real=docker?new DockerJavaSandbox("eclipse-temurin:21-jdk",2,45):(id,code,tests,max)->DebugLessonIntegrationTest.grade(root,id,code,tests,max);
        GradingSandbox sandbox=(id,code,tests,max)->{runs.incrementAndGet();return real.grade(id,code,tests,max);};
        String code=DebugLessonIntegrationTest.CODE,bug1=code.replace("n+1","n-1"),bug2=code.replace("n+1","n*2");String libraryId,roomId,snapshotId;
        try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments,sandbox)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            Identity owner=DebugLessonIntegrationTest.identity(db,"teacher");QuestionBankRepository bank=new QuestionBankRepository(new AssignmentRepository(learning));
            bank.save(owner,DebugLessonIntegrationTest.exercise("MANUAL_SOURCE",code),"PUBLISHED",false,0,"PRACTICE");
            bank.save(owner,DebugLessonIntegrationTest.exercise("MANUAL_METHOD",code),"PUBLISHED",false,0,"PRACTICE");
            bank.save(owner,DebugLessonIntegrationTest.exercise("MANUAL_CLASS",code),"PUBLISHED",false,0,"PRACTICE");
            try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+learning);java.sql.Statement s=c.createStatement()){
                s.executeUpdate("UPDATE p3_exercises SET java_structure='METHOD',entry_file='Solution.java' WHERE id='MANUAL_METHOD'");
                s.executeUpdate("UPDATE p3_exercises SET java_structure='CLASS',entry_file='Student.java' WHERE id='MANUAL_CLASS'");
            }
            String teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!"),foreign=login(base,"teacher2","Teacher234!");
            check(call(base,"/api/rooms/current",teacher,null).status()==409,"prepare before joining any room");
            check(call(base,"/api/debug-library",null,null).status()==401,"anonymous denied");
            check(call(base,"/api/debug-library",student,null).status()==403,"student cannot list teacher library");
            check(call(base,"/api/debug-library/source/MANUAL_SOURCE",student,null).status()==403,"student cannot obtain source solution");
            check(stringify(call(base,"/api/debug-library/sources",teacher,null).body()).contains("MANUAL_SOURCE"),"source picker works without room");
            check(code.equals(obj(call(base,"/api/debug-library/source/MANUAL_SOURCE",teacher,null).body().get("source")).get("referenceSolution")),"teacher reads correct code before editing");
            List<Map<String,Object>> sourceRows=rows(call(base,"/api/debug-library/sources",teacher,null).body().get("sources"));
            Map<String,Object> method=sourceRows.stream().filter(row->"MANUAL_METHOD".equals(row.get("id"))).findFirst().orElseThrow();
            Map<String,Object> classRow=sourceRows.stream().filter(row->"MANUAL_CLASS".equals(row.get("id"))).findFirst().orElseThrow();
            check(Boolean.FALSE.equals(method.get("debugEligible"))&&text(method,"debugReason","").contains("Method (Solution.java)"),"method with reference/tests reports unsupported type, not missing data");
            check(Boolean.FALSE.equals(classRow.get("debugEligible"))&&text(classRow,"debugReason","").contains("Class (Student.java)"),"class source reports actual unsupported entry");
            Response unsupported=call(base,"/api/debug-library/source/MANUAL_METHOD",teacher,null);check(unsupported.status()!=200&&stringify(unsupported.body()).contains("Method"),"picker and source API agree on the same reason");
            check(call(base,"/api/debug-library",teacher,mapOf("sourceId","MANUAL_SOURCE","title","Unedited","code",code)).status()!=202,"unchanged reference cannot be saved as buggy exercise");
            Map<String,Object> item=save(base,teacher,"",mapOf("sourceId","MANUAL_SOURCE","title","Bài lỗi tự soạn","code",bug1));libraryId=text(item,"id","");
            check("READY".equals(item.get("status"))&&bug1.equals(item.get("starterCode")),"manual faulty code is preserved and validated");
            check(!item.containsKey("tests"),"teacher response excludes raw hidden tests");
            String list=stringify(call(base,"/api/debug-library",teacher,null).body());check(list.contains(libraryId)&&!list.contains("referenceSolution")&&!list.contains("starterCode"),"durable library list contains metadata only");
            Map<String,Object> metadata=rows(call(base,"/api/debug-library",teacher,null).body().get("items")).getFirst();check("Conditions".equals(metadata.get("topic"))&&"Easy".equals(metadata.get("difficulty")),"library list exposes source classification for filters");
            check(rows(call(base,"/api/debug-library",foreign,null).body().get("items")).isEmpty(),"teacher libraries isolated");
            check(call(base,"/api/debug-library/"+libraryId,foreign,null).status()==403,"foreign teacher cannot read code");
            check(call(base,"/api/debug-library/"+libraryId,student,mapOf("action","DELETE","expectedVersion",1)).status()==403,"student cannot delete");
            call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM","roomCode","MANUAL_ROOM_1"));waitFor(()->call(base,"/api/rooms/current",teacher,null).status()==200,"first room");String firstRoom=room(base,teacher);ok(base,teacher,"START",Map.of());
            check(action(base,teacher,"SELECT_LIBRARY",mapOf("libraryId",libraryId,"libraryVersion",2)).status()!=200,"stale library version rejected");
            int before=runs.get();ok(base,teacher,"SELECT_LIBRARY",mapOf("libraryId",libraryId,"libraryVersion",1));check(runs.get()==before,"choosing saved code does not run validation again");
            Map<String,Object> changed=save(base,teacher,libraryId,mapOf("title","Bài lỗi phiên bản 2","code",bug2,"expectedVersion",1));check("READY".equals(changed.get("status"))&&number(changed,"version",0)==2,"edit saved exercise with optimistic version");
            check(bug1.equals(obj(call(base,"/api/rooms/current",teacher,null).body().get("room")).get("code")),"editing library never changes the assigned room snapshot");
            check(call(base,"/api/debug-library/"+libraryId,teacher,mapOf("title","Stale edit","code",bug1,"expectedVersion",1)).status()!=202,"stale edits rejected");
            bank.delete(owner,"MANUAL_SOURCE",1);
            call(base,"/api/rooms/current/command",teacher,mapOf("action","LEAVE_ROOM"));call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM","roomCode","MANUAL_ROOM_2"));waitFor(()->call(base,"/api/rooms/current",teacher,null).status()==200,"second room");roomId=room(base,teacher);ok(base,teacher,"START",Map.of());
            Map<String,Object> selected=ok(base,teacher,"SELECT_LIBRARY",mapOf("libraryId",libraryId,"libraryVersion",2));snapshotId=text(obj(selected.get("exercise")),"id","");check(bug2.equals(obj(call(base,"/api/rooms/current",teacher,null).body().get("room")).get("code")),"reuse in another room after original source was removed");
            join(base,student,roomId);check(!stringify(view(base,student)).contains("referenceSolution"),"student classroom does not expose correct solution");
            ok(base,teacher,"FORK",Map.of());ok(base,student,"BRANCH_SAVE",mapOf("expectedVersion",0,"code",code));ok(base,student,"BRANCH_CHECK",mapOf("expectedVersion",1));
            check("ACCEPTED".equals(DebugLessonIntegrationTest.waitResult(base,student).get("verdict")),"student fixes saved buggy code and receives AC");
        }
        try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments,sandbox)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!");
            Map<String,Object> item=obj(call(base,"/api/debug-library/"+libraryId,teacher,null).body().get("item"));check("READY".equals(item.get("status"))&&number(item,"version",0)==2,"manual library preserved after restart without joining a room");
            Map<String,Object> syntax=save(base,teacher,libraryId,mapOf("title","Lỗi cú pháp tự soạn","code","public class Main { syntax error }","expectedVersion",2));check("READY".equals(syntax.get("status"))&&"COMPILE_ERROR".equals(obj(syntax.get("validation")).get("brokenVerdict")),"manual syntax errors can be prepared and reused");
            Map<String,Object> noError=save(base,teacher,libraryId,mapOf("title","Cần sửa tiếp","code",code+" ","expectedVersion",3));check("FAILED".equals(noError.get("status"))&&(code+" ").equals(noError.get("starterCode")),"equivalent code remains saved but not ready to assign");
            join(base,teacher,roomId);check(snapshotId.equals(obj(view(base,teacher).get("exercise")).get("id")),"active lesson survives library edits and restart");
            ok(base,teacher,"FREEZE",Map.of());ok(base,teacher,"MERGE",mapOf("userId",DebugLessonIntegrationTest.identity(db,"student1").userId(),"branchVersion",1));
            check(action(base,teacher,"SELECT_LIBRARY",mapOf("libraryId",libraryId,"libraryVersion",4)).status()!=200,"unvalidated library item cannot be assigned");
            check(call(base,"/api/debug-library/"+libraryId,teacher,mapOf("action","DELETE","expectedVersion",3)).status()!=200,"stale delete rejected");
            check(call(base,"/api/debug-library/"+libraryId,teacher,mapOf("action","DELETE","expectedVersion",4)).status()==200,"owner soft deletes saved item");
            check(rows(call(base,"/api/debug-library",teacher,null).body().get("items")).isEmpty(),"deleted item removed from list");
            check(snapshotId.equals(obj(view(base,teacher).get("exercise")).get("id")),"deleting library item preserves assigned lesson");
        }
        System.out.println("DebugLibraryIntegrationTest PASS ("+checks+" assertions; "+(docker?"real Docker":"trusted fixture javac")+")");
    }
}