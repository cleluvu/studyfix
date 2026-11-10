package studyfix;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Teacher là Admin; không tạo role ADMIN thứ ba làm lệch quyền đang dùng. */
final class AdminManagement {
    private final Server core;private final MySqlManagement db;private final Consumer<String> revoke;
    private final BiConsumer<String,String> detach;
    private final Map<String,String> syncedClasses=new java.util.concurrent.ConcurrentHashMap<>();
    AdminManagement(Server core,Consumer<String> revoke,BiConsumer<String,String> detach){this.core=core;this.db=core.store().management();this.revoke=revoke;this.detach=detach;}
    Map<String,Object> view(Identity actor,String selected)throws SQLException{
        requireAdmin(actor);
        if(db==null)return mapOf("enabled",false,"database","sqlite","message","Trang quản trị cần STUDYFIX_MANAGEMENT_DB=mysql và cấu hình MySQL. Các chức năng lớp học cũ vẫn hoạt động.");
        syncClasses();Map<String,Object> result=db.dashboard(actor.userId(),selected);result.put("enabled",true);result.put("joinRequests",new ClassAdmission(db).pending(actor.userId()));
        List<Map<String,Object>> activity=new ArrayList<>();int activeRooms=0,lessons=0;
        for(Object item:(List<?>)result.get("classes")){
            Map<String,Object> row=map(item);String id=text(row,"classId","");Room room=core.rooms.get(id);
            ClassroomRepository.Session session=new ClassroomRepository(core.store()).session(id);
            row.put("phase",session.phase());row.put("round",session.round());row.put("lastLessonActivity",session.updatedAt());
            if(room!=null&&!room.memberDetails().isEmpty())activeRooms++;
            if(!Set.of("WAITING","ENDED").contains(session.phase()))lessons++;
            for(Map<String,Object> event:new ClassroomRepository(core.store()).report(id)){Map<String,Object> copy=new LinkedHashMap<>(event);copy.put("roomCode",row.get("roomCode"));activity.add(copy);}
        }
        activity.sort(Comparator.comparingLong(a->-number(a,"createdAt",0)));result.put("recentActivity",activity.stream().limit(15).toList());
        map(result.get("stats")).put("activeRooms",activeRooms);map(result.get("stats")).put("liveLessons",lessons);return result;
    }
    Map<String,Object> action(Identity actor,Map<String,Object> body)throws SQLException{
        requireAdmin(actor);if(db==null)throw new IllegalStateException("Chưa cấu hình MySQL.");
        String action=text(body,"action","");
        switch(action){
            case "APPROVE_JOIN","REJECT_JOIN"->new ClassAdmission(db).decide(actor.userId(),text(body,"classId",""),text(body,"requestId",""),action.equals("APPROVE_JOIN"));
            case "CREATE_USER"->{
                if(!text(body,"role","STUDENT").equals("STUDENT"))throw new SecurityException("Teacher chỉ được tạo tài khoản Student, không được cấp quyền Teacher.");
                MySqlManagement.Account account=db.createUser(text(body,"username","").trim(),text(body,"studentCode","").trim(),text(body,"displayName","").trim(),"STUDENT",text(body,"password","").toCharArray(),actor.userId());
                core.store().mirrorAccount(account);return mapOf("ok",true,"userId",account.identity().userId());
            }
            case "SET_ACTIVE"->{String id=text(body,"userId","");Object value=body.get("active");if(!(value instanceof Boolean))throw new IllegalArgumentException("active phải là boolean.");db.setActive(actor.userId(),id,(Boolean)value);if(!Boolean.TRUE.equals(value))revoke.accept(id);}
            case "RESET_PASSWORD"->{String id=text(body,"userId","");db.resetPassword(actor.userId(),id,text(body,"password","").toCharArray());revoke.accept(id);}
            case "CREATE_CLASS"->{Room room=core.createRoom(actor,RoomCode.normalize(text(body,"roomCode","")));syncClass(room);return mapOf("ok",true,"classId",room.getRoomId());}
            case "DELETE_CLASS"->{String id=text(body,"classId","");requireClass(actor,id);core.deleteRoom(actor,id);db.transaction(c->{try(var p=c.prepareStatement("UPDATE classes SET active=0 WHERE class_id=? AND owner_id=?")){p.setString(1,id);p.setString(2,actor.userId());p.executeUpdate();}return null;});}
            case "UPDATE_CLASS"->{
                String id=text(body,"classId",""),name=text(body,"name","").strip();if(name.isBlank()||name.length()>200)throw new IllegalArgumentException("Tên lớp dài 1–200 ký tự.");
                Object closed=body.get("closed");if(!(closed instanceof Boolean))throw new IllegalArgumentException("closed phải là boolean.");
                db.transaction(c->{MySqlTeacherRepository.requireOwner(c,actor.userId(),id);try(var p=c.prepareStatement("UPDATE classes SET name=?,closed=? WHERE class_id=? AND owner_id=? AND active=1")){p.setString(1,name);p.setBoolean(2,(Boolean)closed);p.setString(3,id);p.setString(4,actor.userId());if(p.executeUpdate()==0)throw new IllegalStateException("Lớp không còn hoạt động.");}if(Boolean.TRUE.equals(closed))try(var p=c.prepareStatement("UPDATE class_admissions SET status='CANCELLED' WHERE class_id=? AND status IN ('PENDING','APPROVED','ENTERED')")){p.setString(1,id);p.executeUpdate();}return null;});
                if(Boolean.TRUE.equals(closed))for(ClientHandler client:core.clients.values())client.leaveClassIfCurrent(id);
            }
            case "DISCONNECT_SESSION"->{
                String id=text(body,"classId",""),connection=text(body,"connectionId","");
                db.transaction(c->{MySqlTeacherRepository.requireOwner(c,actor.userId(),id);try(var p=c.prepareStatement("SELECT 1 FROM attendance a JOIN sessions s ON s.connection_id=a.connection_id JOIN users u ON u.user_id=s.user_id WHERE a.class_id=? AND a.connection_id=? AND a.left_at IS NULL AND s.status='ONLINE' AND u.role='STUDENT'")){p.setString(1,id);p.setString(2,connection);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Phiên không thuộc lớp đang quản lý.");}}return null;});
                detach.accept(connection,id);
            }
            case "ENROLL_CODE"->{
                String room=text(body,"classId","");requireClass(actor,room);
                String user=db.transaction(c->{try(var p=c.prepareStatement("SELECT user_id FROM users WHERE student_code=? AND role='STUDENT' AND active=1")){p.setString(1,text(body,"studentCode","").strip());try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không có sinh viên khả dụng với mã này.");return r.getString(1);}}});
                db.enrollment(actor.userId(),room,user,true);
            }
            case "ENROLL","REMOVE_MEMBER"->{
                String room=text(body,"classId",""),user=text(body,"userId","");boolean enroll=action.equals("ENROLL");db.enrollment(actor.userId(),room,user,enroll);
                if(!enroll)for(ClientHandler client:core.clients.values())if(client.identity()!=null&&client.identity().userId().equals(user))client.leaveClassIfCurrent(room);
            }
            default->throw new IllegalArgumentException("Thao tác quản trị không hợp lệ.");
        }
        return mapOf("ok",true);
    }
    void syncClass(Room room)throws SQLException{
        if(db==null)return;String signature=room.getRoomCode()+"|"+room.isExpired();
        if(signature.equals(syncedClasses.get(room.getRoomId())))return;
        db.syncClass(room.getRoomId(),room.getRoomCode(),room.snapshot().ownerId(),!room.isExpired());syncedClasses.put(room.getRoomId(),signature);
    }
    private void syncClasses()throws SQLException{for(Room room:core.rooms.values())syncClass(room);}
    private void requireClass(Identity actor,String room)throws SQLException{db.transaction(c->{MySqlTeacherRepository.requireOwner(c,actor.userId(),room);return null;});}
    static void requireAdmin(Identity actor){if(actor==null||actor.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ Teacher (Admin) được quản lý người dùng và lớp học.");}
}
