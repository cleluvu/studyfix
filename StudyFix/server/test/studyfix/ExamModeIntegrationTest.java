package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

public final class ExamModeIntegrationTest {
    static Map<String,Object> assign(String base,String token,String type,Map<String,Object> data)throws Exception{
        Map<String,Object> body=new LinkedHashMap<>(data);body.put("type",type);Response r=call(base,"/api/assignments",token,body);
        if(r.status()!=200)throw new AssertionError(type+": "+r.body());return obj(r.body().get("data"));
    }
    static Response send(String base,String token,String type,Map<String,Object> data)throws Exception{Map<String,Object> body=new LinkedHashMap<>(data);body.put("type",type);return call(base,"/api/assignments",token,body);}
    static Map<String,Object> event(String contest,String session,String type,String source){return mapOf("contestId",contest,"examSessionId",session,"eventId",UUID.randomUUID().toString(),"eventType",type,"source",source,"clientAt",System.currentTimeMillis());}
    public static void main(String[] args)throws Exception{
        System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
        Path temp=Files.createTempDirectory(Path.of("build"),"exam-mode-test-"),db=temp.resolve("core.db"),learning=temp.resolve("learning.db");String contest="",savedSession="",randomContest="";List<String> savedRandom=List.of();
        try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
            core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port();
            String teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!"),other=login(base,"student2","Student234!");
            call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM","roomCode","EXAM"));join(base,student,"EXAM");join(base,other,"EXAM");
            contest=text(assign(base,teacher,"CONTEST_CREATE",mapOf("title","Kiểm tra có giám sát","exerciseIds",List.of("ARR001"),"startInMinutes",0,"durationMinutes",10)),"contestId","");
            Map<String,Object> cid=mapOf("contestId",contest);
            check(send(base,student,"CONTEST_CREATE",mapOf("title","Forbidden random","exerciseIds",List.of("ARR001"),"durationMinutes",10,"distributionMode","RANDOM","problemsPerStudent",1)).status()!=200,"Student cannot create random contest");
            check(send(base,teacher,"CONTEST_CREATE",mapOf("title","Invalid draw","exerciseIds",List.of("ARR001"),"durationMinutes",10,"distributionMode","RANDOM","problemsPerStudent",2)).status()!=200,"draw count cannot exceed pool size");
            randomContest=text(assign(base,teacher,"CONTEST_CREATE",mapOf("title","Random test","exerciseIds",List.of("ARR001","ARR002","ARR003"),"durationMinutes",10,"distributionMode","RANDOM","problemsPerStudent",2)),"contestId","");
            Map<String,Object> randomCid=mapOf("contestId",randomContest);
            final Map<String,Object> concurrentCid=randomCid;
            var drawA=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return assign(base,student,"CONTEST_PROBLEMS_GET",concurrentCid);}catch(Exception e){throw new RuntimeException(e);}});
            var drawB=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return assign(base,student,"CONTEST_PROBLEMS_GET",concurrentCid);}catch(Exception e){throw new RuntimeException(e);}});
            List<Map<String,Object>> drawn=rows(drawA.get().get("problems"));savedRandom=drawn.stream().map(x->text(x,"id","")).toList();
            check(savedRandom.size()==2&&new HashSet<>(savedRandom).size()==2,"random draw has exact distinct problem count");
            String targetContest=text(assign(base,teacher,"CONTEST_CREATE",mapOf("title","Selected recipients","exerciseIds",List.of("ARR001","ARR002","ARR003"),"durationMinutes",10)),"contestId","");
            Map<String,Object> targetCid=mapOf("contestId",targetContest);
            List<Map<String,Object>> candidates=rows(assign(base,teacher,"CONTEST_RECIPIENTS",targetCid).get("students"));
            String targetStudent=candidates.stream().filter(x->"student1".equals(x.get("username"))).map(x->text(x,"userId","")).findFirst().orElseThrow();
            String targetOther=candidates.stream().filter(x->"student2".equals(x.get("username"))).map(x->text(x,"userId","")).findFirst().orElseThrow();
            Map<String,Object> allocation=mapOf("contestId",targetContest,"studentIds",List.of(targetStudent),"exerciseIds",List.of("ARR001","ARR003"));
            check(send(base,student,"CONTEST_DISTRIBUTE",allocation).status()!=200,"Student cannot assign exams");
            check(send(base,teacher,"CONTEST_DISTRIBUTE",mapOf("contestId",targetContest,"studentIds",List.of("foreign-student"),"exerciseIds",List.of("ARR001"))).status()!=200,"foreign/nonmember recipients rejected");
            check(send(base,teacher,"CONTEST_DISTRIBUTE",mapOf("contestId",targetContest,"studentIds",List.of(targetStudent),"exerciseIds",List.of("STR001"))).status()!=200,"assignment cannot add problems outside contest pool");
            assign(base,teacher,"CONTEST_DISTRIBUTE",allocation);
            check(rows(assign(base,student,"CONTEST_PROBLEMS_GET",targetCid).get("problems")).size()==2,"selected Student receives chosen subset even in common exam");
            check(send(base,other,"CONTEST_PROBLEMS_GET",targetCid).status()!=200&&send(base,other,"EXAM_STATUS",targetCid).status()!=200,"unselected Student cannot open exam or monitoring session");
            check(rows(assign(base,other,"CONTEST_LIST",Map.of()).get("contests")).stream().noneMatch(x->targetContest.equals(x.get("id"))),"unassigned exam hidden from Student directory");
            check(send(base,student,"CONTEST_SUBMIT",mapOf("contestId",targetContest,"exerciseId","ARR002","sourceCode","public class Main {}","idempotencyKey",UUID.randomUUID().toString())).status()!=200,"chosen recipient cannot submit outside teacher subset");
            assign(base,teacher,"CONTEST_DISTRIBUTE",mapOf("contestId",targetContest,"studentIds",List.of(targetStudent),"exerciseIds",List.of("ARR002")));
            check(rows(assign(base,student,"CONTEST_PROBLEMS_GET",targetCid).get("problems")).stream().map(x->text(x,"id","")).toList().equals(List.of("ARR001","ARR003")),"repeated assignment preserves previously received paper");
            assign(base,teacher,"CONTEST_DISTRIBUTE",mapOf("contestId",targetContest,"studentIds",List.of(targetOther),"exerciseIds",List.of("ARR002")));
            check(rows(assign(base,other,"CONTEST_PROBLEMS_GET",targetCid).get("problems")).size()==1,"Teacher can append recipients with a separate subset");

            check(savedRandom.equals(rows(drawB.get().get("problems")).stream().map(x->text(x,"id","")).toList()),"concurrent requests cannot reroll random draw");
            check(savedRandom.equals(rows(assign(base,student,"CONTEST_PROBLEMS_GET",randomCid).get("problems")).stream().map(x->text(x,"id","")).toList()),"refresh preserves assigned problems");
            Map<String,Object> otherDraw=assign(base,other,"CONTEST_PROBLEMS_GET",randomCid);
            check(rows(otherDraw.get("problems")).size()==2&&rows(otherDraw.get("draws")).isEmpty(),"other Student gets own exact draw without class allocations");
            Map<String,Object> allDraws=assign(base,teacher,"CONTEST_PROBLEMS_GET",randomCid);
            check(rows(allDraws.get("problems")).size()==3&&rows(allDraws.get("draws")).size()==4,"Teacher sees pool and persisted student allocations");
            String excluded=List.of("ARR001","ARR002","ARR003").stream().filter(x->!drawn.stream().anyMatch(d->x.equals(d.get("id")))).findFirst().orElseThrow();
            check(send(base,student,"CONTEST_SUBMIT",mapOf("contestId",randomContest,"exerciseId",excluded,"sourceCode","public class Main {}","idempotencyKey",UUID.randomUUID().toString())).status()!=200,"Student cannot submit an unassigned pool problem");

            check(!Boolean.TRUE.equals(assign(base,student,"EXAM_STATUS",cid).get("enabled")),"Exam Mode is off by default");
            check(send(base,student,"EXAM_CONFIG",mapOf("contestId",contest,"enabled",true)).status()!=200,"Student cannot enable or disable exam mode");
            assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",contest,"enabled",true,"tabLimit",1,"pasteLimit",1));
            Map<String,Object> status=assign(base,student,"EXAM_STATUS",cid);savedSession=text(status,"examSessionId","");
            check(!savedSession.isBlank()&&Boolean.TRUE.equals(status.get("live")),"active exam creates authenticated Student exam session");
            check(savedSession.equals(assign(base,student,"EXAM_STATUS",cid).get("examSessionId")),"refresh/status keeps same exam session");
            Map<String,Object> paste=event(contest,savedSession,"PASTE_ATTEMPT","paste");paste.put("studentId","FORGED");paste.put("clipboard","NEVER_STORE_CLIPBOARD");
            assign(base,student,"EXAM_EVENT",paste);check(Boolean.TRUE.equals(assign(base,student,"EXAM_EVENT",paste).get("duplicate")),"network retry cannot duplicate paste count");
            assign(base,student,"EXAM_EVENT",event(contest,savedSession,"TAB_SWITCH","blur"));
            check(Boolean.TRUE.equals(assign(base,student,"EXAM_EVENT",event(contest,savedSession,"TAB_SWITCH","hidden")).get("duplicate")),"blur + hidden are one departure episode");
            Thread.sleep(80);assign(base,student,"EXAM_EVENT",event(contest,savedSession,"TAB_RETURN","focus"));
            Map<String,Object> monitor=assign(base,teacher,"EXAM_MONITOR",cid),row=rows(monitor.get("students")).getFirst();
            check(number(row,"tabCount",0)==1&&number(row,"pasteCount",0)==1&&!Boolean.TRUE.equals(row.get("away")),"official counters and return status stored on server");
            check(Boolean.TRUE.equals(row.get("warning"))&&"student1".equals(row.get("username")),"threshold flags signal and identity comes from login");
            check(send(base,other,"EXAM_EVENT",paste).status()!=200,"another Student cannot report using someone else's exam session");
            check(send(base,student,"EXAM_MONITOR",cid).status()!=200&&send(base,student,"EXAM_HISTORY",cid).status()!=200,"Student cannot read monitoring rows or event histories");
            String sameUser=login(base,"student1","Student123!");join(base,sameUser,"EXAM");
            check(send(base,sameUser,"EXAM_EVENT",paste).status()!=200,"same account's other device cannot reuse first device exam session");
            check(!savedSession.equals(assign(base,sameUser,"EXAM_STATUS",cid).get("examSessionId")),"different login devices get independent exam sessions");
            WebSocketPresenceIntegrationTest.Listener listener=new WebSocketPresenceIntegrationTest.Listener();var socket=WebSocketPresenceIntegrationTest.connect(web,teacher,listener);listener.next("AUTH_OK");
            Map<String,Object> push=obj(listener.next("PRESENCE").get("examSnapshot"));
            check(!obj(push.get("monitors")).isEmpty(),"Teacher monitoring updates use existing real WebSocket");socket.sendClose(1000,"done").join();
            WebSocketPresenceIntegrationTest.Listener studentListener=new WebSocketPresenceIntegrationTest.Listener();var studentSocket=WebSocketPresenceIntegrationTest.connect(web,student,studentListener);studentListener.next("AUTH_OK");
            check(obj(obj(studentListener.next("ALIVE").get("examSnapshot")).get("monitors")).isEmpty(),"Student WebSocket snapshot never contains Teacher monitoring data");studentSocket.sendClose(1000,"done").join();
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role) VALUES(?,?,?,?,?)")){
                p.setString(1,UUID.randomUUID().toString());p.setString(2,"exam_teacher2");p.setString(3,Passwords.hash("Teacher234!".toCharArray()));p.setString(4,"Teacher B");p.setString(5,"TEACHER");p.executeUpdate();
            }
            String foreign=login(base,"exam_teacher2","Teacher234!");call(base,"/api/rooms/current/command",foreign,mapOf("action","CREATE_ROOM","roomCode","OTHER-EXAM"));
            check(send(base,foreign,"EXAM_MONITOR",cid).status()!=200&&send(base,foreign,"EXAM_CONFIG",mapOf("contestId",contest,"enabled",false)).status()!=200,"foreign Teacher cannot read/change another room exam");
            List<Map<String,Object>> history=rows(assign(base,teacher,"EXAM_HISTORY",cid).get("events"));
            check(history.size()==3&&history.stream().anyMatch(e->number(e,"durationMs",0)>0),"history stores departure/return and server-measured duration");
            check(!stringify(history).contains("NEVER_STORE_CLIPBOARD")&&!stringify(history).contains("FORGED"),"clipboard payload and forged identity are never saved");
            assign(base,student,"EXAM_EVENT",event(contest,savedSession,"TAB_SWITCH","navigation"));assign(base,student,"EXAM_EVENT",event(contest,savedSession,"TAB_RETURN","focus"));
            check(number(rows(assign(base,teacher,"EXAM_MONITOR",cid).get("students")).getFirst(),"tabCount",0)==2,"leaving exam view inside the app is recorded as one episode");
            Map<String,Object> malicious=event(contest,savedSession,"PASTE_ATTEMPT","paste");malicious.put("eventType","DISABLE_EXAM");check(send(base,student,"EXAM_EVENT",malicious).status()!=200,"unknown event types rejected");
            int rejected=0;for(int i=0;i<35;i++)if(send(base,student,"EXAM_EVENT",event(contest,savedSession,"PASTE_ATTEMPT","shortcut")).status()!=200)rejected++;
            check(rejected>0,"server rate limit prevents event flood");
            String browserNode=System.getenv("STUDYFIX_BROWSER_TEST_NODE");
            if(browserNode!=null){
                Process browser=new ProcessBuilder(browserNode,"../web-client/tests/exam_mode_browser.cjs",base,contest).inheritIO().start();
                if(!browser.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)){browser.destroyForcibly();throw new AssertionError("Exam browser timeout");}
                check(browser.exitValue()==0,"real browser Student/Teacher exam workflow");
            }
            assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",contest,"enabled",false,"tabLimit",2,"pasteLimit",2));
            check(send(base,student,"EXAM_EVENT",paste).status()!=200&&!Boolean.TRUE.equals(assign(base,student,"EXAM_STATUS",cid).get("enabled")),"disabled exam stops collecting signals");
            assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",contest,"enabled",true));
            check(send(base,student,"EXAM_EVENT",paste).status()!=200,"old generation cannot replay into re-enabled exam");
            check(!savedSession.equals(assign(base,student,"EXAM_STATUS",cid).get("examSessionId")),"re-enabled exam creates a new session generation");
            ok(base,teacher,"START",Map.of());
            ok(base,teacher,"SELECT_EXERCISE",mapOf("exerciseId","DBG_TEXT001"));
            Map<String,Object> branchRound=ok(base,teacher,"FORK",Map.of());
            String roomId=text(obj(call(base,"/api/rooms/current",teacher,null).body().get("room")),"roomId","");
            String branchId="classroom:"+roomId+":"+number(branchRound,"round",0);Map<String,Object> branchCid=mapOf("contestId",branchId);
            check(!Boolean.TRUE.equals(assign(base,student,"EXAM_STATUS",branchCid).get("enabled")),"branch monitoring defaults off independently of contest");
            check(send(base,student,"EXAM_CONFIG",mapOf("contestId",branchId,"enabled",true)).status()!=200,"Student cannot enable branch monitoring");
            assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",branchId,"enabled",true,"tabLimit",1,"pasteLimit",1));
            String bs=text(assign(base,student,"EXAM_STATUS",branchCid),"examSessionId","");
            Map<String,Object> be=event(branchId,bs,"PASTE_ATTEMPT","paste");assign(base,student,"EXAM_EVENT",be);assign(base,student,"EXAM_EVENT",be);
            assign(base,student,"EXAM_EVENT",event(branchId,bs,"TAB_SWITCH","hidden"));assign(base,student,"EXAM_EVENT",event(branchId,bs,"TAB_RETURN","focus"));
            Map<String,Object> br=rows(assign(base,teacher,"EXAM_MONITOR",branchCid).get("students")).getFirst();
            check(number(br,"pasteCount",0)==1&&number(br,"tabCount",0)==1,"branch counts persist, deduplicate and remain separate from contest");
            check(send(base,student,"EXAM_MONITOR",branchCid).status()!=200&&send(base,other,"EXAM_EVENT",be).status()!=200,"branch monitor and session ownership enforced");
            check(send(base,foreign,"EXAM_MONITOR",branchCid).status()!=200,"foreign Teacher cannot read branch monitoring");
            Map<String,Object> branchPush=assign(base,teacher,"EXAM_SNAPSHOT",Map.of());
            check(obj(branchPush.get("monitors")).containsKey(branchId),"existing realtime snapshot includes scoped branch monitor");
            check(rows(assign(base,teacher,"EXAM_HISTORY",branchCid).get("events")).size()==3,"branch history records paste, leave and return");
            ok(base,teacher,"FREEZE",Map.of());
            check(!Boolean.TRUE.equals(assign(base,student,"EXAM_STATUS",branchCid).get("live"))&&send(base,student,"EXAM_EVENT",event(branchId,bs,"PASTE_ATTEMPT","paste")).status()!=200,"freeze ends collection on server even if browser claims active");
            ok(base,teacher,"RESUME",Map.of());
            assign(base,teacher,"EXAM_CONFIG",mapOf("contestId",branchId,"enabled",false));
            check(send(base,student,"EXAM_EVENT",event(branchId,bs,"PASTE_ATTEMPT","paste")).status()!=200,"Teacher can turn off branch restrictions");
            ok(base,teacher,"FREEZE",Map.of());ok(base,teacher,"MERGE",mapOf("userId",text(br,"userId",""),"branchVersion",0));
            ok(base,teacher,"FORK",Map.of());
            check(send(base,student,"EXAM_STATUS",branchCid).status()!=200,"old round cannot submit monitoring signals into new round");
            if(browserNode!=null){
                Process browser=new ProcessBuilder(browserNode,"../web-client/tests/branch_monitor_browser.cjs",base).inheritIO().start();
                if(!browser.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)){browser.destroyForcibly();throw new AssertionError("Branch browser timeout");}
                check(browser.exitValue()==0,"real browser branch monitoring workflow");
            }


        }
        try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
            core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!");join(base,teacher,"EXAM");
            check(Boolean.TRUE.equals(assign(base,teacher,"EXAM_STATUS",mapOf("contestId",contest)).get("enabled")),"Exam Mode persists after server restart");
            check(!rows(assign(base,teacher,"EXAM_HISTORY",mapOf("contestId",contest)).get("events")).isEmpty(),"monitoring history survives restart");
            String returningStudent=login(base,"student1","Student123!");join(base,returningStudent,"EXAM");
            check(savedRandom.equals(rows(assign(base,returningStudent,"CONTEST_PROBLEMS_GET",mapOf("contestId",randomContest)).get("problems")).stream().map(x->text(x,"id","")).toList()),"random allocation survives server restart and new login session");

        }
        System.out.println("ExamModeIntegrationTest: "+checks+" checks PASS (real Java/HTTP/WebSocket/SQLite, isolated data).");
    }
}
