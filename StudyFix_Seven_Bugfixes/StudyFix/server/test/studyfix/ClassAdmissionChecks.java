package studyfix;

import java.util.*;
import java.net.*;
import java.io.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ManagementIntegrationTest.admin;
import static studyfix.assignment.protocol.AssignmentJson.*;

final class ClassAdmissionChecks {
    static void verify(Server core,WebGatewayServer web,ManagementDatabase sql,String base,String teacher,String suffix)throws Exception{
        String code="Join-"+suffix,user="join_"+suffix,secret="J9_"+UUID.randomUUID();
        String room=text(obj(admin(base,teacher,mapOf("action","CREATE_CLASS","roomCode",code)).body().get("data")),"classId","");
        String studentId=text(obj(admin(base,teacher,mapOf("action","CREATE_USER","username",user,"studentCode",user,"displayName","Sinh viên xin vào","password",secret)).body().get("data")),"userId","");
        String student=login(base,user,secret);
        String previousRoom=room(base,teacher),previousCode=core.rooms.get(previousRoom).getRoomCode();
        call(base,"/api/rooms/current/command",teacher,mapOf("action","LEAVE_ROOM"));join(base,teacher,code);
        check(call(base,"/api/classroom",teacher,mapOf("action","START","expectedPhase","WAITING","round",0)).status()==200,"teacher starts admission fixture lesson");
        Map<String,Object> directory=rows(call(base,"/api/rooms",student,null).body().get("rooms")).stream().filter(r->room.equals(r.get("roomId"))).findFirst().orElseThrow();
        check(directory.get("admissionStatus").equals("NONE"),"unassigned Student discovers open class without gaining access");
        Map<String,Object> request=mapOf("action","JOIN_ROOM","roomId",code);
        check(call(base,"/api/rooms/current/command",student,request).status()==403,"unassigned student cannot request classroom entry");
        check(admin(base,teacher,mapOf("action","ENROLL","classId",room,"userId",studentId)).status()==200,"teacher assigns student before entry request");
        Response sent=call(base,"/api/rooms/current/command",student,request);
        check(sent.status()==202,"join from assigned student creates pending admission");
        long first=number(obj(sent.body().get("admission")),"requestedAt",0);
        check(number(obj(call(base,"/api/rooms/current/command",student,request).body().get("admission")),"requestedAt",-1)==first,"duplicate pending requests are idempotent");
        check(call(base,"/api/rooms/current",student,null).status()==409,"pending request cannot access classroom");
        try(Socket socket=new Socket("127.0.0.1",core.port())){
            socket.setSoTimeout(4000);DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            Protocol.send(out,"/login "+user+" "+secret);
            for(int i=0;i<20;i++)if(Protocol.receive(in).contains("[LOGIN_OK]"))break;
            Protocol.send(out,"/join "+code);boolean denied=false;
            for(int i=0;i<20;i++){String deniedMessage=Protocol.receive(in);if(deniedMessage.contains("chưa được duyệt")||deniedMessage.contains("Phiên đăng nhập không còn khả dụng")){denied=true;break;}}
            check(denied,"raw TCP join cannot bypass pending admission");
        }
        WebSocketPresenceIntegrationTest.Listener notices=new WebSocketPresenceIntegrationTest.Listener();
        var socket=WebSocketPresenceIntegrationTest.connect(web,teacher,notices);notices.next("AUTH_OK");
        check(rows(notices.next("PRESENCE").get("joinRequests")).stream().anyMatch(r->studentId.equals(r.get("userId"))),"Teacher receives pending request via existing meeting WebSocket");socket.sendClose(1000,"done").join();
        String outsiderName="join_teacher_"+suffix;sql.createUser(outsiderName,null,"Teacher khác","TEACHER",secret.toCharArray());String outsider=login(base,outsiderName,secret);
        Map<String,Object> decision=mapOf("action","APPROVE_JOIN","classId",room,"requestId",obj(sent.body().get("admission")).get("requestId"));
        check(admin(base,outsider,decision).status()==403,"another Teacher cannot approve request");
        check(rows(obj(admin(base,outsider,null).body().get("data")).get("joinRequests")).isEmpty(),"other Teacher cannot see pending identities");
        check(admin(base,student,decision).status()==403,"Student cannot approve own admission");
        decision.put("action","REJECT_JOIN");check(admin(base,teacher,decision).status()==200,"owner can reject pending request");
        check(sql.allowedInClass(room,studentId),"rejection preserves durable enrollment");
        Map<String,Object> retry=obj(call(base,"/api/rooms/current/command",student,request).body().get("admission"));long second=number(retry,"requestedAt",0);
        check(second>first,"rejected Student may submit a new request");
        decision.put("action","APPROVE_JOIN");check(admin(base,teacher,decision).status()!=200,"old notification cannot approve a newer request");
        decision.put("requestId",retry.get("requestId"));check(admin(base,teacher,decision).status()==200,"owner approval enrolls Student");
        check(admin(base,teacher,decision).status()!=200,"already processed request cannot be decided twice");
        WebSocketPresenceIntegrationTest.Listener personal=new WebSocketPresenceIntegrationTest.Listener();
        var studentSocket=WebSocketPresenceIntegrationTest.connect(web,student,personal);personal.next("AUTH_OK");
        Map<String,Object> update=personal.next("ADMISSION_UPDATE");
        check(rows(update.get("admissions")).stream().anyMatch(r->room.equals(r.get("classId"))&&"APPROVED".equals(r.get("status"))),"Student receives personal approval on WebSocket");
        check(!stringify(update).contains("sessionId")&&!stringify(update).contains("ip"),"personal admission update excludes session and LAN metadata");studentSocket.sendClose(1000,"done").join();
        join(base,student,code);check(room.equals(room(base,student)),"approved Student enters existing classroom workflow");
        check(rows(obj(admin(base,teacher,null).body().get("data")).get("joinRequests")).stream().noneMatch(r->studentId.equals(r.get("userId"))),"processed request leaves Teacher waiting list");
        call(base,"/api/rooms/current/command",student,mapOf("action","LEAVE_ROOM"));
        check(sql.allowedInClass(room,studentId),"leaving meeting preserves durable class membership");
        Response reentry=call(base,"/api/rooms/current/command",student,request);
        check(reentry.status()==202&&call(base,"/api/rooms/current",student,null).status()==409,"already enrolled Student must be approved again after leaving");
        Map<String,Object> next=obj(reentry.body().get("admission"));
        check(!next.get("requestId").equals(retry.get("requestId")),"reentry has a new single-use request identity");
        check(admin(base,teacher,decision).status()!=200,"old approval notification cannot approve reentry");
        String device=login(base,user,secret);Map<String,Object> other=obj(call(base,"/api/rooms/current/command",device,request).body().get("admission"));
        check(!other.get("requestId").equals(next.get("requestId")),"each device needs its own approval request");
        decision.put("requestId",next.get("requestId"));check(admin(base,teacher,decision).status()==200,"owner approves only the requesting device");
        check(call(base,"/api/rooms/current/command",device,request).status()==202,"second device cannot reuse first device approval");
        join(base,student,code);call(base,"/api/auth/logout",student,Map.of());
        check(admin(base,teacher,mapOf("action","APPROVE_JOIN","classId",room,"requestId",next.get("requestId"))).status()!=200,"logout invalidates old login's admission");
        student=login(base,user,secret);Response newLogin=call(base,"/api/rooms/current/command",student,request);
        check(newLogin.status()==202,"new login must request approval even while enrolled");
        call(base,"/api/auth/logout",device,Map.of());
        check(admin(base,teacher,mapOf("action","APPROVE_JOIN","classId",room,"requestId",other.get("requestId"))).status()!=200,"offline device pending request cannot be approved");
        admin(base,teacher,mapOf("action","REMOVE_MEMBER","classId",room,"userId",studentId));
        check(call(base,"/api/rooms/current/command",student,request).status()==403,"removed student cannot request classroom entry");
        admin(base,teacher,mapOf("action","UPDATE_CLASS","classId",room,"name",code,"closed",true));
        check(call(base,"/api/rooms/current/command",student,request).status()!=202,"closed class cannot accept a new pending request");
        decision.put("requestedAt",number(rows(obj(admin(base,teacher,null).body().get("data")).get("members")).stream().findFirst().orElse(Map.of()),"enrolledAt",0));
        check(admin(base,teacher,decision).status()!=200,"closed class cannot approve stale admission");
        call(base,"/api/auth/logout",student,Map.of());
        call(base,"/api/rooms/current/command",teacher,mapOf("action","LEAVE_ROOM"));join(base,teacher,previousCode);
    }
}
