package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;
import static studyfix.TeacherDashboardIntegrationTest.*;

/** Isolated HTTP/SQLite coverage for student scope, scoring and room admission. */
public final class StudentPortalIntegrationTest {
 static Map<String,Object> portal(String base,String token,String query)throws Exception{var r=request(base,token,"/api/student/portal"+query,null);check(r.status()==200,"student portal: "+r.json());return map(r.json().get("data"));}
 public static void main(String[] args)throws Exception{
  System.clearProperty("studyfix.legacy.sqlite");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");System.setProperty("studyfix.web.root",Path.of("../web-client/static").toAbsolutePath().toString());
  Files.createDirectories(Path.of("build-student-portal"));Path temp=Files.createTempDirectory(Path.of("build-student-portal"),"integration-"),management=temp.resolve("management.db"),learning=temp.resolve("learning.db");System.setProperty("studyfix.management.sqlite",management.toAbsolutePath().toString());var db=new ManagementDatabase(new ManagementConfig("jdbc:sqlite:"+management.toAbsolutePath()));
  String teacher=db.createUser("portalteacher",null,"Giảng viên Java","TEACHER","Teacher12345!".toCharArray()).identity().userId(),student=db.createUser("portalstudent","SV001","Nguyễn Minh Anh","STUDENT","Student12345!".toCharArray()).identity().userId();db.createUser("portalother","SV002","Trần Hải Nam","STUDENT","Student23456!".toCharArray());
  try(var core=new Server(0,temp.resolve("core.db"));var assignments=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");var web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),token=login(base,"portalstudent","Student12345!"),t=login(base,"portalteacher","Teacher12345!"),other=login(base,"portalother","Student23456!");
   check(request(base,"","/api/student/portal",null).status()==401,"anonymous denied");check(request(base,t,"/api/student/portal",null).status()==403,"teacher denied student portal");
   check(list(portal(base,token,"").get("classes")).isEmpty(),"empty enrollment");
   var made=request(base,t,"/api/admin",mapOf("action","CREATE_CLASS","roomCode","JAVA-STUDENT","name","Lập trình Java cơ bản"));check(made.status()==200,"create class");String room=text(map(made.json().get("data")),"classId","");db.enrollment(teacher,room,student,true);
   check(list(portal(base,token,"").get("classes")).size()==1,"enrolled class visible");check(list(portal(base,other,"").get("classes")).isEmpty(),"foreign class hidden");check(request(base,other,"/api/student/portal?classId="+room,null).status()==403,"foreign class filter denied");check(request(base,token,"/api/student/portal?days=999",null).status()!=200,"invalid range denied");
   check(request(base,t,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-STUDENT")).status()==202,"teacher enters class");Thread.sleep(400);
   check(request(base,token,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-STUDENT")).status()!=200,"waiting session cannot be joined");
   var session=map(request(base,t,"/api/classroom",null).json().get("classroom"));
   var started=request(base,t,"/api/classroom",mapOf("action","START","expectedPhase","WAITING","round",number(session,"round",0)));check(started.status()==200,"start lesson: "+started.json());
   check(Boolean.TRUE.equals(map(list(portal(base,token,"").get("classes")).getFirst()).get("canJoin")),"active lesson join enabled");
   check(request(base,other,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-STUDENT")).status()==403,"unenrolled student cannot request admission");
   var pending=request(base,token,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-STUDENT"));check(pending.status()==202,"admission approval still required");String admission=text(map(pending.json().get("admission")),"requestId","");
   check(request(base,t,"/api/admin",mapOf("action","APPROVE_JOIN","classId",room,"requestId",admission)).status()==200,"teacher approves");
   check(request(base,token,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-STUDENT")).status()==202,"approved student enters");Thread.sleep(400);
   check(request(base,token,"/api/rooms/current",null).status()==200,"student in meeting");
   check(request(base,token,"/api/classroom",mapOf("action","END","expectedPhase","LECTURE","round",number(session,"round",0))).status()==403,"student cannot end lesson");
   check(request(base,token,"/api/rooms/current/command",mapOf("action","TEACHER_REQUEST_EDIT")).status()==403,"student cannot take teacher edit priority");
   String exercise;try(var c=DriverManager.getConnection("jdbc:sqlite:"+learning.toAbsolutePath());var q=c.createStatement();var r=q.executeQuery("SELECT id FROM p3_exercises LIMIT 1")){r.next();exercise=r.getString(1);}
   long now=System.currentTimeMillis();sql(learning,"INSERT INTO p3_assignments(id,exercise_id,room_id,created_by_user_id,scope,created_at,deadline_at,status) VALUES('portal-task',?,?,?,'ROOM',?,0,'OPEN')",exercise,room,teacher,now);sql(learning,"INSERT INTO p3_assignment_recipients VALUES('portal-task',?,'portalstudent','Nguyễn Minh Anh')",student);
   sql(learning,"INSERT INTO p3_question_weeks VALUES('portal-week',1,'Java nhập môn','',?,?,?)",teacher,now,now);sql(learning,"INSERT INTO p3_question_week_items VALUES('portal-week',?,?)",exercise,now);
   for(int i=0;i<24;i++)sql(learning,"INSERT INTO p3_practice_submissions(id,exercise_id,user_id,version,is_run,source_code,submitted_at,status,verdict,score,request_key) VALUES(?,?,?,?,?,'PRIVATE_JAVA',?,'DONE',?,?,?)","portal-sub-"+i,exercise,student,i+1,i==23?1:0,now,i%2==0?"ACCEPTED":"WRONG_ANSWER",i%2==0?100:50,"portal-key-"+i);
   var d=portal(base,token,"");var l=map(d.get("learning"));check(number(l,"accepted",0)==1,"unique AC ignores repeated submissions");check(number(l,"attempts",0)==23,"exclude runs");check(list(l.get("history")).size()==20,"history first page");check(number(map(portal(base,token,"?page=2").get("learning")),"page",0)==2,"history pagination");check(list(map(portal(base,token,"?page=2").get("learning")).get("history")).size()==3,"history second page");check(number(map(l.get("myRank")),"rank",0)==1,"personal rank");check(number(map(l.get("myRank")),"points",0)==100,"best score counted once");check(list(l.get("weeks")).size()==1,"week progress follows assigned exercises");check(!stringify(d).contains("PRIVATE_JAVA"),"summary never exposes source code");check(list(map(portal(base,other,"").get("learning")).get("assignments")).isEmpty(),"private assignments isolated");
   var scoped=map(portal(base,token,"?classId="+room).get("learning"));check(number(scoped,"attempts",-1)==0,"class history excludes unscoped practice");check(list(scoped.get("ranking")).size()==1,"class leaderboard uses enrollment");
   check(request(base,token,"/api/student/submission?id=portal-sub-0",null).status()==200,"own source history available outside classroom");check(request(base,other,"/api/student/submission?id=portal-sub-0",null).status()==403,"foreign source history denied");
   if(args.length>0&&args[0].equals("--browser")){Files.writeString(Path.of("build-student-portal/fixture.json"),stringify(mapOf("base",base,"token",token,"teacherToken",t,"roomId",room,"exerciseId",exercise)));Path stop=Path.of("build-student-portal/stop");Files.deleteIfExists(stop);System.out.println("BROWSER_READY "+base);System.out.flush();long heartbeat=0;while(!Files.exists(stop)){if(System.currentTimeMillis()-heartbeat>10000){request(base,t,"/api/auth/heartbeat",Map.of());request(base,token,"/api/auth/heartbeat",Map.of());heartbeat=System.currentTimeMillis();}Thread.sleep(250);}}
  }
  System.out.println("StudentPortalIntegrationTest PASS: "+assertions+" assertions.");
 }
}
