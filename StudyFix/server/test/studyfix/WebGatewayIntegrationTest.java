package studyfix;

import studyfix.assignment.AssignmentModuleConfig;
import studyfix.assignment.protocol.AssignmentJson;
import studyfix.assignment.transport.AssignmentTcpServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.*;

/** Full temporary-database smoke test for the web FE gateway and the existing Java services. */
public final class WebGatewayIntegrationTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private record Response(int status, Map<String,Object> body, String raw) { }

    private static Response request(HttpClient client, String base, String path, String method,
                                    String token, Map<String,Object> payload) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(8));
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);
        if (payload != null) {
            b.header("Content-Type", "application/json").method(method,
                    HttpRequest.BodyPublishers.ofString(AssignmentJson.stringify(payload), StandardCharsets.UTF_8));
        } else if ("GET".equalsIgnoreCase(method)) b.GET();
        else b.method(method, HttpRequest.BodyPublishers.noBody());
        HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String,Object> body;
        try { body=AssignmentJson.object(r.body()); } catch (RuntimeException e) { body=Map.of(); }
        return new Response(r.statusCode(), body, r.body());
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object o) { return o instanceof Map<?,?> m ? (Map<String,Object>)m : Map.of(); }
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static long number(Object o) { return o instanceof Number n ? n.longValue() : 0L; }
    @SuppressWarnings("unchecked") private static List<Object> list(Object o) { return o instanceof List<?> l ? (List<Object>)l : List.of(); }
    private static Response assign(HttpClient client,String base,String token,String room,String type,Map<String,Object> data) throws Exception {
        Map<String,Object> body=new LinkedHashMap<>(data);body.put("type",type);
        // roomId is injected from the authenticated current core session by the gateway.
        return request(client,base,"/api/assignments","POST",token,body);
    }
    private static String login(HttpClient client,String base,String username,String password) throws Exception {
        Response r=request(client,base,"/api/auth/login","POST",null,Map.of("username",username,"password",password));
        check(r.status==200 && !str(r.body.get("token")).isBlank(),username+" login succeeds");
        return str(r.body.get("token"));
    }
    private static void waitFor(BooleanCheck condition,String message)throws Exception{
        long end=System.currentTimeMillis()+3500;
        while(System.currentTimeMillis()<end){if(condition.ok())return;Thread.sleep(100);}
        check(false,message);
    }
    @FunctionalInterface private interface BooleanCheck { boolean ok() throws Exception; }

    private static HttpClient trustAllHttpsClient() throws Exception {
        TrustManager[] trust = { new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        }};
        SSLContext context=SSLContext.getInstance("TLS");
        context.init(null,trust,new SecureRandom());
        return HttpClient.newBuilder().sslContext(context).connectTimeout(Duration.ofSeconds(3)).build();
    }
    private static void testHttpsGateway(HttpClient client, String host, int unused, Path temp,
                                         Server core, AssignmentTcpServer assignments, WebGatewayServer[] current)
            throws Exception {
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        String keytoolName=os.contains("win")?"keytool.exe":"keytool";
        Path keytool=Path.of(System.getProperty("java.home"),"bin",keytoolName);
        Path store=temp.resolve("gateway-test.p12");
        Process cert=new ProcessBuilder(keytool.toString(),"-genkeypair","-noprompt","-alias","studyfix-test",
                "-keyalg","RSA","-keysize","2048","-validity","2","-storetype","PKCS12",
                "-keystore",store.toString(),"-storepass","testpass123","-keypass","testpass123",
                "-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1")
                .redirectErrorStream(true).start();
        byte[] certOutput=cert.getInputStream().readAllBytes();
        if(!cert.waitFor(25,java.util.concurrent.TimeUnit.SECONDS)||cert.exitValue()!=0)
            throw new AssertionError("keytool could not create test keystore: "+new String(certOutput,StandardCharsets.UTF_8));
        current[0].close();
        System.setProperty("studyfix.web.keystore",store.toString());
        System.setProperty("studyfix.web.keystore.password","testpass123");
        System.setProperty("studyfix.web.port","0");
        current[0]=new WebGatewayServer(core,assignments);
        current[0].start();
        String secureBase="https://127.0.0.1:"+current[0].port();
        Response health=request(client,secureBase,"/api/health","GET",null,null);
        check(health.status==200&&"https".equals(health.body.get("scheme")),"optional HTTPS gateway serves API health with TLS");
        Response index=request(client,secureBase,"/","GET",null,null);
        check(index.status==200&&index.raw.contains("StudyFix"),"HTTPS gateway serves the same web client assets");
        System.clearProperty("studyfix.web.keystore");
        System.clearProperty("studyfix.web.keystore.password");
    }

    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("studyfix-web-integration-");
        Path coreDb=temp.resolve("core.db"), learningDb=temp.resolve("learning.db");
        System.setProperty("studyfix.screen.port","0");
        System.setProperty("studyfix.web.port","0");
        System.setProperty("studyfix.web.host","127.0.0.1");
        Path staticRoot=Path.of("../web-client/static").toAbsolutePath().normalize();
        System.setProperty("studyfix.web.root",staticRoot.toString());
        System.setProperty("studyfix.web.dist",temp.resolve("no-dist").toString());
        Server core=null; AssignmentTcpServer assignments=null; WebGatewayServer web=null;
        try {
            core=new Server(0,coreDb);
            assignments=new AssignmentTcpServer(core.gateway(),learningDb,0,24,1,"studyfix-test-image-not-installed");
            web=new WebGatewayServer(core,assignments);
            core.start();assignments.start();web.start();
            String base="http://127.0.0.1:"+web.port();
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

            Response health=request(client,base,"/api/health","GET",null,null);
            check(health.status==200 && "StudyFix Web Gateway".equals(health.body.get("service")),"health endpoint returns ready status");
            Response home=request(client,base,"/","GET",null,null);
            check(home.status==200 && home.raw.contains("StudyFix"),"web client static entry is served");
            Response css=request(client,base,"/app.css","GET",null,null);
            check(css.status==200 && css.raw.contains("app-shell"),"responsive web stylesheet is served");
            Response js=request(client,base,"/app.js","GET",null,null);
            check(js.status==200 && js.raw.contains("core/events.js") && js.raw.contains("registerPage"),"refactored web app entry module is served");

            String teacher=login(client,base,"teacher","Teacher123!");
            Response createRoom=request(client,base,"/api/rooms/current/command","POST",teacher,Map.of("action","CREATE_ROOM","roomCode","Test "+UUID.randomUUID()));
            check(createRoom.status==200,"teacher room-create command accepted");
            waitFor(()->request(client,base,"/api/me","GET",teacher,null).status==200
                    && !str(request(client,base,"/api/me","GET",teacher,null).body.get("roomId")).isBlank(),"teacher receives created room");
            Response teacherRoom=request(client,base,"/api/rooms/current","GET",teacher,null);
            String roomId=str(object(teacherRoom.body.get("room")).get("roomId"));
            check(teacherRoom.status==200&&!roomId.isBlank(),"teacher can load current room");

            String student1=login(client,base,"student1","Student123!");
            check(request(client,base,"/api/rooms/current/command","POST",student1,Map.of("action","JOIN_ROOM","roomId",roomId)).status==202,"student1 join command accepted");
            waitFor(()->!str(request(client,base,"/api/me","GET",student1,null).body.get("roomId")).isBlank(),"student1 joins room");

            // Browser screen-sharing signaling is role-neutral, scoped to the current room,
            // and permits one active presenter at a time. This tests signaling/routing, not
            // the browser's physical capture device or ICE connectivity.
            String webTeacherPeer=str(request(client,base,"/api/me","GET",teacher,null).body.get("peerId"));
            String webStudentPeer=str(request(client,base,"/api/me","GET",student1,null).body.get("peerId"));
            Response teacherScreenStart=request(client,base,"/api/screen/signal","POST",teacher,
                    Map.of("kind","START","payload","Teacher screen"));
            check(teacherScreenStart.status==202,"teacher can start browser screen share");
            Response studentScreenPoll=request(client,base,"/api/screen/signals?after=0","GET",student1,null);
            List<Object> studentScreenSignals=list(studentScreenPoll.body.get("signals"));
            check(webTeacherPeer.equals(str(studentScreenPoll.body.get("activePresenterPeerId"))),"student screen poll identifies teacher as the active presenter");
            check(studentScreenSignals.stream().map(WebGatewayIntegrationTest::object).anyMatch(x->"START".equals(str(x.get("kind")))&&webTeacherPeer.equals(str(x.get("fromPeerId")))),"teacher START signal reaches student in the same room");
            Response studentScreenRequest=request(client,base,"/api/screen/signal","POST",student1,Map.of("kind","REQUEST","payload",""));
            check(studentScreenRequest.status==202,"student can request the active screen");
            Response teacherScreenPoll=request(client,base,"/api/screen/signals?after=0","GET",teacher,null);
            check(list(teacherScreenPoll.body.get("signals")).stream().map(WebGatewayIntegrationTest::object).anyMatch(x->"REQUEST".equals(str(x.get("kind")))&&webStudentPeer.equals(str(x.get("fromPeerId")))),"screen request is routed to teacher presenter");
            Response teacherOffer=request(client,base,"/api/screen/signal","POST",teacher,
                    Map.of("toPeerId",webStudentPeer,"kind","OFFER","payload","{\"type\":\"offer\",\"sdp\":\"test-sdp\"}"));
            check(teacherOffer.status==202,"teacher offer is routed to student in the same room");
            Response studentOfferPoll=request(client,base,"/api/screen/signals?after=0","GET",student1,null);
            check(list(studentOfferPoll.body.get("signals")).stream().map(WebGatewayIntegrationTest::object).anyMatch(x->"OFFER".equals(str(x.get("kind")))&&webTeacherPeer.equals(str(x.get("fromPeerId")))),"student receives teacher offer signal");
            Response competingStart=request(client,base,"/api/screen/signal","POST",student1,Map.of("kind","START","payload","Student screen"));
            check(competingStart.status==409,"second presenter is rejected while teacher is sharing");
            Response teacherEnd=request(client,base,"/api/screen/signal","POST",teacher,Map.of("kind","END","payload",""));
            check(teacherEnd.status==202,"teacher can stop their screen share");
            Response clearedPresenter=request(client,base,"/api/screen/signals?after=0","GET",student1,null);
            check(str(clearedPresenter.body.get("activePresenterPeerId")).isBlank(),"student receives cleared presenter state after teacher stops");

            Response studentScreenStart=request(client,base,"/api/screen/signal","POST",student1,
                    Map.of("kind","START","payload","Student screen"));
            check(studentScreenStart.status==202,"student can initiate browser screen share");
            Response teacherSeesStudent=request(client,base,"/api/screen/signals?after=0","GET",teacher,null);
            check(webStudentPeer.equals(str(teacherSeesStudent.body.get("activePresenterPeerId"))),"teacher poll identifies student as active presenter");
            Response teacherRequestsStudent=request(client,base,"/api/screen/signal","POST",teacher,Map.of("kind","REQUEST","payload",""));
            check(teacherRequestsStudent.status==202,"teacher can request student's screen");
            Response studentSeesRequest=request(client,base,"/api/screen/signals?after=0","GET",student1,null);
            check(list(studentSeesRequest.body.get("signals")).stream().map(WebGatewayIntegrationTest::object).anyMatch(x->"REQUEST".equals(str(x.get("kind")))&&webTeacherPeer.equals(str(x.get("fromPeerId")))),"teacher request is routed to student presenter");
            Response studentOffer=request(client,base,"/api/screen/signal","POST",student1,
                    Map.of("toPeerId",webTeacherPeer,"kind","OFFER","payload","{\"type\":\"offer\",\"sdp\":\"student-sdp\"}"));
            check(studentOffer.status==202,"student offer is routed to teacher in the same room");
            Response teacherSeesOffer=request(client,base,"/api/screen/signals?after=0","GET",teacher,null);
            check(list(teacherSeesOffer.body.get("signals")).stream().map(WebGatewayIntegrationTest::object).anyMatch(x->"OFFER".equals(str(x.get("kind")))&&webStudentPeer.equals(str(x.get("fromPeerId")))),"teacher receives student offer signal");
            Response teacherAnswer=request(client,base,"/api/screen/signal","POST",teacher,
                    Map.of("toPeerId",webStudentPeer,"kind","ANSWER","payload","{\"type\":\"answer\",\"sdp\":\"answer-sdp\"}"));
            check(teacherAnswer.status==202,"viewer answer is accepted when student is the presenter");
            Response studentEnd=request(client,base,"/api/screen/signal","POST",student1,Map.of("kind","END","payload",""));
            check(studentEnd.status==202,"student presenter can stop their share");
            Response student2Login=request(client,base,"/api/auth/login","POST",null,Map.of("username","student2","password","Student234!"));
            if(student2Login.status==200) {
                Response outOfRoomScreen=request(client,base,"/api/screen/signal","POST",str(student2Login.body.get("token")),Map.of("kind","START","payload","Outside room"));
                check(outOfRoomScreen.status>=400,"user outside the room cannot start or broadcast a screen share");
            } else {
                check(false,"student2 account is available to test out-of-room screen-share rejection");
            }

            Response catalog=assign(client,base,teacher,roomId,"CATALOG_LIST",Map.of("query","","topic","","mode",""));
            Map<String,Object> catalogData=object(catalog.body.get("data"));
            List<Object> exercises=list(catalogData.get("exercises"));
            check(catalog.status==200 && "OK".equals(catalog.body.get("status")) && exercises.size()>=20,"teacher reads exercise bank");
            Response hiddenCatalog=assign(client,base,student1,roomId,"CATALOG_LIST",Map.of());
            check(hiddenCatalog.status==400 && "FORBIDDEN".equals(hiddenCatalog.body.get("errorCode")),"student cannot read teacher-only catalog API");
            String scratchId="";
            for(Object row:exercises){Map<String,Object> ex=object(row);if("BUILD_FROM_SCRATCH".equals(str(ex.get("mode")))){scratchId=str(ex.get("id"));break;}}
            check(!scratchId.isBlank(),"catalog has an individual coding exercise");

            Response contest=assign(client,base,teacher,roomId,"CONTEST_CREATE",Map.of("title","Web gateway direct catalog contest","startInMinutes",0,"durationMinutes",60,"contestType","INDIVIDUAL","exerciseIds",List.of(scratchId)));
            check(contest.status==200 && "OK".equals(contest.body.get("status")),"teacher creates contest directly from catalog without a pre-existing assignment");
            Map<String,Object> contestData=object(contest.body.get("data"));
            String contestId=str(object(contestData.get("contest")).get("id"));
            if(contestId.isBlank()) contestId=str(contestData.get("contestId"));
            check(!contestId.isBlank(),"created contest returns its identifier for access-control checks");
            Response studentLeaderboard=assign(client,base,student1,roomId,"LEADERBOARD_GET",Map.of("contestId",contestId));
            check(studentLeaderboard.status==400 && "FORBIDDEN".equals(studentLeaderboard.body.get("errorCode")),"student cannot query contest leaderboard via API");
            Response teacherLeaderboard=assign(client,base,teacher,roomId,"LEADERBOARD_GET",Map.of("contestId",contestId));
            check(teacherLeaderboard.status==200 && "OK".equals(teacherLeaderboard.body.get("status")),"teacher retains permission to query contest leaderboard");

            Response assignment=assign(client,base,teacher,roomId,"ASSIGN_CREATE",Map.of("exerciseId",scratchId,"scope","ROOM","deadlineMinutes",1440));
            check(assignment.status==200 && "OK".equals(assignment.body.get("status")),"teacher assigns exercise to current room");
            Map<String,Object> assignmentData=object(assignment.body.get("data"));
            String assignmentId=str(object(assignmentData.get("assignment")).get("id"));
            check(!assignmentId.isBlank(),"created assignment returns internal assignment identifier");
            Response studentList=assign(client,base,student1,roomId,"ASSIGN_LIST",Map.of());
            List<Object> studentAssignments=list(object(studentList.body.get("data")).get("assignments"));
            check(studentList.status==200 && studentAssignments.stream().map(WebGatewayIntegrationTest::object).anyMatch(a->assignmentId.equals(str(a.get("id")))),"student only receives assigned room tasks");
            Response progress=assign(client,base,teacher,roomId,"CLASS_PROGRESS",Map.of());
            List<Object> progressRows=list(object(progress.body.get("data")).get("assignments"));
            Map<String,Object> progressRow=progressRows.stream().map(WebGatewayIntegrationTest::object).filter(a->assignmentId.equals(str(a.get("assignmentId")))).findFirst().orElse(Map.of());
            check(number(progressRow.get("receivedCount"))==1,"class progress uses recipient snapshot captured at assignment time");

            Response studentTrial=request(client,base,"/api/assignments","POST",student1,Map.of("type","TEACHER_TRIAL_LIST"));
            check(studentTrial.status==400 && "FORBIDDEN".equals(studentTrial.body.get("errorCode")),"student cannot call teacher trial API");

            Response trial=assign(client,base,teacher,roomId,"TEACHER_TRIAL_CREATE",Map.of("exerciseId",scratchId,
                    "sourceCode","public class Main { public static void main(String[] args) { System.out.println(0); } }",
                    "idempotencyKey","web-test-trial-001"));
            check(trial.status==200 && "OK".equals(trial.body.get("status")),"teacher trial is accepted into the grading queue: HTTP "+trial.status+" "+trial.raw);
            String trialId=str(object(trial.body.get("data")).get("trial")==null ? "" : object(object(trial.body.get("data")).get("trial")).get("id"));
            check(!trialId.isBlank(),"teacher trial receives a separate trial identifier");
            Response trialList=assign(client,base,teacher,roomId,"TEACHER_TRIAL_LIST",Map.of());
            check(list(object(trialList.body.get("data")).get("trials")).stream().map(WebGatewayIntegrationTest::object)
                    .anyMatch(row->trialId.equals(str(row.get("id")))),"teacher trials are listed separately from student submissions");

            Response studentTurn=request(client,base,"/api/rooms/current/command","POST",student1,Map.of("action","REQUEST_EDIT"));
            check(studentTurn.status==202,"student edit request accepted");
            Response studentMe=request(client,base,"/api/me","GET",student1,null);
            String studentPeer=str(studentMe.body.get("peerId"));
            waitFor(()->{
                Map<String,Object> room=object(request(client,base,"/api/rooms/current","GET",student1,null).body.get("room"));
                return studentPeer.equals(str(room.get("editorPeerId"))) && number(room.get("token"))>0;
            },"student receives an edit turn and only their session sees its token");
            Map<String,Object> studentRoom=object(request(client,base,"/api/rooms/current","GET",student1,null).body.get("room"));
            long studentToken=number(studentRoom.get("token"));
            long roomVersion=number(studentRoom.get("version"));
            Response selected=request(client,base,"/api/rooms/current/command","POST",student1,
                    Map.of("action","SELECT_LINES","version",roomVersion,"token",studentToken,"selection","3-5"));
            check(selected.status==202,"student can select code lines for an edit draft");
            Response replaced=request(client,base,"/api/rooms/current/command","POST",student1,
                    Map.of("action","SET_RANGE","token",studentToken,"text","            total = 88;"));
            check(replaced.status==202,"student can create a recoverable shared-code draft");
            waitFor(()->Boolean.TRUE.equals(object(request(client,base,"/api/rooms/current","GET",student1,null).body.get("room")).get("draftActive")),
                    "shared edit draft becomes active");

            Response teacherTakeover=request(client,base,"/api/rooms/current/command","POST",teacher,Map.of("action","TEACHER_REQUEST_EDIT"));
            check(teacherTakeover.status==202,"teacher priority takeover is accepted");
            Response teacherMe=request(client,base,"/api/me","GET",teacher,null);
            String teacherPeer=str(teacherMe.body.get("peerId"));
            waitFor(()->{
                Map<String,Object> room=object(request(client,base,"/api/rooms/current","GET",teacher,null).body.get("room"));
                return teacherPeer.equals(str(room.get("editorPeerId"))) && number(room.get("token"))>0;
            },"teacher takes edit turn ahead of student");
            Map<String,Object> oldEditorRoom=object(request(client,base,"/api/rooms/current","GET",student1,null).body.get("room"));
            Map<String,Object> recovered=object(oldEditorRoom.get("recoverableDraft"));
            check(number(oldEditorRoom.get("token"))==0,"former editor no longer receives the active edit token");
            check(str(recovered.get("sourceCode")).contains("total = 88"),"teacher takeover preserves the student's exact draft privately");
            Response acknowledge=request(client,base,"/api/rooms/current/command","POST",student1,Map.of("action","ACK_DRAFT_RECOVERY"));
            check(acknowledge.status==200,"student can acknowledge private draft recovery");
            waitFor(()->object(request(client,base,"/api/rooms/current","GET",student1,null).body.get("room")).get("recoverableDraft") instanceof Map<?,?> map && map.isEmpty(),
                    "acknowledged draft recovery is cleared");

            Response hiddenReport=assign(client,base,student1,roomId,"CLASS_PROGRESS",Map.of());
            check(hiddenReport.status==400 && "FORBIDDEN".equals(hiddenReport.body.get("errorCode")),"student cannot read teacher class-progress reports");
            Response leave=request(client,base,"/api/rooms/current/command","POST",student1,Map.of("action","LEAVE_ROOM"));
            check(leave.status==200,"student can leave room synchronously");

            WebGatewayServer[] activeGateway={web};
            testHttpsGateway(trustAllHttpsClient(),"127.0.0.1",0,temp,core,assignments,activeGateway);
            web=activeGateway[0];
        } finally {
            if(web!=null)web.close(); if(assignments!=null)assignments.close(); if(core!=null)core.close();
        }
        System.out.println("WebGatewayIntegrationTest PASS: "+checks+" checks");
    }
}
