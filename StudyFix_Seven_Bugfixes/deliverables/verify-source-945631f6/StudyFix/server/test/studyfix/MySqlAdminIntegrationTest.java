package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Chỉ chạy với database tên kết thúc _test và credential do script cấp. */
public final class MySqlAdminIntegrationTest {
    static Response admin(String base,String token,Map<String,Object> body)throws Exception{return call(base,"/api/admin",token,body);}
    static void joinApproved(String base,String teacher,String student,String room)throws Exception{
        Response requested=call(base,"/api/rooms/current/command",student,mapOf("action","JOIN_ROOM","roomId",room));
        if(requested.status()==202){Map<String,Object> row=obj(requested.body().get("admission"));
            Response approved=admin(base,teacher,mapOf("action","APPROVE_JOIN","classId",row.get("classId"),"requestId",row.get("requestId")));
            if(approved.status()!=200)throw new AssertionError("Test approval failed: "+approved.status());
        }
        join(base,student,room);
    }
    public static void main(String[] args)throws Exception{
        ManagementConfig cfg=ManagementConfig.environment();if(cfg==null){System.out.println("SKIP MySQL: cần STUDYFIX_MANAGEMENT_DB=mysql và database kiểm thử riêng.");return;}
        if(!java.net.URI.create(cfg.url.substring(5)).getPath().endsWith("_test"))throw new IllegalArgumentException("Từ chối test trên database không kết thúc _test, chưa kết nối/migrate.");
        MySqlMigrationChecks.prepareIfEmpty(cfg);
        MySqlManagement sql=new MySqlManagement(cfg);sql.transaction(c->{String name=c.getCatalog();if(name==null||!name.endsWith("_test"))throw new IllegalArgumentException("Từ chối chạy trên DB không có hậu tố _test.");return null;});
        MySqlMigrationChecks.verify(sql);
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");
        Path temp=Files.createTempDirectory(Path.of("build"),"mysql-admin-");
        String sqliteConnection=UUID.randomUUID().toString();String[] savedClass={""};boolean importSqlite="true".equalsIgnoreCase(System.getenv("STUDYFIX_MYSQL_IMPORT_SQLITE"));
        try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            String teacher=login(base,System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"),System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD"));
            try{sql.bindCore("different-core");throw new AssertionError("Different core accepted");}catch(java.sql.SQLException expected){check(true,"MySQL cannot silently mix classes from another SQLite core");}
            String legacyId=UUID.randomUUID().toString(),legacyName="legacy_"+legacyId.substring(0,8),legacySecret="L9_"+UUID.randomUUID();
            MySqlManagement.Account legacy=new MySqlManagement.Account(new Identity(legacyId,legacyName,"Sinh viên nhập cũ",Identity.Role.STUDENT),Passwords.hash(legacySecret.toCharArray()),legacyName,true);
            sql.importAccount(legacy);sql.importAccount(legacy);
            check(sql.authenticate(legacyName,legacySecret.toCharArray()).identity().userId().equals(legacyId),"import preserves original userId/hash and is repeatable");
            try{sql.importAccount(new MySqlManagement.Account(new Identity(UUID.randomUUID().toString(),legacyName,"Conflict",Identity.Role.STUDENT),legacy.hash(),legacyName,true));throw new AssertionError("Import conflict accepted");}catch(java.sql.SQLException expected){check(true,"conflicting identity import is rejected");}
            check(admin(base,null,null).status()==401,"admin requires authentication");
            Map<String,Object> dashboard=obj(admin(base,teacher,null).body().get("data"));check(Boolean.TRUE.equals(dashboard.get("enabled")),"MySQL admin enabled");
            String suffix=UUID.randomUUID().toString().substring(0,8),username="user_"+suffix,code="SV_"+suffix,password="P9_"+UUID.randomUUID();
            Response created=admin(base,teacher,mapOf("action","CREATE_USER","username",username,"studentCode",code,"displayName","Sinh viên MySQL","role","STUDENT","password",password));
            check(created.status()==200,"Teacher creates student in MySQL");String id=text(obj(created.body().get("data")),"userId","");
            check(admin(base,teacher,mapOf("action","CREATE_USER","username",username,"studentCode",code,"displayName","Duplicate","role","STUDENT","password",password)).status()!=200,"duplicate login/code rejected");
            String student=login(base,code,password);check(admin(base,student,null).status()==403,"Student cannot read users/IP admin list");
            check(admin(base,student,mapOf("action","CREATE_USER","username","escalate")).status()==403,"Student cannot escalate to admin");
            String classCode="Class-"+suffix;
            Response classroom=admin(base,teacher,mapOf("action","CREATE_CLASS","roomCode",classCode));check(classroom.status()==200,"Admin creates class without entering meeting");String classId=text(obj(classroom.body().get("data")),"classId","");
            savedClass[0]=classId;
            check(admin(base,teacher,mapOf("action","ENROLL","classId",classId,"userId",id)).status()==200,"Admin enrolls student");
            join(base,teacher,classCode);joinApproved(base,teacher,student,classCode);
            Map<String,Object> connection=rows(call(base,"/api/rooms/current/presence",teacher,null).body().get("connections")).getFirst();
            check(connection.get("studentCode").equals(code)&&connection.get("ip").equals("127.0.0.1"),"MySQL session/attendance records real peer and identity");
            WebSocketPresenceIntegrationTest.Listener listener=new WebSocketPresenceIntegrationTest.Listener();
            java.net.http.WebSocket live=WebSocketPresenceIntegrationTest.connect(web,teacher,listener);listener.next("AUTH_OK");
            check(!rows(listener.next("PRESENCE").get("connections")).isEmpty(),"WebSocket publishes real MySQL attendance");
            live.sendText("{\"type\":\"SUBSCRIBE\",\"view\":\"admin\"}",true).join();
            check(Boolean.TRUE.equals(obj(listener.next("ADMIN_UPDATE").get("data")).get("enabled")),"WebSocket publishes MySQL admin dashboard");live.sendClose(1000,"done").join();
            check(!stringify(connection).contains(student)&&!connection.containsKey("sessionId"),"MySQL session secrets absent from API");
            sql.transaction(c->{try(var p=c.prepareStatement("SELECT COUNT(*) FROM attendance a JOIN sessions s ON s.connection_id=a.connection_id WHERE a.user_id=? AND s.status='ONLINE'")){p.setString(1,id);try(var r=p.executeQuery()){check(r.next()&&r.getInt(1)==1,"attendance and sessions persisted in real MySQL");}}return null;});
            TeacherScopeChecks.verify(core,web,sql,base,teacher,student,classId,classCode,id,code,password,suffix,connection);
            ClassAdmissionChecks.verify(core,web,sql,base,teacher,suffix);
            String examContest=text(ExamModeIntegrationTest.assign(base,teacher,"CONTEST_CREATE",mapOf("title","MySQL identity exam","exerciseIds",List.of("ARR001"),"startInMinutes",0,"durationMinutes",10)),"contestId","");
            ExamModeIntegrationTest.assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",examContest,"enabled",true));
            String examSession=text(ExamModeIntegrationTest.assign(base,student,"EXAM_STATUS",mapOf("contestId",examContest)),"examSessionId","");
            ExamModeIntegrationTest.assign(base,student,"EXAM_EVENT",ExamModeIntegrationTest.event(examContest,examSession,"PASTE_ATTEMPT","paste"));
            Map<String,Object> examRow=rows(ExamModeIntegrationTest.assign(base,teacher,"EXAM_MONITOR",mapOf("contestId",examContest)).get("students")).getFirst();
            check(id.equals(examRow.get("userId"))&&number(examRow,"pasteCount",0)==1,"MySQL authenticated identity records exam signal in existing learning database");
            check(ExamModeIntegrationTest.send(base,student,"EXAM_MONITOR",mapOf("contestId",examContest)).status()!=200,"MySQL Student cannot read Teacher exam monitor");
            ExamModeIntegrationTest.assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",examContest,"enabled",false));
            Map<String,Object> recipientData=ExamModeIntegrationTest.assign(base,teacher,"CONTEST_RECIPIENTS",mapOf("contestId",examContest));
            check(rows(recipientData.get("students")).stream().anyMatch(x->id.equals(x.get("userId"))),"MySQL enrolled students are eligible exam recipients");
            check(ExamModeIntegrationTest.send(base,student,"CONTEST_RECIPIENTS",mapOf("contestId",examContest)).status()!=200,"MySQL Student cannot enumerate exam assignment roster");
            ExamModeIntegrationTest.assign(base,teacher,"CONTEST_DISTRIBUTE",mapOf("contestId",examContest,"studentIds",List.of(id),"exerciseIds",List.of("ARR001")));
            check(rows(ExamModeIntegrationTest.assign(base,student,"CONTEST_PROBLEMS_GET",mapOf("contestId",examContest)).get("problems")).size()==1,"MySQL Student receives Teacher chosen exercise using learning database");

