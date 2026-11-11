package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP, private publication, assets, import and optional Chrome checks. */
public final class PrivateBankIntegrationTest {
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"private-bank-");
  try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,8,1,"eclipse-temurin:21-jdk");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");
   var p=BuilderCompleteIntegrationTest.payload("SCOPE_PRIVATE","PROGRAM","public class Main {public static void main(String[] a){System.out.println(0);}}","import java.util.*;public class Main {public static void main(String[] a){Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt());}}","1 2\n","3\n","-2 8\n","6\n");p.put("bankScope","EXAM_PRIVATE");p.put("visibility","PRIVATE");
   var d=obj(assign(base,teacher,"BUILDER_CREATE",mapOf("requestKey",UUID.randomUUID().toString(),"payload",p)).get("draft"));
   byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=");
   var upload=ProblemBuilderIntegrationTest.binary(base,teacher,"/api/problem-builder/uploads/"+d.get("id")+"?name=private.png",png);
   String asset=text(obj(obj(parse(new String(upload.body(),java.nio.charset.StandardCharsets.UTF_8))).get("asset")),"id","");
   obj(p.get("statement")).put("description","Private examination question\n![Image](/api/problem-builder/assets/"+asset+")");d=BuilderCompleteIntegrationTest.save(base,teacher,d,p);
   check(Boolean.TRUE.equals(BuilderCompleteIntegrationTest.validate(base,teacher,d).get("passed")),"private draft passes real Docker validation");
   assign(base,teacher,"BUILDER_REVIEW",mapOf("draftId",d.get("id"),"expectedRevision",d.get("revision"),"approveSelf",true));
   String id=text(assign(base,teacher,"BUILDER_PUBLISH",mapOf("draftId",d.get("id"),"expectedRevision",d.get("revision"))),"exerciseId","");
   check("EXAM_PRIVATE".equals(obj(assign(base,teacher,"BANK_GET",mapOf("exerciseId",id)).get("exercise")).get("bankScope")),"private destination persisted at publication");
   check(send(base,student,"PRACTICE_GET",mapOf("exerciseId",id)).status()!=200,"private publication inaccessible via practice");
   check(ProblemBuilderIntegrationTest.binary(base,student,"/api/problem-builder/assets/"+asset,null).statusCode()!=200,"private published image inaccessible to student");
   check(ProblemBuilderIntegrationTest.binary(base,teacher,"/api/problem-builder/assets/"+asset,null).statusCode()==200,"author can access private image");
   var cloned=obj(assign(base,teacher,"BUILDER_CREATE",mapOf("requestKey",UUID.randomUUID().toString(),"baseExerciseId",id)).get("draft"));check("EXAM_PRIVATE".equals(obj(cloned.get("payload")).get("bankScope")),"new version retains private destination");
   Path importFile=temp.resolve("private-import.zip");Files.write(importFile,OjImportIntegrationTest.samplePackage(Files.readAllBytes(Path.of("private/catalogs/CodeBank_Java_100_OJ_Complete.zip")),1,"PRIVATE_IMPORT",0,true));
   if(args.length>0&&args[0].equals("browser")){
    var child=new ProcessBuilder("C:/Program Files/nodejs/node.exe",Path.of("../web-client/tests/private_bank_browser.cjs").toAbsolutePath().toString(),base,importFile.toAbsolutePath().toString()).inheritIO().start();
    if(!child.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("Chrome test timed out");}check(child.exitValue()==0,"Chrome bank tabs and import pass");
   }
  }
  System.out.println("PrivateBankIntegrationTest PASS: real private publication, validation, asset access and destination inheritance.");
 }
}
