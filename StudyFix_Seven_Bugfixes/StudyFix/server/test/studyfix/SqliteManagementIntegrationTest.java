package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Full SQLite management path: no legacy bypass, no external database, real HTTP/core/presence. */
public final class SqliteManagementIntegrationTest {
 public static void main(String[] args)throws Exception{
  System.clearProperty("studyfix.legacy.sqlite");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"sqlite-management-"),management=temp.resolve("management.db");System.setProperty("studyfix.management.sqlite",management.toAbsolutePath().toString());
  ManagementDatabase db=new ManagementDatabase(new ManagementConfig("jdbc:sqlite:"+management.toAbsolutePath()));
  db.createUser("teacher",null,"Teacher A","TEACHER","Teacher12345!".toCharArray());db.createUser("teacher2",null,"Teacher B","TEACHER","Teacher23456!".toCharArray());
  try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,16,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher12345!"),foreign=login(base,"teacher2","Teacher23456!");
   check("sqlite".equals(call(base,"/api/health","",null).body().get("managementDatabase")),"health reports actual SQLite management engine");
   db.transaction(c->{try(var s=c.createStatement()){s.execute("CREATE TRIGGER reject_test_user BEFORE INSERT ON users WHEN NEW.username='constraint_student' BEGIN SELECT RAISE(ABORT,'test SQLite constraint'); END");}return null;});
   try{
    var conflict=call(base,"/api/admin",teacher,mapOf("action","CREATE_USER","username","constraint_student","displayName","Constraint test","password","Student12345!","role","STUDENT"));
    check(conflict.status()==409&&"DATA_CONFLICT".equals(conflict.body().get("error")),"SQLite constraint failure is HTTP 409, not database unavailable");
    check(db.accounts().stream().noneMatch(a->a.identity().username().equals("constraint_student")),"failed SQLite write rolls back account creation");
   }finally{db.transaction(c->{try(var s=c.createStatement()){s.execute("DROP TRIGGER reject_test_user");}return null;});}
   check(call(base,"/api/admin",teacher,mapOf("action","CREATE_USER","username","student","displayName","Student A","password","Student12345!","role","STUDENT")).status()==200,"SQLite Admin creates student with hashed password");String student=login(base,"student","Student12345!");
   check(call(base,"/api/admin",student,null).status()==403,"Student cannot read Admin/IP data");
   var created=call(base,"/api/admin",teacher,mapOf("action","CREATE_CLASS","roomCode","SQLITE-LAN"));check(created.status()==200,"SQLite creates class");String room=text(obj(created.body().get("data")),"classId","");join(base,teacher,"SQLITE-LAN");
   String enrolledUser=db.accounts().stream().filter(a->a.identity().username().equals("student")).findFirst().orElseThrow().identity().userId();
   check(call(base,"/api/admin",teacher,mapOf("action","ENROLL","classId",room,"userId",enrolledUser)).status()==200,"student assigned to class before admission");
   check(call(base,"/api/classroom",teacher,mapOf("action","START","expectedPhase","WAITING","round",0)).status()==200,"teacher opens lesson before admission");
   var requested=call(base,"/api/rooms/current/command",student,mapOf("action","JOIN_ROOM","roomId","SQLITE-LAN"));check(requested.status()==202,"SQLite Student requires admission");String request=text(obj(requested.body().get("admission")),"requestId","");
   check(call(base,"/api/admin",foreign,mapOf("action","APPROVE_JOIN","classId",room,"requestId",request)).status()==403,"foreign Teacher cannot approve");
   check(call(base,"/api/admin",teacher,mapOf("action","APPROVE_JOIN","classId",room,"requestId",request)).status()==200,"owner approves SQLite admission");join(base,student,"SQLITE-LAN");
   var view=obj(call(base,"/api/admin?classId="+room,teacher,null).body().get("data"));check("sqlite".equals(view.get("database")),"Admin identifies SQLite database");check(!list(view.get("connections")).isEmpty(),"SQLite attendance and IP recorded");
   check(call(base,"/api/rooms/current/command",student,mapOf("action","LEAVE_ROOM")).status()==200,"student leaves class");check(call(base,"/api/rooms/current/command",student,mapOf("action","JOIN_ROOM","roomId","SQLITE-LAN")).status()==202,"reentry requires new approval");
   String user=db.accounts().stream().filter(a->a.identity().username().equals("student")).findFirst().orElseThrow().identity().userId();
   check(call(base,"/api/admin",teacher,mapOf("action","SET_ACTIVE","userId",user,"active",false)).status()==200,"SQLite account lock");check(call(base,"/api/auth/login","",mapOf("username","student","password","Student12345!")).status()!=200,"locked account cannot login");
   check(call(base,"/api/admin",teacher,mapOf("action","SET_ACTIVE","userId",user,"active",true)).status()==200,"SQLite account unlock");check(call(base,"/api/admin",teacher,mapOf("action","RESET_PASSWORD","userId",user,"password","ResetStudent123!")).status()==200,"SQLite resets password");login(base,"student","ResetStudent123!");
   check(call(base,"/api/admin",teacher,mapOf("action","UPDATE_CLASS","classId",room,"name","SQLite class","closed",true)).status()==200,"SQLite closes class");
  }
  db.recover();check(db.accounts().size()==3,"SQLite accounts persist without external database");System.out.println("SqliteManagementIntegrationTest PASS: real HTTP, login, permissions, class admission, IP/attendance, account lock/reset and persistence.");
 }
}
