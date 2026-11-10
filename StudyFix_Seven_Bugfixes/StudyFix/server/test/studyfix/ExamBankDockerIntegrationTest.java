package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Verify common grading policy and exam-specific maximum using real Docker jobs. */
public final class ExamBankDockerIntegrationTest {
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path tmp=Files.createTempDirectory(Path.of("build"),"exam-bank-docker-");
  try(Server core=new Server(0,tmp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),tmp.resolve("learning.db"),0,8,1,"eclipse-temurin:21-jdk");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),t=login(base,"teacher","Teacher123!"),s=login(base,"student1","Student123!");
   Map<String,Object> question=QuestionBankIntegrationTest.question("EB_DOCKER","Sum two integers","PUBLISHED");question.put("tests",List.of(mapOf("input","1 2","expectedOutput","3","weight",1,"hidden",false),mapOf("input","-2 8","expectedOutput","6","weight",1,"hidden",true)));assign(base,t,"CATALOG_CREATE",question);
   Map<String,Object> document=mapOf("title","Docker grade policy","durationMinutes",10,"totalPoints",37,"items",List.of(mapOf("exerciseId","EB_DOCKER","points",37)),"grading",mapOf("inherit",false,"timeLimitMs",Integer.getInteger("studyfix.test.docker.timeLimitMs",1000),"memoryLimitMb",256,"outputLimitKb",1024,"checker","TOKEN","partialScoring",false));
   var draft=ExamBankIntegrationTest.save(base,t,"DOCKER_EXAM",document);var approved=ExamBankIntegrationTest.approve(base,t,draft);
   call(base,"/api/rooms/current/command",t,mapOf("action","CREATE_ROOM","roomCode","EB_DOCKER"));join(base,s,"EB_DOCKER");String contest=text(assign(base,t,"CONTEST_CREATE",mapOf("title","Real Docker exam","durationMinutes",10,"examVersionId",approved.get("id"))),"contestId","");
   String correct="import java.util.*; public class Main {public static void main(String[] a){Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt());}}";
   Map<String,String> cases=new LinkedHashMap<>();cases.put("ACCEPTED",correct);cases.put("WRONG_ANSWER","public class Main{public static void main(String[] a){System.out.println(3);}}");
   if(Arrays.asList(args).contains("--verdicts")){cases.put("COMPILE_ERROR","public class Main { broken syntax }");cases.put("RUNTIME_ERROR","public class Main {public static void main(String[] a){throw new IllegalStateException();}}");cases.put("TIME_LIMIT_EXCEEDED","public class Main {public static void main(String[] a){while(true){}}}");}
   for(var sample:cases.entrySet()){String code=sample.getValue();
    Map<String,Object> receipt=obj(assign(base,s,"CONTEST_SUBMIT",mapOf("contestId",contest,"exerciseId","EB_DOCKER","sourceCode",code,"idempotencyKey",UUID.randomUUID().toString())).get("submission"));String submission=text(receipt,"id","");Map<String,Object> result=receipt;long end=System.nanoTime()+120_000_000_000L;
    while(List.of("QUEUED","RUNNING").contains(result.get("status"))&&System.nanoTime()<end){Thread.sleep(150);result=obj(assign(base,s,"CONTEST_SUBMISSION_RESULT",mapOf("submissionId",submission)).get("submission"));}
    check("DONE".equals(result.get("status")),"Docker job finishes");check(number(result,"maxScore",0)==37,"real grade uses 37-point maximum");check(sample.getKey().equals(result.get("verdict"))&&number(result,"score",-1)==(code.equals(correct)?37:0),"Expected "+sample.getKey()+" with exam points; got "+result);System.out.println("Docker exam verdict confirmed: "+sample.getKey());
   }
  }
  System.out.println("ExamBankDockerIntegrationTest PASS: actual Docker TOKEN checker, exam points and all-or-nothing policy.");
 }
}
