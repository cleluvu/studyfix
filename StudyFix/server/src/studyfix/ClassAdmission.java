package studyfix;

import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.mapOf;

/** Membership is durable; admission is single-use and bound to one login session. */
final class ClassAdmission {
    private final MySqlManagement db;
    ClassAdmission(MySqlManagement db){this.db=db;}
    Map<String,Object> request(Identity student,String room,String session)throws SQLException{
        if(student.role()!=Identity.Role.STUDENT)throw new SecurityException("Chỉ sinh viên gửi yêu cầu vào lớp.");
        return db.transaction(c->{
            requireOpenStudent(c,room,student.userId());requireSession(c,student.userId(),session);
            String id="",status="",code="";long previous=0;
            try(PreparedStatement p=c.prepareStatement("SELECT room_code FROM classes WHERE class_id=?")){p.setString(1,room);try(ResultSet r=p.executeQuery()){r.next();code=r.getString(1);}}
            try(PreparedStatement p=c.prepareStatement("SELECT request_id,status,requested_at FROM class_admissions WHERE class_id=? AND session_id=? AND user_id=? FOR UPDATE")){
                p.setString(1,room);p.setString(2,session);p.setString(3,student.userId());try(ResultSet r=p.executeQuery()){if(r.next()){id=r.getString(1);status=r.getString(2);previous=r.getLong(3);}}
            }
            long requested=previous;
            if(!status.equals("APPROVED")&&!status.equals("PENDING")){
                id=UUID.randomUUID().toString();requested=Math.max(System.currentTimeMillis(),previous+1);
                try(PreparedStatement p=c.prepareStatement("INSERT INTO class_admissions(request_id,class_id,user_id,session_id,status,requested_at) VALUES(?,?,?,?,'PENDING',?) ON DUPLICATE KEY UPDATE request_id=VALUES(request_id),status='PENDING',requested_at=VALUES(requested_at)")){
                    p.setString(1,id);p.setString(2,room);p.setString(3,student.userId());p.setString(4,session);p.setLong(5,requested);p.executeUpdate();
                }status="PENDING";
            }
            return mapOf("requestId",id,"classId",room,"roomCode",code,"status",status,"requestedAt",requested);
        });
    }
    void decide(String teacher,String room,String request,boolean accepted)throws SQLException{
        db.transaction(c->{
            MySqlTeacherRepository.requireOwner(c,teacher,room);
            String user="",session="";
            try(PreparedStatement p=c.prepareStatement("SELECT user_id,session_id FROM class_admissions WHERE request_id=? AND class_id=?")){
                p.setString(1,request);p.setString(2,room);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Yêu cầu không còn tồn tại.");user=r.getString(1);session=r.getString(2);}
            }
            requireOpenStudent(c,room,user);requireSession(c,user,session);
            try(PreparedStatement p=c.prepareStatement("UPDATE class_admissions SET status=? WHERE request_id=? AND class_id=? AND status='PENDING'")){
                p.setString(1,accepted?"APPROVED":"REJECTED");p.setString(2,request);p.setString(3,room);
                if(p.executeUpdate()!=1)throw new IllegalStateException("Yêu cầu đã được xử lý hoặc đã thay đổi. Hãy làm mới danh sách.");
            }
            if(accepted)try(PreparedStatement p=c.prepareStatement("INSERT INTO class_members(class_id,user_id,membership_status,enrolled_at) VALUES(?,?,'ENROLLED',?) ON DUPLICATE KEY UPDATE membership_status='ENROLLED'")){
                p.setString(1,room);p.setString(2,user);p.setLong(3,System.currentTimeMillis());p.executeUpdate();
            }return null;
        });
    }
    boolean enter(String room,String user,String session)throws SQLException{return db.transaction(c->{
        requireOpenStudent(c,room,user);requireSession(c,user,session);
        try(PreparedStatement p=c.prepareStatement("UPDATE class_admissions a JOIN class_members m ON m.class_id=a.class_id AND m.user_id=a.user_id SET a.status='ENTERED' WHERE a.class_id=? AND a.user_id=? AND a.session_id=? AND a.status='APPROVED' AND m.membership_status='ENROLLED'")){
            p.setString(1,room);p.setString(2,user);p.setString(3,session);return p.executeUpdate()==1;
        }
    });}
    void leave(String room,String user,String session)throws SQLException{db.transaction(c->{
        try(PreparedStatement p=c.prepareStatement("UPDATE class_admissions SET status='LEFT' WHERE class_id=? AND user_id=? AND session_id=? AND status IN ('APPROVED','ENTERED')")){
            p.setString(1,room);p.setString(2,user);p.setString(3,session);p.executeUpdate();
        }return null;
    });}
    private static void requireOpenStudent(Connection c,String room,String student)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users u JOIN classes k ON k.class_id=? WHERE u.user_id=? AND u.role='STUDENT' AND u.active=1 AND k.active=1 AND k.closed=0 FOR UPDATE")){
            p.setString(1,room);p.setString(2,student);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Lớp đang đóng hoặc tài khoản không khả dụng.");}
        }
    }
    private static void requireSession(Connection c,String user,String session)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM sessions WHERE user_id=? AND session_id=? AND status='ONLINE' FOR UPDATE")){
            p.setString(1,user);p.setString(2,session);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Phiên đăng nhập không còn khả dụng.");}
        }
    }
    List<Map<String,Object>> studentStatus(String student,String session)throws SQLException{return db.transaction(c->{
        List<Map<String,Object>> result=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT a.request_id,a.class_id,k.room_code,a.status,k.active,k.closed FROM class_admissions a JOIN classes k ON k.class_id=a.class_id JOIN sessions s ON s.session_id=a.session_id WHERE a.user_id=? AND a.session_id=? AND s.status='ONLINE'")){
            p.setString(1,student);p.setString(2,session);try(ResultSet r=p.executeQuery()){while(r.next())result.add(mapOf("requestId",r.getString(1),"classId",r.getString(2),"roomCode",r.getString(3),"status",r.getBoolean(5)&&!r.getBoolean(6)?r.getString(4):"UNAVAILABLE"));}
        }return result;
    });}
    List<Map<String,Object>> pending(String teacher)throws SQLException{return db.transaction(c->{
        List<Map<String,Object>> result=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT a.request_id,a.class_id,k.room_code,u.user_id,u.student_code,u.display_name,a.requested_at FROM class_admissions a JOIN classes k ON k.class_id=a.class_id JOIN users u ON u.user_id=a.user_id JOIN sessions s ON s.session_id=a.session_id WHERE k.owner_id=? AND k.active=1 AND k.closed=0 AND u.active=1 AND u.role='STUDENT' AND s.status='ONLINE' AND a.status='PENDING' ORDER BY a.requested_at")){
            p.setString(1,teacher);try(ResultSet r=p.executeQuery()){while(r.next())result.add(mapOf("requestId",r.getString(1),"classId",r.getString(2),"roomCode",r.getString(3),"userId",r.getString(4),"studentCode",r.getString(5),"displayName",r.getString(6),"requestedAt",r.getLong(7)));}
        }return result;
    });}
}
