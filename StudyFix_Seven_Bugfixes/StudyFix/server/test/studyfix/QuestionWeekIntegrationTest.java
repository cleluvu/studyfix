package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP -> existing gateway/service -> isolated learning database; no Docker required. */
public final class QuestionWeekIntegrationTest {
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"question-week-test-"),db=temp.resolve("core.db"),learning=temp.resolve("learning.db");String saved;
  try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
   core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");
   check(send(base,"","PRACTICE_WEEK_LIST",Map.of()).status()!=200,"anonymous bank access rejected");
   check(rows(assign(base,teacher,"PRACTICE_WEEK_LIST",Map.of()).get("weeks")).isEmpty(),"no fabricated weeks seeded");
   check(send(base,student,"PRACTICE_WEEK_SAVE",mapOf("weekNo",1,"title","Forbidden")).status()!=200,"Student cannot create Week");
   check(send(base,teacher,"PRACTICE_WEEK_SAVE",mapOf("weekNo",0,"title","Bad")).status()!=200,"invalid number rejected");
   check(send(base,teacher,"PRACTICE_WEEK_SAVE",mapOf("weekNo",1,"title"," ")).status()!=200,"empty title rejected");
   saved=text(assign(base,teacher,"PRACTICE_WEEK_SAVE",mapOf("weekNo",3,"title","Mảng một chiều","description","Khai báo, tìm kiếm, sắp xếp")),"weekId","");Map<String,Object> id=mapOf("weekId",saved);
   check(!saved.isBlank(),"Teacher creates Week without room membership");
   check(send(base,teacher,"PRACTICE_WEEK_SAVE",mapOf("weekNo",3,"title","Duplicate")).status()!=200,"duplicate Week number rejected");
   Map<String,Object> studentWeek=rows(assign(base,student,"PRACTICE_WEEK_LIST",Map.of()).get("weeks")).getFirst();
   check(Boolean.FALSE.equals(studentWeek.get("isMine"))&&!studentWeek.containsKey("owner_id"),"Student can browse without owner internals");
   for(String action:List.of("PRACTICE_WEEK_ADD","PRACTICE_WEEK_REMOVE","PRACTICE_WEEK_DELETE"))check(send(base,student,action,mapOf("weekId",saved,"exerciseIds",List.of("ARR001"))).status()!=200,"Student cannot mutate "+action);
   assign(base,teacher,"PRACTICE_WEEK_ADD",mapOf("weekId",saved,"exerciseIds",List.of("ARR001","ARR002","ARR001")));
   assign(base,teacher,"PRACTICE_WEEK_ADD",mapOf("weekId",saved,"exerciseIds",List.of("ARR001")));
   Map<String,Object> list=assign(base,student,"PRACTICE_LIST",id);
   check(number(list,"total",0)==2&&number(obj(list.get("stats")),"total",0)==2,"Week list and stats scoped to distinct linked questions");
   check(number(obj(list.get("stats")),"fresh",0)==2,"personal progress initially new");
   check(number(assign(base,student,"PRACTICE_LIST",mapOf("weekId",saved,"query","ARR002")),"total",0)==1,"search scoped within Week");
   check(send(base,teacher,"PRACTICE_WEEK_ADD",mapOf("weekId",saved,"exerciseIds",List.of("ARR003","NOT_REAL"))).status()!=200,"invalid batch rejected");
   check(number(assign(base,student,"PRACTICE_LIST",id),"total",0)==2,"failed batch cannot partially add");
   try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role) VALUES(?,?,?,?,?)")){p.setString(1,UUID.randomUUID().toString());p.setString(2,"week_teacher2");p.setString(3,Passwords.hash("Teacher234!".toCharArray()));p.setString(4,"Teacher B");p.setString(5,"TEACHER");p.executeUpdate();}
   String foreign=login(base,"week_teacher2","Teacher234!");
   check(send(base,foreign,"PRACTICE_WEEK_SAVE",mapOf("weekId",saved,"weekNo",3,"title","Hijack")).status()!=200,"foreign Teacher cannot edit");
   check(send(base,foreign,"PRACTICE_WEEK_ADD",mapOf("weekId",saved,"exerciseIds",List.of("ARR003"))).status()!=200,"foreign Teacher cannot change question associations");
   check(send(base,foreign,"PRACTICE_WEEK_DELETE",id).status()!=200,"foreign Teacher cannot delete");
   assign(base,teacher,"PRACTICE_WEEK_SAVE",mapOf("weekId",saved,"weekNo",3,"title","Mảng cơ bản","description","Nội dung đã cập nhật"));
   assign(base,teacher,"PRACTICE_WEEK_REMOVE",mapOf("weekId",saved,"exerciseIds",List.of("ARR002")));
   check(number(assign(base,student,"PRACTICE_LIST",id),"total",0)==1,"remove association updates scoped list");
   check(obj(assign(base,student,"PRACTICE_GET",mapOf("exerciseId","ARR002")).get("exercise")).get("id").equals("ARR002"),"removing from Week preserves original question");
  }
  try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
   core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!");
   Map<String,Object> week=rows(assign(base,teacher,"PRACTICE_WEEK_LIST",Map.of()).get("weeks")).getFirst();
   check(saved.equals(week.get("id"))&&"Mảng cơ bản".equals(week.get("title"))&&number(week,"exerciseCount",0)==1,"Week metadata and associations survive server restart");
   assign(base,teacher,"PRACTICE_WEEK_DELETE",mapOf("weekId",saved));
   check(rows(assign(base,teacher,"PRACTICE_WEEK_LIST",Map.of()).get("weeks")).isEmpty(),"owner deletes Week");
   check(obj(assign(base,teacher,"PRACTICE_GET",mapOf("exerciseId","ARR001")).get("exercise")).get("id").equals("ARR001"),"deleting Week preserves question");
   check(send(base,teacher,"PRACTICE_LIST",mapOf("weekId",saved)).status()!=200,"stale deleted Week rejected");
   try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+learning.toAbsolutePath());Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM p3_question_week_items")){check(r.next()&&r.getInt(1)==0,"delete cascades only membership rows");}
  }
  System.out.println("Question Week HTTP integration PASS");
 }
}
