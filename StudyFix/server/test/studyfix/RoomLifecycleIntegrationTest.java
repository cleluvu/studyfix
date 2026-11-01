package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Callable;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Custom codes, fixed pagination and explicit owner-authorized deletion. */
public final class RoomLifecycleIntegrationTest {
    static void until(Callable<Boolean> condition,int seconds)throws Exception {
        long end=System.nanoTime()+seconds*1_000_000_000L;
        while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(50);}throw new AssertionError("Timed out waiting for room lifecycle");
    }
    static Response create(String base,String token,String code)throws Exception {
        return call(base,"/api/rooms/current/command",token,mapOf("action","CREATE_ROOM","roomCode",code));
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");
        Path temp=Files.createTempDirectory(Path.of("build"),"room-lifecycle-");Path db=temp.resolve("core.db");String archivedId;
        try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,32,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            String owner=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!"),observer=login(base,"student2","Student234!"),second=login(base,"teacher2","Teacher234!");
            check(call(base,"/api/rooms",null,null).status()==401,"room directory requires authentication");
            check(create(base,student,"Student room").status()==403,"students cannot create rooms");
            check(create(base,second,"").status()==400,"empty custom code rejected");
            check(create(base,second,"abc\n/quit").status()==400,"control characters cannot inject a protocol command");
            check(create(base,second,"x".repeat(81)).status()==400,"code length bounded");
            check(call(base,"/api/rooms/current/command",second,mapOf("action","CREATE_ROOM")).status()==400,"web creation never silently auto-numbers a room");
            Response first=create(base,owner,"  Lớp Java A  ");check(first.status()==200,"teacher creates custom code");
            String id=text(first.body(),"roomId","");archivedId=id;Room oldRoom=core.rooms.get(id);
            check("Lớp Java A".equals(first.body().get("roomCode")),"Unicode and spaces preserved, outer spaces trimmed");
            check(create(base,second,"lớp java a").status()==409,"active code duplicates are case-insensitive");
            check(create(base,second,java.text.Normalizer.normalize("Lớp Java A",java.text.Normalizer.Form.NFD)).status()==409,"Unicode normalization prevents duplicate names");
            join(base,student,"LỚP JAVA A");
            check(number(obj(call(base,"/api/rooms/current",student,null).body().get("room")),"version",-1)==0,"student joins by chosen code");
            check("Lớp Java A".equals(obj(call(base,"/api/rooms/current",student,null).body().get("room")).get("roomCode")),"workspace exposes public room code");
            check(rows(call(base,"/api/rooms",observer,null).body().get("rooms")).size()==1,"students see the active directory");
            check(number(call(base,"/api/rooms?q=LOP+JAVA",observer,null).body(),"total",0)==1,"search ignores Vietnamese accents and case");
            check(number(call(base,"/api/rooms?q=phong+lop+java",observer,null).body(),"total",0)==1,"search also accepts displayed room label including Phong prefix");
            check(call(base,"/api/rooms/current/command",student,mapOf("action","DELETE_ROOM","roomId",id)).status()==403,"student cannot delete room");
            check(call(base,"/api/rooms/current/command",second,mapOf("action","DELETE_ROOM","roomId",id)).status()==403,"another teacher cannot delete room");
            check(call(base,"/api/rooms/current/command",owner,mapOf("action","LEAVE_ROOM")).status()==200,"membership leave completes synchronously");
            check(call(base,"/api/rooms/current",owner,null).status()==409,"successful leave response already reflects detached membership");
            until(()->oldRoom.snapshot().members().size()==1,3);
            check(core.rooms.containsKey(id),"room remains while a student is still inside");
            call(base,"/api/rooms/current/command",student,mapOf("action","LEAVE_ROOM"));
            until(()->oldRoom.snapshot().members().isEmpty(),3);
            check(!oldRoom.isExpired()&&core.rooms.containsKey(id),"last departure preserves room");
            check(rows(call(base,"/api/rooms",observer,null).body().get("rooms")).size()==1,"empty room remains in directory");
            check(create(base,second,"Lớp Java A").status()==409,"empty retained room still reserves its code");
            join(base,student,"Lớp Java A");
            check(call(base,"/api/rooms/current/command",owner,mapOf("action","DELETE_ROOM","roomId",id)).status()==200,"owner can explicitly delete occupied room from outside");
            check(oldRoom.isExpired()&&core.findRoom("Lớp Java A")==null,"deleted room cannot be joined");
            check(call(base,"/api/rooms/current",student,null).status()==409,"deletion detaches student from room");
            check(call(base,"/api/me",student,null).status()==200,"deletion keeps student signed in");
            check(rows(call(base,"/api/events",student,null).body().get("events")).stream().anyMatch(e->text(e,"message","").equals("[ROOM_DELETED] "+id)),"member receives deletion notification");
            ClientHandler outsider=core.clients.values().stream().filter(c->c.identity()!=null&&c.identity().username().equals("student2")).findFirst().orElseThrow();
            boolean refused=false;try{oldRoom.addMember(outsider);}catch(IllegalStateException expected){refused=true;}
            check(refused,"stale room reference cannot resurrect a deleted room");
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());PreparedStatement p=c.prepareStatement("SELECT active,(SELECT count(*) FROM revisions WHERE room_id=rooms.room_id) FROM rooms WHERE room_id=?")) {
                p.setString(1,id);try(ResultSet r=p.executeQuery()){check(r.next()&&r.getInt(1)==0&&r.getInt(2)>0,"room archived without deleting code history");}
            }
            Response reused=create(base,second,"Lớp Java A");check(reused.status()==200,"empty room's public code can be reused");
            String reusedId=text(reused.body(),"roomId","");check(!reusedId.equals(id),"reused code receives a fresh internal identity");
            ClientHandler disconnected=core.clients.values().stream().filter(c->reusedId.equals(c.getCurrentRoomId())).findFirst().orElseThrow();disconnected.closeConnection();
            until(()->core.rooms.get(reusedId).snapshot().members().isEmpty(),3);
            check(core.rooms.containsKey(reusedId),"abrupt disconnect preserves empty room");
            String secondAgain=login(base,"teacher2","Teacher234!");
            check(call(base,"/api/rooms/current/command",secondAgain,mapOf("action","DELETE_ROOM","roomId",reusedId)).status()==200,"same owner can delete after signing in again");

            for(int i=0;i<7;i++) {
                check(create(base,owner,"Page "+i).status()==200,"create paginated fixture "+i);
                call(base,"/api/rooms/current/command",owner,mapOf("action","LEAVE_ROOM"));
                until(()->call(base,"/api/rooms/current",owner,null).status()==409,3);
            }
            Set<String> codes=new HashSet<>();
            for(int p=1;p<=2;p++) {
                Map<String,Object> directory=call(base,"/api/rooms?page="+p+"&pageSize=99",observer,null).body();
                check(number(directory,"total",0)==7&&number(directory,"totalPages",0)==2,"directory reports complete total and page count");
                check(number(directory,"pageSize",0)==5&&rows(directory.get("rooms")).size()==(p==1?5:2),"each page has at most five rooms even when different size requested");
                for(Map<String,Object> row:rows(directory.get("rooms")))check(codes.add(text(row,"roomCode","")),"pages do not repeat a room");
            }
            check(codes.size()==7,"all rooms accessible across pages, no four-room truncation");
            Map<String,Object> found=call(base,"/api/rooms?q=Page+6&page=999",observer,null).body();
            check(number(found,"total",0)==1&&number(found,"page",0)==1&&"Page 6".equals(rows(found.get("rooms")).getFirst().get("roomCode")),"search finds room beyond initial page and clamps page after filtering");
            check(number(call(base,"/api/rooms?q=PAGE",student,null).body(),"totalPages",0)==2,"filtered results retain five-room pagination for students");
            check(rows(call(base,"/api/rooms?q=not-found",observer,null).body().get("rooms")).isEmpty(),"unmatched search returns empty results");
            check(number(call(base,"/api/rooms?q=++",observer,null).body(),"total",0)==7,"clearing search returns entire directory");
            check(call(base,"/api/rooms?q="+"x".repeat(161),observer,null).status()==400,"search length bounded");
            check(number(call(base,"/api/rooms?page=999&pageSize=2",observer,null).body(),"page",0)==2,"out-of-range page clamps to last page");
            check(number(call(base,"/api/rooms?page=-1&pageSize=0",observer,null).body(),"pageSize",0)==5,"page size always five");

            String refreshing=owner;
            Response refreshRoom=create(base,refreshing,"Refresh safe");String refreshId=text(refreshRoom.body(),"roomId","");
            call(base,"/api/auth/departing",refreshing,Map.of());call(base,"/api/me",refreshing,null);Thread.sleep(8500);
            check(core.rooms.containsKey(refreshId),"requests from refreshed page cancel delayed departure");
            call(base,"/api/auth/departing",refreshing,Map.of());
            until(()->core.rooms.get(refreshId).snapshot().members().isEmpty(),12);
            check(core.rooms.containsKey(refreshId),"closing last browser tab preserves room");
            check(call(base,"/api/me",refreshing,null).status()==401,"closing last browser tab expires its session after grace period");
        }
        try(Server restarted=new Server(0,db)) {
            check(restarted.rooms.size()==8,"restart restores all retained empty rooms");
            check(restarted.findRoom("Page 0")!=null&&restarted.findRoom("Refresh safe")!=null,"custom codes survive restart");
            check(restarted.rooms.values().stream().allMatch(r->r.snapshot().members().isEmpty()),"restart does not restore stale memberships");
            check(restarted.findRoom("Lớp Java A")==null,"archived room remains unavailable after restart");
        }
        System.out.println("RoomLifecycleIntegrationTest: "+ClassroomIntegrationTest.checks+" checks PASS (manual deletion and persistent rooms).");
    }
}
