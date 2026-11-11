package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import studyfix.assignment.persistence.AssignmentRepository;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

public final class QuestionBankIntegrationTest {
 static Map<String,Object> question(String id,String title,String status){return mapOf("id",id,"title",title,"topic","Mảng một chiều","difficulty","Dễ","mode","BUILD_FROM_SCRATCH","statement","Read n","inputFormat","n","outputFormat","n","constraints","n < 100","template","public class Main {}","publication",status,"tests",List.of(mapOf("input","1","expectedOutput","OLD_PUBLIC","weight",1,"hidden",false),mapOf("input","2","expectedOutput","SECRET_TEST","weight",1,"hidden",true)));}
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"question-bank-test-"),db=temp.resolve("core.db"),learning=temp.resolve("learning.db");String newest;
  try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
   core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");
   check(send(base,student,"BANK_LIST",Map.of()).status()!=200,"Student cannot list management bank");
   Map<String,Object> original=assign(base,teacher,"BANK_LIST",Map.of());check(!obj(original.get("stats")).containsKey("accepted")&&number(obj(original.get("stats")),"drafts",-1)==0,"legacy migration publishes existing catalog and management has no AC counters");
   assign(base,teacher,"CATALOG_CREATE",question("BANK_DRAFT","Draft question","DRAFT"));
   check(number(assign(base,student,"PRACTICE_LIST",mapOf("query","BANK_DRAFT")),"total",-1)==0,"draft hidden from Student directory");
   check(send(base,student,"PRACTICE_GET",mapOf("exerciseId","BANK_DRAFT")).status()!=200,"draft cannot be opened through practice API");
   check(send(base,student,"BANK_GET",mapOf("exerciseId","BANK_DRAFT")).status()!=200,"Student cannot inspect management detail");
   Map<String,Object> detail=obj(assign(base,teacher,"BANK_GET",mapOf("exerciseId","BANK_DRAFT")).get("exercise"));check(rows(detail.get("tests")).size()==2,"author can edit full testcase set");
   try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role) VALUES(?,?,?,?,?)")){p.setString(1,UUID.randomUUID().toString());p.setString(2,"bank_teacher2");p.setString(3,Passwords.hash("Teacher234!".toCharArray()));p.setString(4,"Teacher B");p.setString(5,"TEACHER");p.executeUpdate();}
   String foreign=login(base,"bank_teacher2","Teacher234!");check(send(base,foreign,"BANK_GET",mapOf("exerciseId","BANK_DRAFT")).status()!=200,"other Teacher cannot read private draft");
   Map<String,Object> privateQuestion=question("BANK_PRIVATE","Private examination question","PUBLISHED");privateQuestion.put("bankScope","EXAM_PRIVATE");
   assign(base,teacher,"CATALOG_CREATE",privateQuestion);
   check(number(assign(base,teacher,"BANK_LIST",mapOf("query","BANK_PRIVATE")),"total",-1)==0,"private question is outside practice bank");
   check(number(assign(base,teacher,"BANK_LIST",mapOf("bankScope","EXAM_PRIVATE","query","BANK_PRIVATE")),"total",-1)==1,"owner sees published question in private bank");
   check(number(assign(base,student,"PRACTICE_LIST",mapOf("bankScope","EXAM_PRIVATE","query","BANK_PRIVATE")),"total",-1)==0,"student cannot list private questions even with forged scope");
   for(String action:List.of("PRACTICE_GET","PRACTICE_SUBMISSIONS","PRACTICE_SUBMIT","PRACTICE_RUN"))check(send(base,student,action,mapOf("exerciseId","BANK_PRIVATE","sourceCode","public class Main {}","idempotencyKey","private-probe")).status()!=200,"private question blocked through "+action);
   check(send(base,foreign,"BANK_GET",mapOf("exerciseId","BANK_PRIVATE")).status()!=200,"private publication is not shared with another teacher");
   check(number(assign(base,foreign,"BANK_LIST",mapOf("bankScope","EXAM_PRIVATE","query","BANK_PRIVATE")),"total",-1)==0,"foreign teacher cannot list private publication");
   String privateWeek=text(assign(base,teacher,"PRACTICE_WEEK_SAVE",mapOf("weekNo",99,"title","Private scope week")),"weekId","");
   assign(base,teacher,"PRACTICE_WEEK_ADD",mapOf("weekId",privateWeek,"exerciseIds",List.of("BANK_PRIVATE")));
   check(!stringify(assign(base,student,"PRACTICE_WEEK_LIST",Map.of())).contains("BANK_PRIVATE"),"week metadata never leaks private exercise IDs to students");
   privateQuestion.put("expectedRevision",1);String privateRevision=text(obj(assign(base,teacher,"BANK_UPDATE",privateQuestion).get("exercise")),"id","");
   check("EXAM_PRIVATE".equals(obj(assign(base,teacher,"BANK_GET",mapOf("exerciseId",privateRevision)).get("exercise")).get("bankScope")),"question edits preserve the private destination");
   check(send(base,teacher,"BANK_LIST",mapOf("bankScope","UNKNOWN")).status()!=200,"invalid bank scope rejected");
   Map<String,Object> publish=question("BANK_DRAFT","Published bank question","PUBLISHED");publish.put("expectedRevision",1);String published=text(obj(assign(base,teacher,"BANK_UPDATE",publish).get("exercise")),"id","");
   check(!published.equals("BANK_DRAFT"),"edits create immutable version IDs");
   check(number(assign(base,student,"PRACTICE_LIST",mapOf("query","Published bank question")),"total",0)==1,"published version appears to Student");
   check(!stringify(assign(base,student,"PRACTICE_GET",mapOf("exerciseId",published))).contains("SECRET_TEST"),"Student cannot obtain hidden answers");
   check(!stringify(assign(base,foreign,"BANK_GET",mapOf("exerciseId",published))).contains("SECRET_TEST"),"non-author Teacher gets public preview only");
   check(send(base,teacher,"BANK_UPDATE",publish).status()!=200,"stale edits rejected");
   check(send(base,foreign,"BANK_DELETE",mapOf("exerciseId",published,"expectedRevision",2)).status()!=200,"foreign Teacher cannot delete author's question");
   call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM","roomCode","BANK-ROOM"));join(base,student,"BANK-ROOM");
   check(send(base,teacher,"CONTEST_CREATE",mapOf("title","Invalid draft","exerciseIds",List.of("BANK_DRAFT"),"durationMinutes",10)).status()!=200,"unpublished/superseded question cannot enter new contest");
   String contest=text(assign(base,teacher,"CONTEST_CREATE",mapOf("title","Bank preservation","exerciseIds",List.of(published),"durationMinutes",10)),"contestId","");
   Map<String,Object> bankRow=rows(assign(base,teacher,"BANK_LIST",mapOf("query","Published bank question")).get("exercises")).getFirst();check(number(bankRow,"usageCount",0)==1,"real usage counts distinct contests");
   Map<String,Object> edit=question(published,"Changed future question","PUBLISHED");edit.put("expectedRevision",2);edit.put("tests",List.of(mapOf("input","1","expectedOutput","NEW_ANSWER","weight",1,"hidden",false)));newest=text(obj(assign(base,teacher,"BANK_UPDATE",edit).get("exercise")),"id","");
   AssignmentRepository repository=new AssignmentRepository(learning);check(repository.getExercise(published).tests().getFirst().expected().equals("OLD_PUBLIC")&&repository.getExercise(newest).tests().getFirst().expected().equals("NEW_ANSWER"),"old grading inputs frozen while new version changes tests");
   check(stringify(assign(base,student,"CONTEST_PROBLEMS_GET",mapOf("contestId",contest))).contains("Published bank question"),"existing contest keeps original statement/title");
   check(number(rows(assign(base,teacher,"BANK_LIST",mapOf("query","Changed future question")).get("exercises")).getFirst(),"usageCount",0)==1,"usage follows question family across versions");
   check(send(base,student,"BANK_DELETE",mapOf("exerciseId",newest,"expectedRevision",3)).status()!=200,"Student cannot delete");
   assign(base,teacher,"BANK_DELETE",mapOf("exerciseId",newest,"expectedRevision",3));
   check(number(assign(base,teacher,"BANK_LIST",mapOf("query","Changed future question")),"total",-1)==0,"soft delete removes bank listing");
   check(repository.getExercise(published)!=null&&repository.getExercise(newest)!=null,"soft delete preserves source and test rows");
   check(!rows(assign(base,student,"CONTEST_PROBLEMS_GET",mapOf("contestId",contest)).get("problems")).isEmpty(),"deleted bank item does not break previously assigned contest");
   check(send(base,teacher,"CONTEST_CREATE",mapOf("title","Deleted","exerciseIds",List.of(newest),"durationMinutes",10)).status()!=200,"deleted question blocked for new contests");
  }
  try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
   core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!");check(number(assign(base,teacher,"BANK_LIST",mapOf("query","Changed future question")),"total",-1)==0,"metadata/deletion survive restart and idempotent migration");
  }
  System.out.println("QuestionBankIntegrationTest PASS: real HTTP, roles, migration, drafts, immutable edits, usage and soft-delete preservation.");
 }
}
