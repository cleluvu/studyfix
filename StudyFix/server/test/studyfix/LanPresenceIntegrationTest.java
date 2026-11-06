package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** HTTP/TCP thật, DB tạm. Không giả định máy kiểm thử có địa chỉ LAN cụ thể. */
public final class LanPresenceIntegrationTest {
    static List<Map<String,Object>> roster(String base,String token)throws Exception {
        Response r=call(base,"/api/rooms/current/presence",token,null);
        check(r.status()==200,"teacher reads class presence");return rows(r.body().get("connections"));
    }
    static void keepAlive(String base,String token)throws Exception{check(call(base,"/api/auth/heartbeat",token,Map.of()).status()==200,"heartbeat keeps live session");}
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");
        System.setProperty("studyfix.web.sessionTimeoutMillis","2000");
        Path temp=Files.createTempDirectory(Path.of("build"),"lan-presence-");Path db=temp.resolve("core.db");
        String userId=UUID.randomUUID().toString();
        try(Server core=new Server(0,db)){
            // MSSV khác username để chứng minh xác thực thực sự tra mã trong DB.
            core.store().classroomTransaction(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role,student_code) VALUES(?,?,?,?,?,?)")){
                p.setString(1,userId);p.setString(2,"lan-student");p.setString(3,Passwords.hash("LanSecret123!".toCharArray()));p.setString(4,"Sinh viên LAN");p.setString(5,"STUDENT");p.setString(6,"SV2026001");p.executeUpdate();}return null;});
            try(AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
                core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
                check(call(base,"/api/rooms/current/presence",null,null).status()==401,"anonymous cannot list IPs");
                check(call(base,"/api/auth/login",null,mapOf("studentCode","SV2026001","password","wrong")).status()==401,"wrong password rejected");
                String teacher=login(base,"teacher","Teacher123!");
                check(call(base,"/api/rooms/current/presence",teacher,null).status()==409,"teacher cannot read class without membership");
                call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM","roomCode","LAN-test"));String room=room(base,teacher);
                HttpRequest loginRequest=HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).header("Content-Type","application/json").header("X-Forwarded-For","192.168.99.99").POST(HttpRequest.BodyPublishers.ofString(stringify(mapOf("studentCode","SV2026001","password","LanSecret123!")))).build();
                HttpResponse<String> loginResponse=HTTP.send(loginRequest,HttpResponse.BodyHandlers.ofString());
                Response login=new Response(loginResponse.statusCode(),object(loginResponse.body()));
                check(login.status()==200,"student code authenticates against database");String student=text(login.body(),"token","");
                check("lan-student".equals(obj(login.body().get("identity")).get("username"))&&"SV2026001".equals(obj(login.body().get("identity")).get("studentCode")),"canonical identity and student code remain distinct");
                check(!login.body().containsKey("sessionId")&&!login.body().containsKey("coreSessionId"),"login never returns internal session ID");
                join(base,student,"LAN-test");
                check(call(base,"/api/rooms/current/presence",student,null).status()==403,"student cannot list IPs");
                Map<String,Object> studentRoom=obj(call(base,"/api/rooms/current",student,null).body().get("room"));
                check(!studentRoom.containsKey("studentConnections")&&!studentRoom.toString().contains("loginTime"),"regular room response does not leak management data");
                Map<String,Object> first=roster(base,teacher).getFirst();
                check(first.get("userId").equals(userId)&&first.get("studentCode").equals("SV2026001")&&first.get("displayName").equals("Sinh viên LAN"),"teacher sees database identity");
                check(first.get("ip").equals("127.0.0.1")&&Boolean.TRUE.equals(first.get("online")),"IP taken from actual HTTP connection");
                check(number(first,"loginTime",0)>0&&number(first,"lastActivity",0)>=number(first,"loginTime",0),"login/activity timestamps recorded");
                String session=core.clients.values().stream().filter(c->c.identity()!=null&&c.identity().userId().equals(userId)).findFirst().orElseThrow().getMemberId();
                check(!stringify(first).contains(session)&&!stringify(first).contains(student),"teacher gets neither auth token nor internal Session ID");
                check(!stringify(call(base,"/api/events?after=0",student,null).body()).contains(session),"event stream does not expose internal Session ID");
                String otherTeacher=login(base,"teacher2","Teacher234!");
                call(base,"/api/rooms/current/command",otherTeacher,mapOf("action","CREATE_ROOM","roomCode","Other-class"));
                check(roster(base,otherTeacher).isEmpty(),"teacher in another class cannot see this class IPs");
                call(base,"/api/auth/logout",otherTeacher,Map.of());
                // Headers do not replace the real peer unless trusted proxy support is configured.
                HttpRequest spoof=HttpRequest.newBuilder(URI.create(base+"/api/auth/heartbeat")).header("Authorization","Bearer "+student).header("X-Forwarded-For","192.168.99.99").header("Forwarded","for=192.168.99.98").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
                check(HTTP.send(spoof,HttpResponse.BodyHandlers.ofString()).statusCode()==200,"heartbeat accepts authenticated request");
                check(roster(base,teacher).getFirst().get("ip").equals("127.0.0.1"),"untrusted proxy headers ignored");
                String second=login(base,"SV2026001","LanSecret123!");join(base,second,"LAN-test");
                List<Map<String,Object>> two=roster(base,teacher);
                check(two.size()==2&&two.stream().filter(r->Boolean.TRUE.equals(r.get("online"))).count()==2,"same user and IP can have two online devices");
                check(!two.get(0).get("connectionId").equals(two.get(1).get("connectionId")),"each login receives independent connection ID");
                keepAlive(base,teacher);keepAlive(base,student);keepAlive(base,second);
                call(base,"/api/auth/logout",student,Map.of());
                check(call(base,"/api/me",student,null).status()==401,"logout revokes only logged-out token");
                List<Map<String,Object>> after=roster(base,teacher);
                check(after.stream().filter(r->Boolean.TRUE.equals(r.get("online"))).count()==1&&after.stream().anyMatch(r->"LOGOUT".equals(r.get("reason"))),"one logout does not disconnect another device");
                // Student stops sending requests: teacher polling must not keep student alive.
                long end=System.currentTimeMillis()+3200;
                while(System.currentTimeMillis()<end){keepAlive(base,teacher);Thread.sleep(350);}
                check(roster(base,teacher).stream().noneMatch(r->Boolean.TRUE.equals(r.get("online"))),"missing student heartbeat becomes offline while teacher stays online");
                check(call(base,"/api/auth/heartbeat",second,Map.of()).status()==401,"expired token cannot resurrect a session");
                String reconnect=login(base,"SV2026001","LanSecret123!");join(base,reconnect,"LAN-test");
                List<Map<String,Object>> rejoined=roster(base,teacher);
                check(rejoined.size()==3&&rejoined.stream().filter(r->Boolean.TRUE.equals(r.get("online"))).count()==1,"reconnect creates fresh session and preserves offline history");
                call(base,"/api/rooms/current/command",reconnect,mapOf("action","LEAVE_ROOM"));
                check(roster(base,teacher).stream().noneMatch(r->Boolean.TRUE.equals(r.get("online"))),"leave membership marks connection offline for this class");
                check(call(base,"/api/me",reconnect,null).status()==200,"leaving class keeps login session alive");
                // Keep class membership while only closing the meeting UI: no server leave command.
                join(base,reconnect,"LAN-test");keepAlive(base,reconnect);
                check(roster(base,teacher).stream().anyMatch(r->Boolean.TRUE.equals(r.get("online"))),"membership restores independently of meeting media");
                ClientHandler reconnectCore=core.clients.values().stream().filter(c->c.identity()!=null&&c.identity().userId().equals(userId)).findFirst().orElseThrow();
                reconnectCore.closeConnection();
                waitFor(()->roster(base,teacher).stream().noneMatch(r->Boolean.TRUE.equals(r.get("online"))),"core TCP EOF updates persisted Offline");
                check(call(base,"/api/me",reconnect,null).status()==401,"closed core session cannot authenticate with old token");
            }
        }
        try(Store restarted=new Store(db)){
            PresenceRepository repository=new PresenceRepository(restarted);
            repository.login("crash-connection","private-crash-session",new Identity(userId,"lan-student","Sinh viên LAN",Identity.Role.STUDENT),"127.0.0.1",System.currentTimeMillis());
            repository.observe("crash-connection","1","127.0.0.1",System.currentTimeMillis());
            repository.recover();
            check(repository.students("1").stream().noneMatch(r->Boolean.TRUE.equals(r.get("online"))),"server restart never restores stale Online status");
            check(repository.students("1").stream().anyMatch(r->"crash-connection".equals(r.get("connectionId"))&&"SERVER_RESTART".equals(r.get("reason"))),"unclean stop recovered to Offline with reason");
            restarted.classroomTransaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT password_hash FROM users WHERE user_id=?")){p.setString(1,userId);try(ResultSet r=p.executeQuery()){check(r.next()&&!r.getString(1).contains("LanSecret")&&Passwords.verify("LanSecret123!".toCharArray(),r.getString(1)),"password remains salted PBKDF2 hash");}}return null;});
        }
        System.out.println("LanPresenceIntegrationTest: "+checks+" checks PASS (HTTP/TCP/SQLite, separate sessions, timeout, IP privacy).");
    }
}
