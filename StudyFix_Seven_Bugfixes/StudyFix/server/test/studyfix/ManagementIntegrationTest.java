package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Full administration regression against isolated SQLite files. */
public final class ManagementIntegrationTest {
    static Response admin(String base,String token,Map<String,Object> body)throws Exception{return call(base,"/api/admin",token,body);}
    static void joinApproved(String base,String teacher,String student,String room)throws Exception{
        Map<String,Object> lesson=obj(call(base,"/api/classroom",teacher,null).body().get("classroom"));
        if("WAITING".equals(lesson.get("phase")))check(call(base,"/api/classroom",teacher,mapOf("action","START","expectedPhase","WAITING","round",lesson.get("round"))).status()==200,"teacher starts lesson before student admission");
        Response requested=call(base,"/api/rooms/current/command",student,mapOf("action","JOIN_ROOM","roomId",room));
        if(requested.status()==202){Map<String,Object> row=obj(requested.body().get("admission"));
            Response approved=admin(base,teacher,mapOf("action","APPROVE_JOIN","classId",row.get("classId"),"requestId",row.get("requestId")));
            if(approved.status()!=200)throw new AssertionError("Test approval failed: "+approved.status());
        }
        join(base,student,room);
    }
    public static void main(String[] args)throws Exception{
        System.clearProperty("studyfix.legacy.sqlite");
        System.setProperty("studyfix.management.db","sqlite");
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
        Path temp=Files.createTempDirectory(Path.of("build"),"sqlite-admin-");
        System.setProperty("studyfix.management.sqlite",temp.resolve("management.db").toAbsolutePath().toString());
        ManagementConfig cfg=ManagementConfig.environment(temp.resolve("core.db"));
        ManagementDatabase sql=new ManagementDatabase(cfg);
        sql.createUser("teacher",null,"Teacher SQLite","TEACHER","Teacher12345!".toCharArray());
        String sqliteConnection=UUID.randomUUID().toString();String[] savedClass={""};boolean importSqlite=true;
        try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            String teacher=login(base,"teacher","Teacher12345!");
            try{sql.bindCore("different-core");throw new AssertionError("Different core accepted");}catch(java.sql.SQLException expected){check(true,"SQLite cannot silently mix classes from another SQLite core");}
            String legacyId=UUID.randomUUID().toString(),legacyName="legacy_"+legacyId.substring(0,8),legacySecret="L9_"+UUID.randomUUID();
            ManagementDatabase.Account legacy=new ManagementDatabase.Account(new Identity(legacyId,legacyName,"Sinh viên nhập cũ",Identity.Role.STUDENT),Passwords.hash(legacySecret.toCharArray()),legacyName,true);
            sql.importAccount(legacy);sql.importAccount(legacy);
            check(sql.authenticate(legacyName,legacySecret.toCharArray()).identity().userId().equals(legacyId),"import preserves original userId/hash and is repeatable");
            try{sql.importAccount(new ManagementDatabase.Account(new Identity(UUID.randomUUID().toString(),legacyName,"Conflict",Identity.Role.STUDENT),legacy.hash(),legacyName,true));throw new AssertionError("Import conflict accepted");}catch(java.sql.SQLException expected){check(true,"conflicting identity import is rejected");}
            check(admin(base,null,null).status()==401,"admin requires authentication");
            Map<String,Object> dashboard=obj(admin(base,teacher,null).body().get("data"));check(Boolean.TRUE.equals(dashboard.get("enabled")),"SQLite admin enabled");
            String suffix=UUID.randomUUID().toString().substring(0,8),username="user_"+suffix,code="SV_"+suffix,password="P9_"+UUID.randomUUID();
            Response created=admin(base,teacher,mapOf("action","CREATE_USER","username",username,"studentCode",code,"displayName","Sinh viên SQLite","role","STUDENT","password",password));
            check(created.status()==200,"Teacher creates student in SQLite");String id=text(obj(created.body().get("data")),"userId","");
            check(admin(base,teacher,mapOf("action","CREATE_USER","username",username,"studentCode",code,"displayName","Duplicate","role","STUDENT","password",password)).status()!=200,"duplicate login/code rejected");
            String student=login(base,code,password);check(admin(base,student,null).status()==403,"Student cannot read users/IP admin list");
            check(admin(base,student,mapOf("action","CREATE_USER","username","escalate")).status()==403,"Student cannot escalate to admin");
            String noCodeUsername="no_code_"+suffix;
            Response noCodeCreated=admin(base,teacher,mapOf("action","CREATE_USER","username",noCodeUsername,"displayName","Student without separate code","password",password));
            check(noCodeCreated.status()==200,"Teacher creates Student without studentCode");
            String noCodeToken=login(base,noCodeUsername,password);
            check(call(base,"/api/me",noCodeToken,null).status()==200,"Student without separate code can log in by username");
            sql.transaction(c->{try(var p=c.prepareStatement("SELECT student_code FROM users WHERE username=?")){p.setString(1,noCodeUsername);try(var r=p.executeQuery()){check(r.next()&&noCodeUsername.equals(r.getString(1)),"username used as compatible student identifier when omitted");}}return null;});
            check(admin(base,teacher,mapOf("action","CREATE_USER","username",noCodeUsername,"displayName","Duplicate without separate code","password",password)).status()!=200,"duplicate username still rejected without separate code");
            String batchName="batch_"+suffix;
            List<Map<String,Object>> batch=List.of(mapOf("username",batchName,"displayName","Imported A","password",password),mapOf("username",batchName+"b","displayName","Imported B","password",password));
            Response batchResponse=admin(base,teacher,mapOf("action","IMPORT_STUDENTS","students",batch));
            check(batchResponse.status()==200&&number(obj(batchResponse.body().get("data")),"createdCount",0)==2,"Teacher imports multiple Students atomically");
            check(call(base,"/api/me",login(base,batchName,password),null).status()==200,"imported Student can log in");
            check(admin(base,student,mapOf("action","IMPORT_STUDENTS","students",batch)).status()==403,"Student cannot import accounts");
            String rollbackName="rollback_"+suffix;
            check(admin(base,teacher,mapOf("action","IMPORT_STUDENTS","students",List.of(mapOf("username",rollbackName,"displayName","Must rollback","password",password),mapOf("username",batchName,"displayName","Existing","password",password)))).status()!=200,"existing username rejects entire import");
            sql.transaction(c->{try(var p=c.prepareStatement("SELECT COUNT(*) FROM users WHERE username=?")){p.setString(1,rollbackName);try(var r=p.executeQuery()){check(r.next()&&r.getInt(1)==0,"late duplicate rolls back earlier row");}}return null;});
            check(admin(base,teacher,mapOf("action","IMPORT_STUDENTS","students",List.of(mapOf("username","role_"+suffix,"displayName","Forbidden","role","TEACHER","password",password)))).status()!=200,"import cannot elevate role");
            check(admin(base,teacher,mapOf("action","IMPORT_STUDENTS","students",List.of(mapOf("username","weak_"+suffix,"displayName","Weak","password","short")))).status()!=200,"weak password rejected by server");
            check(admin(base,teacher,mapOf("action","IMPORT_STUDENTS","students",List.of(batch.getFirst(),batch.getFirst()))).status()!=200,"duplicates inside batch rejected");
            sql.transaction(c->{try(var p=c.prepareStatement("SELECT password_hash,student_code,created_by FROM users WHERE username=?")){p.setString(1,batchName);try(var r=p.executeQuery()){check(r.next()&&!password.equals(r.getString(1))&&Passwords.verify(password.toCharArray(),r.getString(1))&&batchName.equals(r.getString(2)),"import hashes password and derives compatible student identifier");}}return null;});
            String classCode="Class-"+suffix;
            Response classroom=admin(base,teacher,mapOf("action","CREATE_CLASS","roomCode",classCode));check(classroom.status()==200,"Admin creates class without entering meeting");String classId=text(obj(classroom.body().get("data")),"classId","");
            savedClass[0]=classId;
            check(admin(base,teacher,mapOf("action","ENROLL","classId",classId,"userId",id)).status()==200,"Admin enrolls student");
            join(base,teacher,classCode);joinApproved(base,teacher,student,classCode);
            Map<String,Object> connection=rows(call(base,"/api/rooms/current/presence",teacher,null).body().get("connections")).getFirst();
            check(connection.get("studentCode").equals(code)&&connection.get("ip").equals("127.0.0.1"),"SQLite session/attendance records real peer and identity");
            WebSocketPresenceIntegrationTest.Listener listener=new WebSocketPresenceIntegrationTest.Listener();
            java.net.http.WebSocket live=WebSocketPresenceIntegrationTest.connect(web,teacher,listener);listener.next("AUTH_OK");
            check(!rows(listener.next("PRESENCE").get("connections")).isEmpty(),"WebSocket publishes real SQLite attendance");
            live.sendText("{\"type\":\"SUBSCRIBE\",\"view\":\"admin\"}",true).join();
            check(Boolean.TRUE.equals(obj(listener.next("ADMIN_UPDATE").get("data")).get("enabled")),"WebSocket publishes SQLite admin dashboard");live.sendClose(1000,"done").join();
            check(!stringify(connection).contains(student)&&!connection.containsKey("sessionId"),"SQLite session secrets absent from API");
            sql.transaction(c->{try(var p=c.prepareStatement("SELECT COUNT(*) FROM attendance a JOIN sessions s ON s.connection_id=a.connection_id WHERE a.user_id=? AND s.status='ONLINE'")){p.setString(1,id);try(var r=p.executeQuery()){check(r.next()&&r.getInt(1)==1,"attendance and sessions persisted in real SQLite");}}return null;});
            TeacherScopeChecks.verify(core,web,sql,base,teacher,student,classId,classCode,id,code,password,suffix,connection);
            ClassAdmissionChecks.verify(core,web,sql,base,teacher,suffix);
            String examContest=text(ExamModeIntegrationTest.assign(base,teacher,"CONTEST_CREATE",mapOf("title","SQLite identity exam","exerciseIds",List.of("ARR001"),"startInMinutes",0,"durationMinutes",10)),"contestId","");
            ExamModeIntegrationTest.assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",examContest,"enabled",true));
            String examSession=text(ExamModeIntegrationTest.assign(base,student,"EXAM_STATUS",mapOf("contestId",examContest)),"examSessionId","");
            ExamModeIntegrationTest.assign(base,student,"EXAM_EVENT",ExamModeIntegrationTest.event(examContest,examSession,"PASTE_ATTEMPT","paste"));
            Map<String,Object> examRow=rows(ExamModeIntegrationTest.assign(base,teacher,"EXAM_MONITOR",mapOf("contestId",examContest)).get("students")).getFirst();
            check(id.equals(examRow.get("userId"))&&number(examRow,"pasteCount",0)==1,"SQLite authenticated identity records exam signal in existing learning database");
            check(ExamModeIntegrationTest.send(base,student,"EXAM_MONITOR",mapOf("contestId",examContest)).status()!=200,"SQLite Student cannot read Teacher exam monitor");
            ExamModeIntegrationTest.assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",examContest,"enabled",false));
            Map<String,Object> recipientData=ExamModeIntegrationTest.assign(base,teacher,"CONTEST_RECIPIENTS",mapOf("contestId",examContest));
            check(rows(recipientData.get("students")).stream().anyMatch(x->id.equals(x.get("userId"))),"SQLite enrolled students are eligible exam recipients");
            check(ExamModeIntegrationTest.send(base,student,"CONTEST_RECIPIENTS",mapOf("contestId",examContest)).status()!=200,"SQLite Student cannot enumerate exam assignment roster");
            ExamModeIntegrationTest.assign(base,teacher,"CONTEST_DISTRIBUTE",mapOf("contestId",examContest,"studentIds",List.of(id),"exerciseIds",List.of("ARR001")));
            check(rows(ExamModeIntegrationTest.assign(base,student,"CONTEST_PROBLEMS_GET",mapOf("contestId",examContest)).get("problems")).size()==1,"SQLite Student receives Teacher chosen exercise using learning database");

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
            check(core.store().login(code,reset.toCharArray()).userId().equals(id),"SQLite identity consistently mirrored for existing classroom code");
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
        System.setProperty("studyfix.management.importCore","true");
        try(Store restarted=new Store(temp.resolve("core.db"))){
            check(restarted.login("teacher","Teacher12345!".toCharArray()).role()==Identity.Role.TEACHER,"SQLite account authenticates after core restart");
            check(!restarted.loadRooms().isEmpty(),"classes and code survive restart without deleting databases");
            if(importSqlite)check(restarted.management().students(savedClass[0]).stream().anyMatch(r->sqliteConnection.equals(r.get("connectionId"))&&!Boolean.TRUE.equals(r.get("online"))),"actual Store migration imports SQLite attendance without restoring a live session");
        }
        System.clearProperty("studyfix.management.importCore");
        try{new ManagementConfig("jdbc:other:unsupported");throw new AssertionError("Unsupported engine accepted");}
        catch(IllegalArgumentException expected){check(true,"only SQLite JDBC URLs accepted");}
        System.setProperty("studyfix.management.db","unsupported");
        try{ManagementConfig.environment();throw new AssertionError("Unsupported mode accepted");}
        catch(IllegalArgumentException expected){check(true,"only SQLite configuration accepted");}
        finally{System.setProperty("studyfix.management.db","sqlite");}
        System.out.println("ManagementIntegrationTest: "+checks+" checks PASS against actual SQLite (Teacher ownership, WebSocket, attendance, restart).");
    }
}
