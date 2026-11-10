package studyfix;

import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Mọi truy vấn quản trị đều giới hạn theo owner_id; không có dashboard toàn hệ thống. */
final class MySqlTeacherRepository {
    private final MySqlManagement db;
    MySqlTeacherRepository(MySqlManagement db){this.db=db;}
    static void requireOwner(Connection c,String teacher,String room)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM classes WHERE class_id=? AND owner_id=?")){
            p.setString(1,room);p.setString(2,teacher);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SecurityException("Bạn không được quản lý lớp này.");}
        }
    }
    static boolean canManageStudent(Connection c,String teacher,String user)throws SQLException{
        String sql="SELECT 1 FROM users u WHERE u.user_id=? AND u.role='STUDENT' AND (u.created_by=? OR (u.created_by IS NULL AND EXISTS(SELECT 1 FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.user_id=u.user_id AND m.membership_status='ENROLLED' AND k.owner_id=?))) AND NOT EXISTS(SELECT 1 FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.user_id=u.user_id AND m.membership_status='ENROLLED' AND k.owner_id<>?)";
        try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,user);p.setString(2,teacher);p.setString(3,teacher);p.setString(4,teacher);try(ResultSet r=p.executeQuery()){return r.next();}}
    }
    static void requireManagedStudent(Connection c,String teacher,String user)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT user_id FROM users WHERE user_id=? FOR UPDATE")){p.setString(1,user);try(ResultSet ignored=p.executeQuery()){}}
        if(!canManageStudent(c,teacher,user))throw new SecurityException("Không được thay đổi tài khoản này. Tài khoản giảng viên hoặc sinh viên dùng chung cần quy trình quản lý riêng; bạn chỉ quản lý thành viên trong lớp của mình.");
    }
    Map<String,Object> dashboard(String teacher,String selected)throws SQLException{return db.transaction(c->{
        if(!selected.isBlank())requireOwner(c,teacher,selected);
        List<Map<String,Object>> users=new ArrayList<>(),classes=new ArrayList<>(),members=new ArrayList<>(),connections=new ArrayList<>(),attendance=new ArrayList<>();
        String userSql="SELECT u.user_id,u.username,u.student_code,u.display_name,u.active,(SELECT COUNT(*) FROM sessions s WHERE s.user_id=u.user_id AND s.status='ONLINE'),(SELECT MAX(last_activity) FROM sessions s WHERE s.user_id=u.user_id),(SELECT GROUP_CONCAT(k.room_code ORDER BY k.room_code SEPARATOR ', ') FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.user_id=u.user_id AND m.membership_status='ENROLLED' AND k.owner_id=? AND k.active=1) FROM users u WHERE u.role='STUDENT' AND ((u.created_by=? AND NOT EXISTS(SELECT 1 FROM class_members other_m JOIN classes other_c ON other_c.class_id=other_m.class_id WHERE other_m.user_id=u.user_id AND other_m.membership_status='ENROLLED' AND other_c.active=1 AND other_c.owner_id<>u.created_by)) OR EXISTS(SELECT 1 FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.user_id=u.user_id AND k.owner_id=? AND k.active=1 AND m.membership_status='ENROLLED')) ORDER BY u.student_code,u.username";
        try(PreparedStatement p=c.prepareStatement(userSql)){p.setString(1,teacher);p.setString(2,teacher);p.setString(3,teacher);try(ResultSet r=p.executeQuery()){while(r.next())users.add(mapOf("userId",r.getString(1),"username",r.getString(2),"studentCode",r.getString(3),"displayName",r.getString(4),"role","STUDENT","active",r.getBoolean(5),"onlineConnections",r.getInt(6),"lastActivity",r.getObject(7),"classNames",r.getString(8),"canManageAccount",canManageStudent(c,teacher,r.getString(1))));}}
        try(PreparedStatement p=c.prepareStatement("SELECT k.class_id,k.room_code,k.name,k.owner_id,k.active,k.closed,(SELECT COUNT(*) FROM class_members m JOIN users u ON u.user_id=m.user_id WHERE m.class_id=k.class_id AND m.membership_status='ENROLLED' AND u.role='STUDENT') FROM classes k WHERE k.owner_id=? ORDER BY k.active DESC,k.created_at DESC")){
            p.setString(1,teacher);try(ResultSet r=p.executeQuery()){while(r.next())classes.add(mapOf("classId",r.getString(1),"roomCode",r.getString(2),"name",r.getString(3),"ownerId",r.getString(4),"active",r.getBoolean(5),"closed",r.getBoolean(6),"memberCount",r.getInt(7)));}
        }
        if(!selected.isBlank())try(PreparedStatement p=c.prepareStatement("SELECT u.user_id,u.student_code,u.display_name,m.membership_status,m.enrolled_at FROM class_members m JOIN users u ON u.user_id=m.user_id JOIN classes k ON k.class_id=m.class_id WHERE m.class_id=? AND k.owner_id=? AND u.role='STUDENT' ORDER BY u.student_code")){
            p.setString(1,selected);p.setString(2,teacher);try(ResultSet r=p.executeQuery()){while(r.next())members.add(mapOf("userId",r.getString(1),"studentCode",r.getString(2),"displayName",r.getString(3),"membershipStatus",r.getString(4),"enrolledAt",r.getLong(5)));}
        }
        String connectionSql="SELECT a.class_id,k.room_code,k.name,s.connection_id,s.user_id,u.student_code,u.display_name,a.ip_snapshot,s.login_time,a.activity_snapshot,s.status,a.joined_at,a.left_at,a.visits_json,s.reason FROM attendance a JOIN classes k ON k.class_id=a.class_id JOIN sessions s ON s.connection_id=a.connection_id JOIN users u ON u.user_id=s.user_id WHERE k.owner_id=? AND u.role='STUDENT' AND (?='' OR a.class_id=?) ORDER BY s.login_time DESC";
        try(PreparedStatement p=c.prepareStatement(connectionSql)){p.setString(1,teacher);p.setString(2,selected);p.setString(3,selected);try(ResultSet r=p.executeQuery()){while(r.next()){
            boolean systemOnline=r.getString(11).equals("ONLINE"),inClass=systemOnline&&r.getObject(13)==null;
            Map<String,Object> row=mapOf("classId",r.getString(1),"roomCode",r.getString(2),"className",r.getString(3),"connectionId",r.getString(4),"userId",r.getString(5),"studentCode",r.getString(6),"displayName",r.getString(7),"ip",r.getString(8),"loginTime",r.getLong(9),"lastActivity",r.getLong(10),"systemOnline",systemOnline,"online",inClass,"status",inClass?"ONLINE":"OFFLINE","joinedAt",r.getLong(12),"leftAt",r.getObject(13),"reason",r.getString(15));
            connections.add(row);
            String json=r.getString(14);List<Object> visits=json==null?List.of():list(object("{\"visits\":"+json+"}").get("visits"));
            for(int i=0;i<visits.size();i++){
                Map<String,Object> visit=map(visits.get(i));long joined=number(visit,"joinedAt",r.getLong(12));Object left=visit.get("leftAt");
                long ended=left instanceof Number n?n.longValue():r.getLong(10);
                Map<String,Object> history=new LinkedHashMap<>(row);history.put("visit",i+1);history.put("joinedAt",joined);history.put("leftAt",left);history.put("durationSeconds",Math.max(0,(ended-joined)/1000));history.put("inClass",left==null&&systemOnline);attendance.add(history);
            }
        }}}
        List<Map<String,Object>> enrolled=users.stream().filter(u->!text(u,"classNames","").isBlank()).toList();
        long online=enrolled.stream().filter(u->((Number)u.get("onlineConnections")).intValue()>0).count();
        Map<String,Object> stats=mapOf("classes",classes.stream().filter(k->Boolean.TRUE.equals(k.get("active"))).count(),"students",enrolled.size(),"online",online,"offline",enrolled.size()-online,"inClass",connections.stream().filter(k->Boolean.TRUE.equals(k.get("online"))).map(k->k.get("userId")).distinct().count());
        return mapOf("database","mysql","users",users,"classes",classes,"members",members,"connections",connections,"attendance",attendance,"stats",stats,"selectedClass",selected);
    });}
}
