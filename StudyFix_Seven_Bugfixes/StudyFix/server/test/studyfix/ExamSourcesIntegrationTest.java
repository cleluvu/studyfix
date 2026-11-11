package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Source classification and browser selection use isolated real HTTP/SQLite. */
public final class ExamSourcesIntegrationTest {
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"exam-sources-");
  try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");
   for(int i=1;i<=8;i++)assign(base,teacher,"CATALOG_CREATE",QuestionBankIntegrationTest.question("SOURCE_PUBLIC_"+i,"Practice source "+i,"PUBLISHED"));
   for(int i=1;i<=4;i++){var q=QuestionBankIntegrationTest.question("SOURCE_PRIVATE_"+i,"Private source "+i,"PUBLISHED");q.put("bankScope","EXAM_PRIVATE");assign(base,teacher,"CATALOG_CREATE",q);}
   String week=text(assign(base,teacher,"PRACTICE_WEEK_SAVE",mapOf("weekNo",1,"title","Nguồn Luyện tập","description","Random selection fixture")),"weekId","");
   assign(base,teacher,"PRACTICE_WEEK_ADD",mapOf("weekId",week,"exerciseIds",List.of("SOURCE_PUBLIC_1","SOURCE_PUBLIC_2","SOURCE_PUBLIC_3","SOURCE_PUBLIC_4","SOURCE_PUBLIC_5","SOURCE_PUBLIC_6","SOURCE_PRIVATE_1")));
   var sources=assign(base,teacher,"EXAMBANK_SOURCES",Map.of());
   check(rows(sources.get("questions")).stream().filter(q->text(q,"id","").startsWith("SOURCE_PRIVATE_")).allMatch(q->"EXAM_PRIVATE".equals(q.get("bankScope"))),"private source questions carry their scope");
   check(!stringify(sources).contains("SECRET_TEST"),"source directory excludes hidden tests");
   check(send(base,student,"EXAMBANK_SOURCES",Map.of()).status()!=200,"students cannot read exam sources");
   var doc=ExamBankIntegrationTest.doc(List.of(ExamBankIntegrationTest.item("SOURCE_PUBLIC_1",50),ExamBankIntegrationTest.item("SOURCE_PRIVATE_1",50)));
   var mixed=ExamBankIntegrationTest.save(base,teacher,"MIXED_SOURCES",doc);String id=text(mixed,"id","");
   check(list(mixed.get("sourceKinds")).containsAll(List.of("ALL","PRIVATE")),"mixed exam retains both source labels");
   for(String kind:List.of("ALL","PRIVATE"))check(number(assign(base,teacher,"EXAMBANK_LIST",mapOf("query","MIXED_SOURCES","source",kind)),"total",0)==1,"mixed exam can be filtered by "+kind);
   var weekDoc=ExamBankIntegrationTest.doc(List.of(ExamBankIntegrationTest.item("SOURCE_PUBLIC_1",100)));weekDoc.put("sourceWeeks",List.of(mapOf("id",week,"title","Week 1: Nguồn Luyện tập")));
   ExamBankIntegrationTest.save(base,teacher,"WEEK_SOURCES",weekDoc);check(number(assign(base,teacher,"EXAMBANK_LIST",mapOf("query","WEEK_SOURCES","source","WEEK")),"total",0)==1,"Week Luyện tập filter remains supported");
   if(args.length>0&&args[0].equals("browser")){
    var child=new ProcessBuilder("C:/Program Files/nodejs/node.exe",Path.of("../web-client/tests/exam_sources_browser.cjs").toAbsolutePath().toString(),base).inheritIO().start();
    if(!child.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("Chrome timeout");}check(child.exitValue()==0,"source selection Chrome checks pass");
   }
  }
  System.out.println("ExamSourcesIntegrationTest PASS: source scopes, mixed source labels, filters and permissions.");
 }
}
