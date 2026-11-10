package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

public final class WebSocketPresenceIntegrationTest {
    static final class Listener implements WebSocket.Listener{
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();final StringBuilder text=new StringBuilder();volatile int closed;
        public void onOpen(WebSocket ws){ws.request(1);}
        public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){text.append(data);if(last){messages.add(text.toString());text.setLength(0);}ws.request(1);return null;}
        public CompletionStage<?> onClose(WebSocket ws,int status,String reason){closed=status;return null;}
        Map<String,Object> next(String type)throws Exception{long end=System.currentTimeMillis()+6000;while(System.currentTimeMillis()<end){String s=messages.poll(200,TimeUnit.MILLISECONDS);if(s!=null){Map<String,Object> m=object(s);if(type.equals(m.get("type")))return m;}}throw new AssertionError("Missing WS message "+type);}
    }
    static WebSocket connect(WebGatewayServer web,String token,Listener listener)throws Exception{
        WebSocket socket=HTTP.newWebSocketBuilder().header("Origin","http://127.0.0.1:"+web.port()).buildAsync(URI.create("ws://127.0.0.1:"+web.webSocketPort()+"/presence"),listener).get(5,TimeUnit.SECONDS);
        socket.sendText(stringify(mapOf("type","AUTH","token",token)),true).join();return socket;
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.sessionTimeoutMillis","3000");
        Path temp=Files.createTempDirectory(Path.of("build"),"ws-test-");
        try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();String teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");
            call(base,"/api/rooms/current/command",teacher,mapOf("action","CREATE_ROOM","roomCode","WS-class"));join(base,student,"WS-class");
            Listener t=new Listener(),s=new Listener();WebSocket teacherSocket=connect(web,teacher,t),studentSocket=connect(web,student,s);
            check(t.next("AUTH_OK")!=null&&s.next("AUTH_OK")!=null,"real RFC6455 authentication");
            Map<String,Object> pushed=t.next("PRESENCE");
            check(!rows(pushed.get("connections")).isEmpty()&&rows(pushed.get("connections")).getFirst().get("ip").equals("127.0.0.1"),"teacher receives actual private roster over WebSocket");
            studentSocket.sendText(stringify(mapOf("type","SUBSCRIBE","view","admin")),true).join();
            check(!s.next("ALIVE").containsKey("connections"),"student never receives private roster or admin payload");
            long until=System.currentTimeMillis()+4200;
            while(System.currentTimeMillis()<until){teacherSocket.sendText("{\"type\":\"HEARTBEAT\"}",true).join();studentSocket.sendText("{\"type\":\"HEARTBEAT\"}",true).join();Thread.sleep(450);}
            check(call(base,"/api/me",student,null).status()==200,"WebSocket heartbeat alone keeps HTTP session alive beyond timeout");
            Listener bad=new Listener();WebSocket invalid=connect(web,"invalid-token",bad);waitFor(()->bad.closed==1008,"invalid token rejected");invalid.abort();
            check(call(base,"/api/admin",student,null).status()==403,"student cannot access admin HTTP endpoint");
            call(base,"/api/auth/logout",student,Map.of());
            teacherSocket.sendText("{\"type\":\"HEARTBEAT\"}",true).join();
            waitFor(()->s.closed==1008,"revoked session loses WebSocket access");
            teacherSocket.sendClose(1000,"test done").join();studentSocket.abort();
            // Origin is enforced before accepting credentials.
            Listener foreign=new Listener();WebSocket rejected=HTTP.newWebSocketBuilder().header("Origin","http://untrusted.example").buildAsync(URI.create("ws://127.0.0.1:"+web.webSocketPort()+"/presence"),foreign).join();
            waitFor(()->foreign.closed==1008,"foreign origin rejected");rejected.abort();
            for(int i=0;i<10;i++)check(call(base,"/api/auth/login",null,mapOf("username","not_an_account","password","not-a-password")).status()==401,"invalid account attempt rejected");
            check(call(base,"/api/auth/login",null,mapOf("username","not_an_account","password","not-a-password")).status()==429,"account/IP login limit enforced");
            check(call(base,"/api/auth/login",null,mapOf("username","another_missing_account","password","not-a-password")).status()==401,"shared IP alone does not identify or block another account");
            System.out.println("WebSocketPresenceIntegrationTest: real WebSocket auth, privacy, heartbeat, logout and Origin PASS.");
        }
    }
}