            String browserNode=System.getenv("STUDYFIX_BROWSER_TEST_NODE");
            if(browserNode!=null){
                ProcessBuilder builder=new ProcessBuilder(browserNode,"../web-client/tests/teacher_dashboard_browser.cjs",base).inheritIO();
                builder.environment().put("STUDYFIX_BROWSER_STUDENT_CODE",code);builder.environment().put("STUDYFIX_BROWSER_STUDENT_PASSWORD",password);
                Process browser=builder.start();if(!browser.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)){browser.destroyForcibly();throw new AssertionError("Browser test timeout");}check(browser.exitValue()==0,"real Teacher/Student browser dashboard works");
            }
            check(admin(base,teacher,mapOf("action","REMOVE_MEMBER","classId",classId,"userId",id)).status()==200,"Admin removes membership");
            check(call(base,"/api/rooms/current",student,null).status()==409,"removed student detached from live room");
            call(base,"/api/rooms/current/command",student,mapOf("action","JOIN_ROOM","roomId",classCode));Thread.sleep(150);
            check(call(base,"/api/rooms/current",student,null).status()==409,"removed student cannot rejoin via core TCP command");
            check(admin(base,teacher,mapOf("action","ENROLL","classId",classId,"userId",id)).status()==200,"membership can be restored");joinApproved(base,teacher,student,classCode);
            check(admin(base,teacher,mapOf("action","SET_ACTIVE","userId",id,"active",false)).status()==200,"Admin disables account");
            check(call(base,"/api/me",student,null).status()==401,"disable revokes active web session");
            check(call(base,"/api/auth/login",null,mapOf("studentCode",code,"password",password)).status()==401,"disabled account cannot authenticate");
            check(admin(base,teacher,mapOf("action","REMOVE_MEMBER","classId",classId,"userId",id)).status()==200,"disabled student can still be removed from own class");
            admin(base,teacher,mapOf("action","SET_ACTIVE","userId",id,"active",true));String another=login(base,code,password);
            String reset="R8_"+UUID.randomUUID();admin(base,teacher,mapOf("action","RESET_PASSWORD","userId",id,"password",reset));
            check(call(base,"/api/me",another,null).status()==401,"password reset revokes old sessions");
            check(call(base,"/api/auth/login",null,mapOf("studentCode",code,"password",password)).status()==401,"old password rejected after reset");
            String renewed=login(base,code,reset);call(base,"/api/auth/logout",renewed,Map.of());
            dashboard=obj(admin(base,teacher,null).body().get("data"));check(!stringify(dashboard).contains("password_hash")&&!stringify(dashboard).contains(reset),"dashboard never returns password or hash");
            check(core.store().login(code,reset.toCharArray()).userId().equals(id),"MySQL identity consistently mirrored for existing classroom code");
            String imported=UUID.randomUUID().toString(),oldSession=UUID.randomUUID().toString();long oldTime=System.currentTimeMillis()-10000;
            sql.importLegacySession(imported,oldSession,id,"192.168.20.25",oldTime,oldTime+1000);sql.importLegacySession(imported,oldSession,id,"192.168.20.25",oldTime,oldTime+1000);
            sql.importLegacyAttendance(classId,imported,id,oldTime,oldTime+1000,oldTime+1000);sql.importLegacyAttendance(classId,imported,id,oldTime,oldTime+1000,oldTime+1000);
            List<Map<String,Object>> oldRows=sql.students(classId).stream().filter(r->imported.equals(r.get("connectionId"))).toList();
            check(oldRows.size()==1&&!Boolean.TRUE.equals(oldRows.getFirst().get("online"))&&"".equals(oldRows.getFirst().get("ip")),"SQLite presence import is repeatable, Offline, and does not invent class IP");
            if(importSqlite)core.store().classroomTransaction(c->{
                try(var p=c.prepareStatement("INSERT INTO network_sessions(connection_id,session_id,user_id,ip,login_time,last_activity,status,offline_time,reason) VALUES(?,?,?,'127.0.0.1',?,?,'OFFLINE',?,'LOGOUT')")){p.setString(1,sqliteConnection);p.setString(2,UUID.randomUUID().toString());p.setString(3,id);p.setLong(4,oldTime);p.setLong(5,oldTime+1000);p.setLong(6,oldTime+1000);p.executeUpdate();}
                try(var p=c.prepareStatement("INSERT INTO classroom_connections(room_id,connection_id,joined_at,left_at) VALUES(?,?,?,?)")){p.setString(1,classId);p.setString(2,sqliteConnection);p.setLong(3,oldTime);p.setLong(4,oldTime+1000);p.executeUpdate();}return null;
            });
        }
        try(Store restarted=new Store(temp.resolve("core.db"))){
            check(restarted.login(System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"),System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD").toCharArray()).role()==Identity.Role.TEACHER,"MySQL account authenticates after core restart");
            check(!restarted.loadRooms().isEmpty(),"classes and code survive restart without deleting databases");
            if(importSqlite)check(restarted.management().students(savedClass[0]).stream().anyMatch(r->sqliteConnection.equals(r.get("connectionId"))&&!Boolean.TRUE.equals(r.get("online"))),"actual Store migration imports SQLite attendance without restoring a live session");
        }
        System.setProperty("studyfix.management.db","sqlite");
        try{try(Store ignored=new Store(temp.resolve("core.db"))){throw new AssertionError("Bound core fell back to SQLite authentication");}}
        catch(java.sql.SQLException expected){check(true,"MySQL-bound core cannot authenticate using stale SQLite account copies");}
        finally{System.clearProperty("studyfix.management.db");}
        System.out.println("MySqlAdminIntegrationTest: "+checks+" checks PASS against actual MySQL (Teacher ownership, WebSocket, attendance, restart).");
    }
}
