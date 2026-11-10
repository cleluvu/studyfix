package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import studyfix.assignment.persistence.*;
import studyfix.assignment.catalog.OjPackage;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

public final class OjIntegrationTest {
 static Map<String,Object> submit(String base,String token,String id,String source,boolean run)throws Exception{
  var row=obj(assign(base,token,run?"PRACTICE_RUN":"PRACTICE_SUBMIT",mapOf("exerciseId",id,"sourceCode",source,"idempotencyKey",UUID.randomUUID().toString())).get("submission"));String sid=text(row,"id","");
  RoomLifecycleIntegrationTest.until(()->"DONE".equals(obj(assign(base,token,"PRACTICE_RESULT",mapOf("submissionId",sid)).get("submission")).get("status")),120);
  return obj(assign(base,token,"PRACTICE_RESULT",mapOf("submissionId",sid)).get("submission"));
 }
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"oj-integration-");var pack=new OjPackage(Files.readAllBytes(Path.of("private/catalogs/CodeBank_Java_100_OJ_Complete.zip")));check(pack.problems.size()==100&&pack.weeks.size()==12&&pack.tests==316,"package count comes from actual validated files");
  try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,32,1,"eclipse-temurin:21-jdk");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");Identity author=core.store().login("teacher","Teacher123!".toCharArray());
   AssignmentRepository repo=new AssignmentRepository(temp.resolve("learning.db"));OjRepository oj=new OjRepository(repo);var first=oj.importPackage(pack,author,true);check(integer(first,"inserted",0)==100,"atomic import has 100 new snapshots");check(integer(oj.importPackage(pack,author,true),"reused",0)==100,"second import reuses all 100");check(new QuestionBankRepository(repo).list(author).size()==100,"only 100 OJ exercises active");check(new QuestionWeekRepository(repo).list(author).size()==12,"12 Weeks exactly");check(repo.getExercise("MATH001")!=null,"old immutable exercises retained for historical submissions");
   for(String id:List.of("JAVA001","JAVA028","JAVA064")){
    var exercise=repo.getExercise(id);check(send(base,student,"PRACTICE_GET",mapOf("exerciseId",id)).status()!=200,"draft OJ not public before approval");check(send(base,teacher,"OJ_PUBLISH",mapOf("exerciseId",id)).status()!=200,"cannot publish without real validation");
    var sandbox=new studyfix.assignment.grading.OjJavaSandbox("eclipse-temurin:21-jdk");var reference=sandbox.grade(exercise,exercise.referenceSolution(),exercise.tests(),100,oj.config(id));var bad=sandbox.grade(exercise,exercise.template(),exercise.tests(),100,oj.config(id));Map<String,Object> trial=mapOf("verdict",reference.verdict()),wrong=mapOf("verdict",bad.verdict());check("ACCEPTED".equals(reference.verdict()),id+" reference validated in actual Docker");check(!Set.of("ACCEPTED","INTERNAL_ERROR","SANDBOX_UNAVAILABLE").contains(bad.verdict()),id+" incomplete starter rejected");
    oj.validation(id,mapOf("referencePassed","ACCEPTED".equals(trial.get("verdict")),"wrongDetected",!"ACCEPTED".equals(wrong.get("verdict")),"referenceVerdict",trial.get("verdict"),"wrongVerdict",wrong.get("verdict")));assign(base,teacher,"OJ_PUBLISH",mapOf("exerciseId",id));
    var publicData=obj(assign(base,student,"PRACTICE_GET",mapOf("exerciseId",id)).get("exercise"));String json=stringify(publicData);check(!json.contains("referenceSolution")&&!json.contains("tests/hidden/")&&!json.contains("solution/"),id+" Student DTO excludes private solution/hidden paths");check(list(publicData.get("publicTests")).size()==exercise.tests().stream().filter(t->!t.hidden()).count(),id+" only public examples returned");check(exercise.entryFile().equals(publicData.get("entryFile")),id+" correct filename exposed");
    var run=submit(base,student,id,exercise.referenceSolution(),true);check("ACCEPTED".equals(run.get("verdict"))&&integer(run,"totalTests",0)==list(publicData.get("publicTests")).size(),id+" Run sample is public only");var accepted=submit(base,student,id,exercise.referenceSolution(),false);check("ACCEPTED".equals(accepted.get("verdict"))&&integer(accepted,"totalTests",0)==exercise.tests().size(),id+" Student Submit includes hidden tests");
   }
   String modules=System.getenv("STUDYFIX_BUILDER_BROWSER_MODULES");if(modules!=null&&!modules.isBlank()){var browser=new ProcessBuilder("C:/Program Files/nodejs/node.exe",Path.of("../web-client/tests/oj_browser.cjs").toAbsolutePath().toString(),base).inheritIO();browser.environment().put("STUDYFIX_BROWSER_TEST_MODULES",modules);Process child=browser.start();if(!child.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("OJ browser timeout");}check(child.exitValue()==0,"OJ real browser renders Program/Method/Class");}
  }
  System.out.println("OjIntegrationTest PASS: 100/12 import, idempotence, snapshots, real HTTP queue/Docker Program/Method/Class, public-only Run, private hidden Submit and explicit approval.");
 }
}
