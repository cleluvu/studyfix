package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP room isolation and signaling tests; does not claim real camera/audio media E2E. */
public final class MeetingCallIntegrationTest {
    static Response send(String base,String token,String kind,Map<String,Object> fields)throws Exception {
        Map<String,Object> body=new LinkedHashMap<>(fields);body.put("kind",kind);return call(base,"/api/call/signal",token,body);
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");
        Path temp=Files.createTempDirectory(Path.of("build"),"meeting-call-");
        try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,32,1,"test-image");WebGatewayServer web=new WebGatewayServer(core,assignments)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            check(call(base,"/api/call/signals",null,null).status()==401,"call poll requires authentication");
            String t=login(base,"teacher","Teacher123!"),a=login(base,"student1","Student123!"),b=login(base,"student2","Student234!"),f=login(base,"teacher2","Teacher234!");
            check(send(base,a,"JOIN",Map.of()).status()==409,"call join requires current room");
            call(base,"/api/rooms/current/command",t,mapOf("action","CREATE_ROOM","roomCode","Test "+UUID.randomUUID()));
            waitFor(()->call(base,"/api/rooms/current",t,null).status()==200,"teacher room created");
            String room=room(base,t);join(base,a,room);join(base,b,room);
            call(base,"/api/rooms/current/command",f,mapOf("action","CREATE_ROOM","roomCode","Test "+UUID.randomUUID()));
            waitFor(()->call(base,"/api/rooms/current",f,null).status()==200,"foreign room created");
            String tp=text(call(base,"/api/me",t,null).body(),"peerId",""),ap=text(call(base,"/api/me",a,null).body(),"peerId",""),bp=text(call(base,"/api/me",b,null).body(),"peerId",""),fp=text(call(base,"/api/me",f,null).body(),"peerId","");
            check(send(base,t,"OFFER",mapOf("toPeerId",ap,"payload","{}" )).status()==403,"signaling rejected before explicit call join");
            check(send(base,t,"JOIN",mapOf("audio",true,"video",false)).status()==200,"teacher joins audio");
            check(send(base,a,"JOIN",mapOf("audio",false,"video",true)).status()==200,"student joins camera");
            check(send(base,f,"JOIN",mapOf("audio",true)).status()==200,"second room has own call");
            List<Map<String,Object>> roster=rows(call(base,"/api/call/signals",t,null).body().get("peers"));
            check(roster.size()==2,"roster restricted to current room");
            check(roster.stream().noneMatch(x->fp.equals(x.get("peerId"))),"foreign participant hidden");
            check(roster.stream().anyMatch(x->ap.equals(x.get("peerId"))&&Boolean.TRUE.equals(x.get("video"))),"camera state returned");
            check(send(base,t,"OFFER",mapOf("toPeerId",fp,"payload","{}" )).status()==403,"cross-room offer rejected");
            check(send(base,t,"OFFER",mapOf("toPeerId",bp,"payload","{}" )).status()==403,"receiver must also opt in");
            check(send(base,t,"OFFER",mapOf("toPeerId",ap,"fromPeerId",fp,"payload","{\"type\":\"offer\",\"sdp\":\"test\"}" )).status()==200,"offer routed");
            Map<String,Object> packet=rows(call(base,"/api/call/signals",a,null).body().get("signals")).getFirst();
            check(tp.equals(packet.get("fromPeerId")),"sender identity cannot be forged");
            check("OFFER".equals(packet.get("kind")),"offer payload delivered");
            check(rows(call(base,"/api/call/signals?after="+((Number)packet.get("id")).longValue(),a,null).body().get("signals")).isEmpty(),"cursor acknowledges delivered packets");
            check(send(base,a,"ANSWER",mapOf("toPeerId",tp,"payload","{\"type\":\"answer\"}" )).status()==200,"answer routed back");
            check(send(base,a,"ICE",mapOf("toPeerId",tp,"payload","{\"candidate\":\"test\"}" )).status()==200,"ICE routed");
            check(rows(call(base,"/api/call/signals",t,null).body().get("signals")).size()==2,"answer and ICE delivered");
            check(send(base,a,"STATE",mapOf("audio",true,"video",false)).status()==200,"mic and camera state changes");
            check(send(base,a,"INVALID",Map.of()).status()==400,"unknown signal rejected");
            check(send(base,a,"ICE",mapOf("toPeerId",tp,"payload","x".repeat(96001))).status()==400,"payload bounded");
            check(send(base,a,"LEAVE",Map.of()).status()==200,"explicit leave succeeds");
            check(call(base,"/api/rooms/current",a,null).status()==200,"leaving call preserves room membership");
            check(rows(call(base,"/api/call/signals",t,null).body().get("signals")).isEmpty(),"leaving clears pending signals from sender");
            check(rows(call(base,"/api/call/signals",t,null).body().get("peers")).size()==1,"leaving removes participant");
            check(send(base,a,"STATE",mapOf("audio",true)).status()==403,"left participant cannot update media");
            send(base,b,"JOIN",Map.of());call(base,"/api/rooms/current/command",b,mapOf("action","LEAVE_ROOM"));
            waitFor(()->rows(call(base,"/api/call/signals",t,null).body().get("peers")).size()==1,"room departure removes call participant");
            call(base,"/api/auth/logout",t,Map.of());
            check(call(base,"/api/call/signals",t,null).status()==401,"logout ends call authorization");
        }
        MeetingCalls registry=new MeetingCalls();Identity identity=new Identity("test-user","test","Test",Identity.Role.STUDENT);
        for(int i=0;i<16;i++)registry.join("p"+i,"room",identity,false,false);
        boolean full=false;try{registry.join("p16","room",identity,false,false);}catch(IllegalStateException expected){full=true;}
        check(full,"mesh call bounded to 16 devices");
        registry.leave("p0");registry.join("p16","room",identity,false,false);
        check(rows(registry.poll("p16","room",0).get("peers")).size()==16,"slot reusable after departure");
        registry.prune((id,room)->false);
        check(rows(registry.poll("p16","room",0).get("peers")).isEmpty(),"stale membership pruned");
        System.out.println("MeetingCallIntegrationTest: "+ClassroomIntegrationTest.checks+" checks PASS; signaling only, real media not tested.");
    }
}
