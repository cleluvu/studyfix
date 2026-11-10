package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Isolated real HTTP/SQLite tests; never writes to production accounts or databases. */
public final class TeacherDashboardIntegrationTest {
 static final HttpClient http=HttpClient.newHttpClient();static int assertions;
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);assertions++;}
 record Reply(int status,Map<String,Object> json){}
 static Reply request(String base,String token,String path,Map<String,Object> body)throws Exception{
  var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(30));if(!token.isBlank())b.header("Authorization","Bearer "+token);if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(stringify(body)));
  var response=http.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(response.statusCode(),object(response.body()));
 }
 static String login(String base,String user,String password)throws Exception{var r=request(base,"","/api/auth/login",mapOf("username",user,"password",password));check(r.status==200,"login "+user+": "+r.json);return text(r.json,"token","");}
 static Map<String,Object> get(String base,String token,String query)throws Exception{var r=request(base,token,"/api/teacher/dashboard"+query,null);check(r.status==200,"dashboard: "+r.json);return map(r.json.get("data"));}
 static void sql(Path db,String sql,Object... params)throws Exception{try(var c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());var p=c.prepareStatement(sql)){for(int i=0;i<params.length;i++)p.setObject(i+1,params[i]);p.executeUpdate();}}
 public static void main(String[] args)throws Exception{
  System.clearProperty("studyfix.legacy.sqlite");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");System.setProperty("studyfix.web.root",Path.of("../web-client/static").toAbsolutePath().toString());
  Path temp=Files.createTempDirectory(Path.of("build-teacher-dashboard"),"integration-"),management=temp.resolve("management.db"),learning=temp.resolve("learning.db");System.setProperty("studyfix.management.sqlite",management.toAbsolutePath().toString());var db=new ManagementDatabase(new ManagementConfig("jdbc:sqlite:"+management.toAbsolutePath()));
  String teacher=db.createUser("dashboardteacher",null,"Giảng viên kiểm thử","TEACHER","Teacher12345!".toCharArray()).identity().userId();db.createUser("otherteacher",null,"Giảng viên khác","TEACHER","Teacher23456!".toCharArray());String student=db.createUser("dashboardstudent","SV001","Nguyễn Minh Anh","STUDENT","Student12345!".toCharArray()).identity().userId();
  try(var core=new Server(0,temp.resolve("core.db"));var assignments=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");var web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),token=login(base,"dashboardteacher","Teacher12345!"),other=login(base,"otherteacher","Teacher23456!"),studentToken=login(base,"dashboardstudent","Student12345!");
   check(request(base,"","/api/teacher/dashboard",null).status==401,"anonymous denied");check(request(base,studentToken,"/api/teacher/dashboard",null).status==403,"student denied");
   check(number(map(get(base,token,"").get("stats")),"classes",-1)==0,"empty dashboard supported");
   check(request(base,token,"/api/admin",mapOf("action","CREATE_CLASS","roomCode","JAVA-K23")).status==200,"create first class");check(request(base,token,"/api/admin",mapOf("action","CREATE_CLASS","roomCode","OOP-K23")).status==200,"create second class");
   var d=get(base,token,"");var classes=list(d.get("classes"));String room=text(map(classes.getFirst()),"classId","");String room2=text(map(classes.getLast()),"classId","");db.enrollment(teacher,room,student,true);db.enrollment(teacher,room2,student,true);
   String exercise;try(var c=DriverManager.getConnection("jdbc:sqlite:"+learning.toAbsolutePath());var q=c.createStatement();var r=q.executeQuery("SELECT id FROM p3_exercises LIMIT 1")){r.next();exercise=r.getString(1);}
   long now=System.currentTimeMillis();sql(learning,"INSERT INTO p3_assignments(id,exercise_id,room_id,created_by_user_id,scope,created_at,deadline_at,status) VALUES('fixture-assignment',?,?,?,'ROOM',?,0,'OPEN')",exercise,room,teacher,now);
   sql(learning,"INSERT INTO p3_assignment_recipient_snapshots VALUES('fixture-assignment',?)",now);sql(learning,"INSERT INTO p3_assignment_recipients VALUES('fixture-assignment',?,'dashboardstudent','Nguyễn Minh Anh')",student);
   sql(learning,"INSERT INTO p3_submissions(id,assignment_id,room_id,user_id,username,source_code,submitted_at,status,verdict,idempotency_key) VALUES('fixture-submission','fixture-assignment',?,?,'dashboardstudent','SECRET_SOURCE',?,'DONE','AC','fixture-key')",room,student,now);
   for(int i=0;i<25;i++)sql(learning,"INSERT INTO p3_practice_submissions(id,exercise_id,user_id,version,is_run,source_code,submitted_at,status,verdict,request_key) VALUES(?,?,?,?,?,'SECRET_SOURCE',?,'DONE',?,?)","practice-"+i,exercise,student,i+1,i==24?1:0,now,i%2==0?"AC":"WA","practice-key-"+i);
   sql(learning,"INSERT INTO p3_question_weeks VALUES('fixture-week',1,'Mảng một chiều','',?,?,?)",teacher,now,now);sql(learning,"INSERT INTO p3_question_week_items VALUES('fixture-week',?,?)",exercise,now);
   d=get(base,token,"");var l=map(d.get("learning"));check(number(map(d.get("stats")),"students",0)==1,"deduplicate student in two classes");check(number(map(d.get("stats")),"assignments",0)==1,"assignment total");check(number(l,"total",0)==25,"exclude practice runs");check(list(l.get("submissions")).size()==20,"bounded submission pagination");check(number(l,"totalPages",0)==2,"pagination count");check(number(map(list(l.get("daily")).getFirst()),"submissions",0)==25,"daily equals received submissions");check(number(map(list(l.get("weekProgress")).getFirst()),"percent",0)==100,"week completion uses recipient snapshot");check(!stringify(d).contains("SECRET_SOURCE"),"never exposes code or hidden tests");
   sql(learning,"INSERT INTO p3_assignments(id,exercise_id,room_id,created_by_user_id,scope,created_at,deadline_at,status) VALUES('repeat-assignment',?,?,?,'ROOM',?,0,'OPEN')",exercise,room,teacher,now);
   sql(learning,"INSERT INTO p3_assignment_recipients VALUES('repeat-assignment',?,'dashboardstudent','Nguyễn Minh Anh')",student);
   check(number(map(list(map(get(base,token,"").get("learning")).get("weekProgress")).getFirst()),"total",0)==1,"repeated assignment does not inflate week progress");
   var second=map(get(base,token,"?page=2").get("learning"));check(list(second.get("submissions")).size()==5,"second page");var ac=map(get(base,token,"?verdict=AC").get("learning"));check(number(ac,"total",0)==13,"verdict filter");check(number(map(get(base,other,"").get("learning")),"total",-1)==0,"other teacher sees no student submissions");check(request(base,other,"/api/teacher/dashboard?classId="+room,null).status==403,"foreign class forbidden");check(request(base,token,"/api/teacher/dashboard?days=999",null).status!=200,"invalid range rejected");
   check(request(base,token,"/api/teacher/dashboard",mapOf("action","SETTINGS","days",7,"compact",true,"notifications",false)).status==200,"settings saved");check(number(map(get(base,token,"").get("settings")),"days",0)==7,"settings persisted");check(number(map(get(base,other,"").get("settings")),"days",0)==30,"preferences isolated per user");
   check(request(base,studentToken,"/api/teacher/dashboard",mapOf("action","SETTINGS","days",7)).status==403,"student cannot write preferences");check(request(base,token,"/api/teacher/dashboard",mapOf("action","READ","through",now)).status==200,"read marker saved");check(number(get(base,token,""),"readAt",0)==now,"read marker persisted");
   if(args.length>0&&args[0].equals("--browser")){request(base,token,"/api/teacher/dashboard",mapOf("action","SETTINGS","days",30,"compact",false,"notifications",true));Files.writeString(Path.of("build-teacher-dashboard/fixture.json"),stringify(mapOf("base",base,"token",token,"roomId",room,"directory",temp.toAbsolutePath().toString())));System.out.println("BROWSER_READY "+base);System.out.flush();System.in.read();}
  }
  System.out.println("TeacherDashboardIntegrationTest PASS: "+assertions+" assertions.");
 }
}
