package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real authenticated HTTP/SQLite counts, independent of the 80-message UI window. */
public final class ChatNotificationIntegrationTest {
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");
        Path temp=Files.createTempDirectory(Path.of("build"),"chat-notifications-");
        try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,32,1,"unused-test");WebGatewayServer web=new WebGatewayServer(core,assignments)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            check(call(base,"/api/rooms/current/chat",null,null).status()==401,"anonymous chat read rejected");
            String t=login(base,"teacher","Teacher123!"),s=login(base,"student1","Student123!"),other=login(base,"teacher2","Teacher234!");
            check(call(base,"/api/rooms/current/chat",s,null).status()==409,"chat read requires current room");
            call(base,"/api/rooms/current/command",t,mapOf("action","CREATE_ROOM","roomCode","Test "+UUID.randomUUID()));
            waitFor(()->call(base,"/api/rooms/current",t,null).status()==200,"teacher creates room");String room=room(base,t);join(base,s,room);
            call(base,"/api/rooms/current/command",other,mapOf("action","CREATE_ROOM","roomCode","Test "+UUID.randomUUID()));
            waitFor(()->call(base,"/api/rooms/current",other,null).status()==200,"other room created");
            check(number(call(base,"/api/rooms/current/chat",t,null).body(),"unreadCount",-1)==0,"empty room has no unread messages");
            call(base,"/api/rooms/current/command",t,mapOf("action","CHAT","text","Teacher message"));
            waitFor(()->number(call(base,"/api/rooms/current/chat",s,null).body(),"unreadCount",0)==1,"student receives teacher message");
            check(number(call(base,"/api/rooms/current/chat",t,null).body(),"unreadCount",-1)==0,"sender's own message excluded");
            call(base,"/api/rooms/current/command",s,mapOf("action","CHAT","text","Student reply"));
            waitFor(()->number(call(base,"/api/rooms/current/chat",t,null).body(),"latestId",0)==2,"student reply saved");
            Map<String,Object> status=call(base,"/api/rooms/current/chat",t,null).body();
            check(number(status,"unreadCount",0)==1,"teacher counts only student reply");
            String studentId=text(obj(call(base,"/api/me",s,null).body().get("identity")),"userId","");
            check(rows(status.get("messages")).stream().anyMatch(m->studentId.equals(m.get("userId"))),"chat response carries stable author ID");
            long readThrough=number(status,"latestId",0);
            check(number(call(base,"/api/rooms/current/chat?after="+readThrough,t,null).body(),"unreadCount",-1)==0,"cursor clears messages read through latest ID");
            Identity teacher=core.clients.values().stream().map(ClientHandler::identity).filter(Objects::nonNull).filter(i->i.username().equals("teacher")).findFirst().orElseThrow();
            Identity student=core.clients.values().stream().map(ClientHandler::identity).filter(Objects::nonNull).filter(i->i.username().equals("student1")).findFirst().orElseThrow();
            ClassroomRepository repo=new ClassroomRepository(core.store());
            repo.appendChat(room,new Identity(student.userId(),student.username(),teacher.displayName(),student.role()),"Same name, different account");
            check(number(call(base,"/api/rooms/current/chat?after="+readThrough,t,null).body(),"unreadCount",0)==1,"duplicate display names do not suppress another author's unread message");
            for(int i=0;i<90;i++)repo.appendChat(room,teacher,"Unread "+i);
            status=call(base,"/api/rooms/current/chat?after="+readThrough,s,null).body();
            check(rows(status.get("messages")).size()==80,"display history remains bounded to 80 messages");
            check(number(status,"unreadCount",0)==90,"unread count includes all 90 messages beyond display window");
            check(number(call(base,"/api/rooms/current/chat?after="+readThrough+"&userId="+teacher.userId(),s,null).body(),"unreadCount",0)==90,"client cannot forge identity used to exclude own messages");
            check(rows(call(base,"/api/rooms/current/chat",other,null).body().get("messages")).isEmpty(),"different room cannot see chat from this room");
            long latest=number(status,"latestId",0);
            check(number(call(base,"/api/rooms/current/chat?after="+latest,s,null).body(),"unreadCount",-1)==0,"reading all messages gives zero unread");
        }
        System.out.println("ChatNotificationIntegrationTest: "+ClassroomIntegrationTest.checks+" checks PASS (real HTTP/SQLite).");
    }
}
