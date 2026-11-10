package studyfix;

import java.net.http.WebSocket;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.MySqlAdminIntegrationTest.admin;
import static studyfix.MySqlAdminIntegrationTest.joinApproved;
import static studyfix.assignment.protocol.AssignmentJson.*;

final class TeacherScopeChecks {
    static void verify(Server core,WebGatewayServer web,MySqlManagement sql,String base,String teacher,String student,String classId,String classCode,String studentId,String studentCode,String password,String suffix,Map<String,Object> connection)throws Exception{
        String teacherName="teacher_b_"+suffix,bSecret="B8_"+UUID.randomUUID();
        sql.createUser(teacherName,null,"Giảng viên B","TEACHER",bSecret.toCharArray()); // fixture only; never a Teacher API action.
        String teacherB=login(base,teacherName,bSecret);
        String classB=text(obj(admin(base,teacherB,mapOf("action","CREATE_CLASS","roomCode","B-"+suffix)).body().get("data")),"classId","");
        String bCode="B_SV_"+suffix;
        Response created=admin(base,teacherB,mapOf("action","CREATE_USER","username","b_user_"+suffix,"studentCode",bCode,"displayName","Sinh viên riêng B","password",bSecret));
        String bUser=text(obj(created.body().get("data")),"userId","");
        admin(base,teacherB,mapOf("action","ENROLL","classId",classB,"userId",bUser));
        String studentB=login(base,bCode,bSecret);join(base,teacherB,"B-"+suffix);joinApproved(base,teacherB,studentB,"B-"+suffix);
        check(admin(base,teacher,mapOf("action","CREATE_USER","username","forbidden_teacher","role","TEACHER")).status()==403,"Teacher cannot create another Teacher");
        check(call(base,"/api/admin?classId="+classB,teacher,null).status()==403,"Teacher A cannot read B class members");
        for(String action:List.of("ENROLL","REMOVE_MEMBER","DELETE_CLASS","UPDATE_CLASS","DISCONNECT_SESSION")){
            Map<String,Object> body=mapOf("action",action,"classId",classB,"userId",bUser,"name","Tamper","closed",false,"connectionId",connection.get("connectionId"));
            check(admin(base,teacher,body).status()==403,"Owner guard for "+action);
        }
        String teacherBId=sql.authenticate(teacherName,bSecret.toCharArray()).identity().userId();
        check(admin(base,teacher,mapOf("action","SET_ACTIVE","userId",teacherBId,"active",false)).status()==403,"Teacher cannot disable another Teacher");
        check(admin(base,teacher,mapOf("action","RESET_PASSWORD","userId",bUser,"password","R9_"+UUID.randomUUID())).status()==403,"Teacher cannot reset another teacher's student password");
        String ownDashboard=stringify(obj(admin(base,teacher,null).body().get("data")));
        check(!ownDashboard.contains(bCode)&&!ownDashboard.contains(teacherName)&&!ownDashboard.contains("B-"+suffix),"HTTP dashboard hides B identity, class and LAN activity");
        String second=login(base,studentCode,password);joinApproved(base,teacher,second,classCode);
        check(rows(call(base,"/api/rooms/current/presence",teacher,null).body().get("connections")).stream().filter(r->Boolean.TRUE.equals(r.get("online"))&&studentId.equals(r.get("userId"))).count()==2,"MySQL keeps two devices independent for one account");
        call(base,"/api/auth/logout",second,Map.of());
        check(call(base,"/api/me",student,null).status()==200,"logging out second device does not revoke first device");
        WebSocketPresenceIntegrationTest.Listener listener=new WebSocketPresenceIntegrationTest.Listener();WebSocket socket=WebSocketPresenceIntegrationTest.connect(web,teacher,listener);listener.next("AUTH_OK");socket.sendText("{\"type\":\"SUBSCRIBE\",\"view\":\"admin\"}",true).join();
        String snapshot=stringify(listener.next("ADMIN_UPDATE"));check(!snapshot.contains(bCode)&&!snapshot.contains("B-"+suffix),"WebSocket admin snapshot has the same owner scope");socket.sendClose(1000,"done").join();
        check(admin(base,teacherB,mapOf("action","ENROLL_CODE","classId",classB,"studentCode",studentCode)).status()==200,"student may legitimately belong to two teachers' classes");
        check(admin(base,teacher,mapOf("action","SET_ACTIVE","userId",studentId,"active",false)).status()==403,"shared student cannot be globally disabled by one teacher");
        check(admin(base,teacherB,mapOf("action","DISCONNECT_SESSION","classId",classB,"connectionId",connection.get("connectionId"))).status()==403,"class B cannot disconnect a session currently in A");
        check(admin(base,teacher,mapOf("action","DISCONNECT_SESSION","classId",classId,"connectionId",connection.get("connectionId"))).status()==200,"owner disconnects a single class session by public tracking ID");
        check(call(base,"/api/me",student,null).status()==200&&call(base,"/api/rooms/current",student,null).status()==409,"class disconnect preserves system login");
        String owner=sql.authenticate(System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"),System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD").toCharArray()).identity().userId();
        // Simulate a changed address in another context; no forged HTTP header is trusted.
        sql.transaction(c->{try(var p=c.prepareStatement("UPDATE sessions SET ip='192.168.99.5' WHERE connection_id=?")){p.setString(1,text(connection,"connectionId",""));p.executeUpdate();}return null;});
        Map<String,Object> scoped=rows(sql.dashboard(owner,"").get("connections")).stream().filter(r->r.get("connectionId").equals(connection.get("connectionId"))).findFirst().orElseThrow();
        check(scoped.get("ip").equals("127.0.0.1"),"class A history does not leak a later session IP from another context");
        sql.transaction(c->{try(var p=c.prepareStatement("UPDATE sessions SET ip='127.0.0.1' WHERE connection_id=?")){p.setString(1,text(connection,"connectionId",""));p.executeUpdate();}return null;});
        joinApproved(base,teacher,student,classCode);
        List<Map<String,Object>> attendance=rows(obj(admin(base,teacher,null).body().get("data")).get("attendance"));
        check(attendance.stream().filter(row->row.get("userId").equals(studentId)&&row.get("connectionId").equals(connection.get("connectionId"))).count()>=2,"rejoining preserves previous attendance visit");
        check(attendance.stream().anyMatch(row->row.get("userId").equals(studentId)&&row.get("leftAt")!=null),"closed visit has a departure time");
        admin(base,teacher,mapOf("action","REMOVE_MEMBER","classId",classId,"userId",studentId));
        check(rows(obj(admin(base,teacher,null).body().get("data")).get("users")).stream().noneMatch(r->studentId.equals(r.get("userId"))),"creator no longer sees current account data after student only belongs to B");
        admin(base,teacher,mapOf("action","ENROLL_CODE","classId",classId,"studentCode",studentCode));joinApproved(base,teacher,student,classCode);
        admin(base,teacherB,mapOf("action","REMOVE_MEMBER","classId",classB,"userId",studentId));
        check(admin(base,teacher,mapOf("action","UPDATE_CLASS","classId",classId,"name","Lớp đã cập nhật","closed",true)).status()==200,"owner closes and renames class without deleting code");
        check(call(base,"/api/rooms/current",student,null).status()==409,"closing class detaches participants");
        check(rows(call(base,"/api/rooms",student,null).body().get("rooms")).stream().noneMatch(r->classId.equals(r.get("roomId"))),"student discovery excludes closed classes");
        admin(base,teacher,mapOf("action","UPDATE_CLASS","classId",classId,"name","Lớp đã cập nhật","closed",false));join(base,teacher,classCode);joinApproved(base,teacher,student,classCode);
        check(rows(call(base,"/api/rooms",student,null).body().get("rooms")).stream().anyMatch(r->classId.equals(r.get("roomId"))),"student discovers the reopened class");
        check(core.rooms.get(classId).snapshot().ownerId().equals(sql.authenticate(System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"),System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD").toCharArray()).identity().userId()),"class owner identity remains stable");
    }
}
