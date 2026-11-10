package studyfix;

import java.nio.file.*;
import java.util.*;
import studyfix.assignment.transport.AssignmentTcpServer;
import static studyfix.assignment.protocol.AssignmentJson.*;
import static studyfix.TeacherDashboardIntegrationTest.request;
import static studyfix.TeacherDashboardIntegrationTest.login;
import static studyfix.TeacherDashboardIntegrationTest.sql;

public final class ClassDirectoryIntegrationTest {
 static int assertions;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);assertions++;}
 static Map<String,Object> create(String base,String token,String code,String name,String description)throws Exception{
  var r=request(base,token,"/api/admin",mapOf("action","CREATE_CLASS","roomCode",code,"name",name,"description",description));check(r.status()==200,"create class: "+r.json());return map(r.json().get("data"));
 }
 static Map<String,Object> row(String base,String token,String id)throws Exception{
  var r=request(base,token,"/api/admin",null);check(r.status()==200,"read classes");return list(map(r.json().get("data")).get("classes")).stream().map(v->map(v)).filter(c->id.equals(c.get("classId"))).findFirst().orElseThrow();
 }
 public static void main(String[] args)throws Exception{
  System.clearProperty("studyfix.legacy.sqlite");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");System.setProperty("studyfix.web.root",Path.of("../web-client/static").toAbsolutePath().toString());
  Files.createDirectories(Path.of("build-class-directory"));Path temp=Files.createTempDirectory(Path.of("build-class-directory"),"integration-"),management=temp.resolve("management.db"),learning=temp.resolve("learning.db"),coreDb=temp.resolve("core.db");System.setProperty("studyfix.management.sqlite",management.toAbsolutePath().toString());
  var db=new ManagementDatabase(new ManagementConfig("jdbc:sqlite:"+management.toAbsolutePath()));String teacher=db.createUser("classteacher",null,"Giảng viên lớp học","TEACHER","Teacher12345!".toCharArray()).identity().userId();db.createUser("otherteacher",null,"Giảng viên khác","TEACHER","Teacher23456!".toCharArray());String student=db.createUser("classstudent","SV001","Nguyễn Minh Anh","STUDENT","Student12345!".toCharArray()).identity().userId();String persistentId;
  try(var core=new Server(0,coreDb);var assignments=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");var web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),token=login(base,"classteacher","Teacher12345!"),other=login(base,"otherteacher","Teacher23456!");
   var first=create(base,token,"JAVA-K23","Lập trình Java (K23)","Học Java cơ bản");String id=text(first,"classId","");persistentId=id;
   var c=row(base,token,id);check("Lập trình Java (K23)".equals(c.get("name")),"name stored independently of room code");check("Học Java cơ bản".equals(c.get("description")),"description persisted");check(!Boolean.TRUE.equals(c.get("closed")),"new class open");check(number(c,"onlineStudents",-1)==0,"zero online student count");
   var generated=create(base,token,"","Lớp tự cấp mã","");check(text(generated,"roomCode","").startsWith("LOP-"),"server generates join code");
   check(request(base,other,"/api/admin",mapOf("action","UPDATE_CLASS","classId",id,"name","Hacked","closed",false)).status()==403,"foreign metadata update denied");check(request(base,other,"/api/admin",mapOf("action","DELETE_CLASS","classId",id)).status()==403,"foreign deletion denied");
   check(request(base,token,"/api/admin",mapOf("action","CREATE_CLASS","name"," ","roomCode","INVALID")).status()==400,"blank name rejected");check(request(base,token,"/api/admin",mapOf("action","CREATE_CLASS","name","Invalid","description","a".repeat(2001))).status()==400,"long description rejected before creating room");check(request(base,token,"/api/admin",mapOf("action","CREATE_CLASS","name","Duplicate","roomCode","JAVA-K23")).status()!=200,"duplicate room code rejected");
   check(request(base,token,"/api/admin",mapOf("action","UPDATE_CLASS","classId",id,"name","Tên đã sửa","description","Mô tả đã sửa","closed",false)).status()==200,"metadata update");check("Mô tả đã sửa".equals(row(base,token,id).get("description")),"description reflected in directory");
   check(request(base,token,"/api/admin",mapOf("action","UPDATE_CLASS","classId",id,"name","Tên đã sửa","closed",false)).status()==200,"legacy update remains compatible");check("Mô tả đã sửa".equals(row(base,token,id).get("description")),"omitted description preserves previous text");
   db.enrollment(teacher,id,student,true);check(number(row(base,token,id),"memberCount",0)==1,"student enrollment count");
   String learner=login(base,"classstudent","Student12345!");
   check(request(base,token,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-K23")).status()==202,"teacher enters before opening lesson");Thread.sleep(400);
   check(request(base,token,"/api/classroom",mapOf("action","START","expectedPhase","WAITING","round",0)).status()==200,"teacher opens valid lesson before admission");
   check(request(base,learner,"/api/admin",mapOf("action","CREATE_CLASS","name","Forbidden")).status()==403,"student cannot create class");
   var admission=request(base,learner,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-K23"));check(admission.status()==202,"student requests admission");
   check(request(base,token,"/api/admin",mapOf("action","APPROVE_JOIN","classId",id,"requestId",text(map(admission.json().get("admission")),"requestId",""))).status()==200,"teacher approves classroom admission");
   check(request(base,learner,"/api/rooms/current/command",mapOf("action","JOIN_ROOM","roomId","JAVA-K23")).status()==202,"approved student joins realtime room");
   long online=0;for(int attempt=0;attempt<15;attempt++){online=number(row(base,token,id),"onlineStudents",0);if(online==1)break;Thread.sleep(50);}check(online==1,"online student count reflects live membership");
   long now=System.currentTimeMillis();sql(coreDb,"INSERT OR REPLACE INTO classroom_sessions(room_id,phase,round,exercise_id,base_version,updated_at) VALUES(?,'LECTURE',0,'',0,?)",id,now);
   check(Boolean.TRUE.equals(row(base,token,id).get("deleteBlocked")),"active lesson disables deletion in server data");check(request(base,token,"/api/admin",mapOf("action","DELETE_CLASS","classId",id)).status()==409,"active lesson deletion blocked");check(request(base,token,"/api/rooms/current/command",mapOf("action","DELETE_ROOM","roomId",id)).status()==409,"alternate deletion route cannot bypass guard");check(core.rooms.containsKey(id),"rejected deletion preserves room");
   check(request(base,token,"/api/admin",mapOf("action","UPDATE_CLASS","classId",id,"name","Tên đã sửa","closed",true)).status()==200,"close active class without deleting history");check(Boolean.TRUE.equals(row(base,token,id).get("closed")),"closed state from server");check(!db.classOpen(id),"closed class join denied");check(number(row(base,token,id),"onlineStudents",-1)==0,"closing class disconnects online students");
   check(request(base,token,"/api/admin",mapOf("action","UPDATE_CLASS","classId",id,"name","Tên đã sửa","closed",false)).status()==200,"reopen class");
   sql(coreDb,"UPDATE classroom_sessions SET phase='ENDED' WHERE room_id=?",id);check(!Boolean.TRUE.equals(row(base,token,id).get("deleteBlocked")),"ended lesson deletion allowed");
   String disposable=text(generated,"classId","");check(request(base,token,"/api/admin",mapOf("action","DELETE_CLASS","classId",disposable)).status()==200,"idle class deleted");check(!Boolean.TRUE.equals(row(base,token,disposable).get("active")),"deleted class no longer active");check(core.findRoom(text(generated,"roomCode",""))==null,"deleted join code unavailable");
   for(int i=1;i<=11;i++)create(base,token,"CLASS-"+i,"Lớp thực hành "+i,"Mô tả lớp "+i);
   if(args.length>0&&args[0].equals("--browser")){sql(coreDb,"UPDATE classroom_sessions SET phase='LECTURE' WHERE room_id=?",id);Files.writeString(Path.of("build-class-directory/fixture.json"),stringify(mapOf("base",base,"directory",temp.toAbsolutePath().toString(),"classId",id)));System.out.println("BROWSER_READY "+base);System.out.flush();System.in.read();}
  }
  var restarted=new ManagementDatabase(new ManagementConfig("jdbc:sqlite:"+management.toAbsolutePath()));var persisted=restarted.dashboard(teacher,"");check(list(persisted.get("classes")).stream().map(v->map(v)).anyMatch(c->persistentId.equals(c.get("classId"))&&"Mô tả đã sửa".equals(c.get("description"))),"metadata survives restart and idempotent migration");
  System.out.println("ClassDirectoryIntegrationTest PASS: "+assertions+" assertions.");
 }
}
