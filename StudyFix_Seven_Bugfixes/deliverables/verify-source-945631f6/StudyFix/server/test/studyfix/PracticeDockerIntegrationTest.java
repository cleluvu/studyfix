package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.PracticeIntegrationTest.request;
import static studyfix.PracticeIntegrationTest.data;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Test tùy chọn bằng Docker thật, cần có sẵn image Java đã cấu hình trên máy. */
public final class PracticeDockerIntegrationTest {
    static Map<String,Object> submitAndWait(String base,String token,String type,String source,String key)throws Exception {
        Map<String,Object> row=obj(data(request(base,token,type,mapOf("exerciseId","MATH001","sourceCode",source,"idempotencyKey",key))).get("submission"));
        String id=text(row,"id","");
        RoomLifecycleIntegrationTest.until(()->"DONE".equals(obj(obj(request(base,token,"PRACTICE_RESULT",mapOf("submissionId",id)).body().get("data")).get("submission")).get("status")),90);
        return obj(data(request(base,token,"PRACTICE_RESULT",mapOf("submissionId",id))).get("submission"));
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.clearProperty("studyfix.assignment.docker");
        Path temp=Files.createTempDirectory(Path.of("build"),"practice-real-docker-");
        try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,32,1,"eclipse-temurin:21-jdk");WebGatewayServer web=new WebGatewayServer(core,assignments)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();String student=login(base,"student1","Student123!"),teacher=login(base,"teacher","Teacher123!");
            String correct="import java.util.*;public class Main{public static void main(String[] a){int n=new Scanner(System.in).nextInt();long f=1;for(int i=2;i<=n;i++)f*=i;System.out.println(f);}}";
            Map<String,Object> run=submitAndWait(base,teacher,"PRACTICE_RUN",correct,"teacher-public-run");
            check("ACCEPTED".equals(run.get("verdict")),"real teacher public run passes");
            check(number(run,"totalTests",0)==2,"public run executes only two visible examples");
            check(number(data(request(base,teacher,"PRACTICE_SUBMISSIONS",mapOf("exerciseId","MATH001"))),"total",-1)==0,"public run does not create official history");
            Map<String,Object> ac=submitAndWait(base,student,"PRACTICE_SUBMIT",correct,"student-ac");
            check("ACCEPTED".equals(ac.get("verdict"))&&number(ac,"passedTests",0)==3,"real student solution passes public and hidden tests");
            check(number(ac,"version",0)==1,"accepted solution is version one");
            Map<String,Object> wa=submitAndWait(base,student,"PRACTICE_SUBMIT","public class Main{public static void main(String[] a){System.out.println(0);}}","student-wa");
            check("WRONG_ANSWER".equals(wa.get("verdict"))&&number(wa,"version",0)==2,"wrong Java solution is version two WA");
            Map<String,Object> ce=submitAndWait(base,student,"PRACTICE_SUBMIT","public class Main { this is not Java; }","student-ce");
            check("COMPILE_ERROR".equals(ce.get("verdict"))&&number(ce,"version",0)==3,"invalid Java has compile error on version three");
            Map<String,Object> progress=data(request(base,student,"PRACTICE_LIST",mapOf("query","MATH001")));
            check("AC".equals(rows(progress.get("exercises")).getFirst().get("practiceStatus")),"real AC survives later WA and CE");
            Map<String,Object> history=data(request(base,student,"PRACTICE_SUBMISSIONS",mapOf("exerciseId","MATH001")));
            check(rows(history.get("submissions")).size()==3&&"COMPILE_ERROR".equals(rows(history.get("submissions")).getFirst().get("verdict")),"real history preserves all verdicts newest first");
            Map<String,Object> original=obj(data(request(base,student,"PRACTICE_RESULT",mapOf("submissionId",ac.get("id")))).get("submission"));
            check(correct.equals(original.get("sourceCode"))&&"ACCEPTED".equals(original.get("verdict")),"AC snapshot unchanged after later submissions");
            check(core.rooms.isEmpty(),"real grading needs no classroom");
        }
        System.out.println("PracticeDockerIntegrationTest: "+ClassroomIntegrationTest.checks+" checks PASS (real HTTP + Docker Java AC/WA/CE).");
    }
}
