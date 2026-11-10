package studyfix;

import java.sql.*;
import java.util.*;

/** Nhật ký kết nối web trong database lõi. Session xác thực chỉ được lưu phía server. */
final class PresenceRepository {
    private final Store store;
    PresenceRepository(Store store){this.store=store;}
    static void migrate(Connection db)throws SQLException {
        Set<String> columns=new HashSet<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info(users)")){while(r.next())columns.add(r.getString("name"));}
        try(Statement s=db.createStatement()){
            if(!columns.contains("student_code"))s.executeUpdate("ALTER TABLE users ADD COLUMN student_code TEXT");
            // Giữ tương thích tài khoản đang dùng; không tự sinh MSSV không có trong dữ liệu.
            s.executeUpdate("UPDATE users SET student_code=username WHERE role='STUDENT' AND student_code IS NULL");
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS users_student_code ON users(student_code) WHERE student_code IS NOT NULL");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_sessions(connection_id TEXT PRIMARY KEY,session_id TEXT NOT NULL UNIQUE,user_id TEXT NOT NULL REFERENCES users(user_id),ip TEXT NOT NULL,login_time INTEGER NOT NULL,last_activity INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('ONLINE','OFFLINE')),offline_time INTEGER,reason TEXT NOT NULL DEFAULT '')");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS classroom_connections(room_id INTEGER NOT NULL REFERENCES rooms(room_id),connection_id TEXT NOT NULL REFERENCES network_sessions(connection_id),joined_at INTEGER NOT NULL,left_at INTEGER,PRIMARY KEY(room_id,connection_id))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS classroom_connections_room ON classroom_connections(room_id,joined_at)");
        }
    }
    void recover()throws SQLException {
        if(store.management()!=null){store.management().recover();return;}
        store.classroomTransaction(db->{try(Statement s=db.createStatement()){
            s.executeUpdate("UPDATE classroom_connections SET left_at=(SELECT last_activity FROM network_sessions n WHERE n.connection_id=classroom_connections.connection_id) WHERE left_at IS NULL");
            s.executeUpdate("UPDATE network_sessions SET status='OFFLINE',offline_time=last_activity,reason='SERVER_RESTART' WHERE status='ONLINE'");
        }return null;});
    }
    void login(String connection,String session,Identity user,String ip,long time)throws SQLException {
        if(store.management()!=null){store.management().login(connection,session,user,ip,time);return;}
        store.classroomTransaction(db->{try(PreparedStatement p=db.prepareStatement("INSERT INTO network_sessions(connection_id,session_id,user_id,ip,login_time,last_activity,status) VALUES(?,?,?,?,?,?,'ONLINE')")){
            p.setString(1,connection);p.setString(2,session);p.setString(3,user.userId());p.setString(4,ip);p.setLong(5,time);p.setLong(6,time);p.executeUpdate();
        }return null;});
    }
    void observe(String connection,String room,String ip,long activity)throws SQLException {
        if(store.management()!=null){store.management().observe(connection,room,ip,activity);return;}
        store.classroomTransaction(db->{
            try(PreparedStatement p=db.prepareStatement("UPDATE network_sessions SET ip=?,last_activity=MAX(last_activity,?) WHERE connection_id=? AND status='ONLINE'")){
                p.setString(1,ip);p.setLong(2,activity);p.setString(3,connection);if(p.executeUpdate()==0)return null;
            }
            try(PreparedStatement p=db.prepareStatement("UPDATE classroom_connections SET left_at=? WHERE connection_id=? AND left_at IS NULL AND CAST(room_id AS TEXT)<>?")){
                p.setLong(1,activity);p.setString(2,connection);p.setString(3,room);p.executeUpdate();
            }
            if(!room.isBlank())try(PreparedStatement p=db.prepareStatement("INSERT INTO classroom_connections(room_id,connection_id,joined_at) VALUES(?,?,?) ON CONFLICT(room_id,connection_id) DO UPDATE SET joined_at=CASE WHEN left_at IS NULL THEN joined_at ELSE excluded.joined_at END,left_at=NULL")){
                p.setString(1,room);p.setString(2,connection);p.setLong(3,activity);p.executeUpdate();
            }
            return null;
        });
    }
    void offline(String connection,long lastActivity,String reason)throws SQLException {
        if(store.management()!=null){store.management().offline(connection,lastActivity,reason);return;}
        store.classroomTransaction(db->{
            try(PreparedStatement p=db.prepareStatement("UPDATE network_sessions SET status='OFFLINE',last_activity=MAX(last_activity,?),offline_time=?,reason=? WHERE connection_id=? AND status='ONLINE'")){
                p.setLong(1,lastActivity);p.setLong(2,System.currentTimeMillis());p.setString(3,reason);p.setString(4,connection);p.executeUpdate();
            }
            try(PreparedStatement p=db.prepareStatement("UPDATE classroom_connections SET left_at=? WHERE connection_id=? AND left_at IS NULL")){
                p.setLong(1,lastActivity);p.setString(2,connection);p.executeUpdate();
            }return null;
        });
    }
    List<Map<String,Object>> students(String room)throws SQLException {
        if(store.management()!=null)return store.management().students(room);
        return store.classroomTransaction(db->{
            List<Map<String,Object>> rows=new ArrayList<>();
            String sql="SELECT n.connection_id,n.user_id,u.student_code,u.username,u.display_name,n.ip,n.login_time,n.last_activity,n.status,n.offline_time,n.reason,c.joined_at,c.left_at FROM classroom_connections c JOIN network_sessions n ON n.connection_id=c.connection_id JOIN users u ON u.user_id=n.user_id WHERE c.room_id=? AND u.role='STUDENT' ORDER BY CASE WHEN n.status='ONLINE' AND c.left_at IS NULL THEN 0 ELSE 1 END,u.student_code,n.login_time DESC";
            try(PreparedStatement p=db.prepareStatement(sql)){p.setString(1,room);try(ResultSet r=p.executeQuery()) {while(r.next()){
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("connectionId",r.getString(1));row.put("userId",r.getString(2));row.put("studentCode",Objects.requireNonNullElse(r.getString(3),r.getString(4)));row.put("displayName",r.getString(5));row.put("ip",r.getString(6));row.put("loginTime",r.getLong(7));row.put("lastActivity",r.getLong(8));
                boolean online=r.getString(9).equals("ONLINE")&&r.getObject(13)==null;
                row.put("online",online);row.put("status",online?"ONLINE":"OFFLINE");row.put("connectionStatus",r.getString(9));row.put("offlineTime",r.getObject(10));row.put("reason",r.getObject(13)!=null&&r.getString(9).equals("ONLINE")?"LEFT_ROOM":r.getString(11));row.put("joinedAt",r.getLong(12));row.put("leftAt",r.getObject(13));rows.add(row);
            }}}
            return rows;
        });
    }
    String studentCode(String user)throws SQLException {
        return store.classroomTransaction(db->{try(PreparedStatement p=db.prepareStatement("SELECT CASE WHEN role='STUDENT' THEN COALESCE(student_code,username) ELSE '' END FROM users WHERE user_id=?")){p.setString(1,user);try(ResultSet r=p.executeQuery()){return r.next()?Objects.requireNonNullElse(r.getString(1),""):"";}}});
    }
}
