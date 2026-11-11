package studyfix;

import studyfix.assignment.grading.GradingSandbox;
import studyfix.assignment.model.*;
import studyfix.assignment.persistence.*;
import studyfix.assignment.transport.AssignmentTcpServer;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP/SQLite and javac execution of this test's own fixed fixture; no production sandbox bypass. */
public final class DebugLessonIntegrationTest {
    static final String CODE="import java.util.*; public class Main { public static void main(String[] a) { int n=new Scanner(System.in).nextInt(); if(n<=5) System.out.println(n+1); else System.out.println(n-1); } }";
    static final List<Exercise.TestCase> TESTS=List.of(new Exercise.TestCase("5\n","6\n",1,false),new Exercise.TestCase("6\n","5\n",1,true));
    static Exercise exercise(String id,String reference) {
        return new Exercise(id,"Debug fixture","Conditions","Easy","Read n; print n+1 if n<=5, else n-1.","n","answer","0<=n<=10","public class Main {}","PRIVATE_HINT",reference,ExerciseMode.BUILD_FROM_SCRATCH,"public class Main {}",TESTS);
    }
    static Identity identity(Path db,String username)throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);PreparedStatement p=c.prepareStatement("SELECT user_id,display_name,role FROM users WHERE username=?")) {
            p.setString(1,username);try(ResultSet r=p.executeQuery()){r.next();return new Identity(r.getString(1),username,r.getString(2),Identity.Role.valueOf(r.getString(3)));}
        }
    }
    static Map<String,Object> ready(String base,String token,String id)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        while(System.nanoTime()<deadline) {
            Response r=call(base,"/api/classroom/debug/previews/"+id,token,null);check(r.status()==200,"preview read: "+r.body());
            Map<String,Object> p=obj(r.body().get("preview"));
            if(Set.of("READY","FAILED").contains(text(p,"status","")))return p;
            Thread.sleep(80);
        }
        throw new AssertionError("preview timeout");
    }
    static Map<String,Object> create(String base,String token,Map<String,Object> body)throws Exception {
        Response r=call(base,"/api/classroom/debug/previews",token,body);check(r.status()==202,"preview accepted: "+r.body());
        return ready(base,token,text(obj(r.body().get("preview")),"id",""));
    }
    static Map<String,Object> waitResult(String base,String token)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(System.nanoTime()<deadline){Map<String,Object> latest=obj(rows(view(base,token).get("branches")).getFirst().get("latestCheck"));if("DONE".equals(latest.get("status")))return obj(latest.get("result"));Thread.sleep(100);}
        throw new AssertionError("student grading timeout");
    }
    static GradingResult grade(Path root,String id,String code,List<Exercise.TestCase> tests,double max)throws Exception {
        Path dir=Files.createTempDirectory(root,"run-");Path file=dir.resolve("Main.java");Files.writeString(file,code);
        int compiled=ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-d",dir.toString(),file.toString());
        if(compiled!=0)return new GradingResult("COMPILE_ERROR",0,max,0,tests.size(),"test compile",0,List.of());
        List<Map<String,Object>> results=new ArrayList<>();int passed=0;String javaExe=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        for(Exercise.TestCase t:tests) {
            Process p=new ProcessBuilder(javaExe,"-cp",dir.toString(),"Main").redirectErrorStream(true).start();
            try(var input=p.getOutputStream()){input.write(t.input().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();return new GradingResult("TIME_LIMIT_EXCEEDED",0,max,0,tests.size(),"test timeout",0,List.of());}
            String output=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            if(p.exitValue()!=0)return new GradingResult("RUNTIME_ERROR",0,max,0,tests.size(),"test runtime",0,List.of());
            boolean matches=output.strip().equals(t.expected().strip());if(matches)passed++;
            // Include unsafe hidden fields deliberately: classroom serialization must remove them.
            results.add(mapOf("hidden",t.hidden(),"passed",matches,"input",t.input(),"expectedOutput",t.expected(),"actualOutput",output,"message",matches?"Đạt":"Sai kết quả", "durationMs",1));
        }
        return new GradingResult(passed==tests.size()?"ACCEPTED":"WRONG_ANSWER",max*passed/tests.size(),max,passed,tests.size(),"fixture execution",1,results);
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
        System.setProperty("studyfix.web.root",Path.of("../web-client/static").toAbsolutePath().toString());
        Path root=Files.createTempDirectory(Path.of("build-debug-lessons"),"integration-");Path db=root.resolve("core.db"),learning=root.resolve("learning.db");
        String commentCode="public class Main { String s=\"a <= b + 1\"; /* x <= y + 2 */ int f(int n){return (n /* + */ + 1) + 2;} }";
        check(DebugCodeMutator.variants(commentCode,"ARITHMETIC",1,2).stream().allMatch(v->v.code().contains("\"a <= b + 1\"")&&v.code().contains("/* x <= y + 2 */")),"comments and strings unchanged");
        for(int count=1;count<=3;count++){final int expected=count;check(DebugCodeMutator.variants(CODE,"MIXED",count,1).stream().allMatch(v->v.edits().size()==expected),"exact mutation count "+count);}
        try{DebugCodeMutator.variants(CODE,"MIXED",4,1);throw new AssertionError("count accepted");}catch(IllegalArgumentException expected){check(true,"invalid count rejected");}
        boolean docker=Arrays.asList(args).contains("--docker");
        String oneOperator="public class Main {int f(int a,int b){return a+b;}}";
        List<DebugCodeMutator.Variant> arithmetic=DebugCodeMutator.variants(oneOperator,"ARITHMETIC",1,1);
        check(arithmetic.size()==2&&arithmetic.stream().anyMatch(v->v.code().contains("a*b")),"plus offers subtraction and multiplication");
        try{DebugCodeMutator.variants(oneOperator,"ARITHMETIC",2,1);throw new AssertionError("same position counted twice");}catch(IllegalArgumentException expected){check(true,"error count requires distinct positions");}
        GradingSandbox sandbox=docker?new studyfix.assignment.grading.DockerJavaSandbox("eclipse-temurin:21-jdk",2,45):(id,code,tests,max)->grade(root,id,code,tests,max);
        String lessonId,roomId;
        try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments,sandbox)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            Identity teacherId=identity(db,"teacher"),foreignId=identity(db,"teacher2");
            QuestionBankRepository bank=new QuestionBankRepository(new AssignmentRepository(learning));
            bank.save(teacherId,exercise("DEBUG_SOURCE_TEST",CODE),"PUBLISHED",false,0,"PRACTICE");
            bank.save(foreignId,exercise("FOREIGN_SOURCE_TEST",CODE),"PUBLISHED",false,0,"PRACTICE");
            bank.save(teacherId,exercise("PRIVATE_SOURCE_TEST",CODE),"PUBLISHED",false,0,"EXAM_PRIVATE");
            bank.save(teacherId,exercise("MISSING_REFERENCE_TEST",""),"PUBLISHED",false,0,"PRACTICE");
            String teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!"),late=login(base,"student2","Student234!"),foreign=login(base,"teacher2","Teacher234!");
            call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM","roomCode","DEBUG_BANK_ROOM"));
            waitFor(()->call(base,"/api/rooms/current",teacher,null).status()==200,"create room");roomId=room(base,teacher);join(base,student,roomId);
            check(call(base,"/api/classroom/debug/sources",null,null).status()==401,"anonymous denied");
            check(call(base,"/api/classroom/debug/sources",student,null).status()==403,"student source list denied");
            check(call(base,"/api/classroom/debug/sources",foreign,null).status()!=200,"nonmember denied");
            Map<String,Object> sources=call(base,"/api/classroom/debug/sources",teacher,null).body();String list=stringify(sources);
            check(list.contains("DEBUG_SOURCE_TEST")&&!list.contains("FOREIGN_SOURCE_TEST")&&!list.contains("PRIVATE_SOURCE_TEST"),"only manageable practice sources listed");
            check(rows(sources.get("sources")).stream().filter(r->"MISSING_REFERENCE_TEST".equals(r.get("id"))).noneMatch(r->Boolean.TRUE.equals(r.get("debugEligible"))),"missing reference marked ineligible");
            check(!list.contains(CODE),"list does not return reference source");
            check(call(base,"/api/classroom/debug/source/FOREIGN_SOURCE_TEST",teacher,null).status()==403,"foreign answer denied");
            check(call(base,"/api/classroom/debug/source/PRIVATE_SOURCE_TEST",teacher,null).status()==403,"exam-private source denied");
            check(call(base,"/api/classroom/debug/previews",teacher,mapOf("sourceId","DEBUG_SOURCE_TEST")).status()!=202,"cannot generate before lecture");
            ok(base,teacher,"START",Map.of());
            check(call(base,"/api/classroom/debug/previews",teacher,mapOf("sourceId","MISSING_REFERENCE_TEST")).status()!=202,"missing reference cannot generate");
            check(call(base,"/api/classroom/debug/previews",student,null).status()==403,"student cannot list teacher previews");
            check(call(base,"/api/classroom/debug/previews",student,mapOf("sourceId","DEBUG_SOURCE_TEST")).status()==403,"student generation denied");
            check(call(base,"/api/classroom/debug/previews",teacher,mapOf("sourceId","DEBUG_SOURCE_TEST","count",4)).status()!=202,"invalid count rejected by HTTP");
            Map<String,Object> p=create(base,teacher,mapOf("sourceId","DEBUG_SOURCE_TEST","type","MIXED","count",2));
            check("READY".equals(p.get("status")),"AST mutation validated: "+p);lessonId=text(p,"id","");
            check(stringify(call(base,"/api/classroom/debug/previews",teacher,null).body()).contains(lessonId),"ready preview listed for recovery");
            check(!p.containsKey("tests")&&p.containsKey("referenceSolution"),"teacher preview has solution but not raw tests");
            check(rows(p.get("changes")).size()==2,"two recorded mutations");
            check(call(base,"/api/classroom/debug/previews/"+lessonId,student,null).status()==403,"student answer endpoint denied");
            check(action(base,teacher,"SELECT_EXERCISE",mapOf("exerciseId",lessonId)).status()!=200,"cannot bypass snapshot selection");
            ok(base,teacher,"SELECT_DEBUG",mapOf("previewId",lessonId));
            String studentView=stringify(view(base,student));check(!studentView.contains("referenceSolution")&&!studentView.contains("PRIVATE_HINT")&&!studentView.contains("changes"),"student classroom has no private answer or hints");
            // Change source after creating the immutable lesson; grading must keep the captured tests.
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+learning);Statement st=c.createStatement()) {st.executeUpdate("UPDATE p3_test_cases SET expected_output='CHANGED_SOURCE' WHERE exercise_id='DEBUG_SOURCE_TEST'");}
            ok(base,teacher,"FORK",Map.of());
            join(base,late,roomId);check(rows(view(base,late).get("branches")).size()==1,"late student gets own branch");
            ok(base,student,"BRANCH_CHECK",mapOf("expectedVersion",0));
            Map<String,Object> wrong=waitResult(base,student);
            check("WRONG_ANSWER".equals(wrong.get("verdict")),"unfixed code reports WA to student");
            List<Map<String,Object>> testResults=rows(wrong.get("publicTests"));check(testResults.size()==2,"per-test outcomes returned");
            check(testResults.getFirst().containsKey("input")&&testResults.getFirst().containsKey("expectedOutput")&&testResults.getFirst().containsKey("actualOutput"),"public test includes input expected and actual output");
            Map<String,Object> hidden=testResults.get(1);check(Boolean.TRUE.equals(hidden.get("hidden"))&&!hidden.containsKey("input")&&!hidden.containsKey("expectedOutput")&&!hidden.containsKey("actualOutput"),"hidden test never exposes input or answers");
            long nextSubmitAt=number(obj(rows(view(base,student).get("branches")).getFirst().get("latestCheck")),"createdAt",0)+3100;
            long cooldown=nextSubmitAt-System.currentTimeMillis();if(cooldown>0)Thread.sleep(cooldown);
            ok(base,student,"BRANCH_SAVE",mapOf("expectedVersion",0,"code",CODE));ok(base,student,"BRANCH_CHECK",mapOf("expectedVersion",1));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);boolean accepted=false;
            while(System.nanoTime()<deadline){Map<String,Object> branch=rows(view(base,student).get("branches")).getFirst();Map<String,Object> latest=obj(branch.get("latestCheck"));if("DONE".equals(latest.get("status"))){accepted="ACCEPTED".equals(obj(latest.get("result")).get("verdict"));break;}Thread.sleep(100);}
            check(accepted,"student corrected code passes original snapshot tests");
            ok(base,late,"BRANCH_SAVE",mapOf("expectedVersion",0,"code","public class Main { syntax error }"));ok(base,late,"BRANCH_CHECK",mapOf("expectedVersion",1));
            Map<String,Object> compile=waitResult(base,late);check("COMPILE_ERROR".equals(compile.get("verdict")),"student receives compiler error verdict");check(!text(compile,"feedback","").isBlank(),"student receives compiler diagnostics");
            ok(base,teacher,"FREEZE",Map.of());ok(base,teacher,"MERGE",mapOf("userId",identity(db,"student1").userId(),"branchVersion",1));
            // Equivalent edits and syntax errors never become READY.
            check(call(base,"/api/classroom/debug/previews",teacher,mapOf("sourceId","FOREIGN_SOURCE_TEST","code",CODE+" ")).status()==403,"foreign preview denied");
            // Source test edits above cannot affect already captured lesson.
            call(base,"/api/rooms/current/command",foreign,mapOf("action","CREATE_ROOM","roomCode","OTHER_DEBUG_ROOM"));
            waitFor(()->call(base,"/api/rooms/current",foreign,null).status()==200,"second room");ok(base,foreign,"START",Map.of());
            check(call(base,"/api/classroom/debug/previews/"+lessonId,foreign,null).status()==403,"other room cannot read snapshot");
            check(action(base,foreign,"SELECT_DEBUG",mapOf("previewId",lessonId)).status()==403,"other room cannot select snapshot");
            bank.save(teacherId,exercise("VALIDATION_CASES",CODE),"PUBLISHED",false,0,"PRACTICE");
            Map<String,Object> first=create(base,teacher,mapOf("sourceId","VALIDATION_CASES","type","ARITHMETIC","count",1));
            check("READY".equals(first.get("status")),"first arithmetic variant ready");
            Map<String,Object> another=create(base,teacher,mapOf("sourceId","VALIDATION_CASES","type","ARITHMETIC","count",1,"excludePreviewId",first.get("id")));
            check("READY".equals(another.get("status"))&&!first.get("starterCode").equals(another.get("starterCode")),"regeneration must change actual source code");
            check(number(another,"count",0)==1&&rows(another.get("changes")).size()==1,"regeneration preserves requested distinct error count");
            check(call(base,"/api/classroom/debug/previews",teacher,mapOf("sourceId","VALIDATION_CASES","excludePreviewId",lessonId)).status()!=202,"cannot exclude preview from different source");
            check(call(base,"/api/classroom/debug/previews",foreign,mapOf("sourceId","FOREIGN_SOURCE_TEST","excludePreviewId",first.get("id"))).status()==403,"cannot exclude another room's private preview");
            String singleCode="import java.util.*;public class Main{public static void main(String[] a){int n=new Scanner(System.in).nextInt();System.out.println(n<=5?6:5);}}";
            bank.save(teacherId,exercise("SINGLE_CONDITION",singleCode),"PUBLISHED",false,0,"PRACTICE");
            Map<String,Object> single=create(base,teacher,mapOf("sourceId","SINGLE_CONDITION","type","CONDITION","count",1));
            check("READY".equals(single.get("status")),"single condition mutation ready");
            Map<String,Object> exhausted=create(base,teacher,mapOf("sourceId","SINGLE_CONDITION","type","CONDITION","count",1,"excludePreviewId",single.get("id")));
            check("FAILED".equals(exhausted.get("status"))&&text(exhausted,"message","").contains("Không có biến thể khác"),"exhausted alternatives report clearly instead of repeating code");
            check("READY".equals(obj(call(base,"/api/classroom/debug/previews/"+single.get("id"),teacher,null).body().get("preview")).get("status")),"previous preview remains available");
            Map<String,Object> eq=create(base,teacher,mapOf("sourceId","VALIDATION_CASES","code",CODE+" "));
            check("FAILED".equals(eq.get("status")),"equivalent custom edit rejected");
            Map<String,Object> ce=create(base,teacher,mapOf("sourceId","VALIDATION_CASES","code","public class Main { invalid syntax }"));
            check("FAILED".equals(ce.get("status")),"compile error rejected for logic exercise");
            check(call(base,"/api/classroom/debug/previews/"+lessonId,teacher,null).status()==200,"ready snapshot still accessible");
        } catch (AssertionError failure) {
            throw failure;
        }
        try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments,sandbox)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();String teacher=login(base,"teacher","Teacher123!");join(base,teacher,roomId);
            Map<String,Object> persisted=obj(call(base,"/api/classroom/debug/previews/"+lessonId,teacher,null).body().get("preview"));
            check(stringify(call(base,"/api/classroom/debug/previews",teacher,null).body()).contains(lessonId),"persisted preview listed after restart");
            check("READY".equals(persisted.get("status")),"snapshot restored after server restart");
            check(lessonId.equals(obj(view(base,teacher).get("exercise")).get("id")),"active lesson restored after restart");
            ok(base,teacher,"SELECT_DEBUG",mapOf("previewId",lessonId));
        }
        System.out.println("Debug lesson integration PASS ("+checks+" assertions; "+(docker?"real Docker":"trusted fixture javac")+")");
    }
}