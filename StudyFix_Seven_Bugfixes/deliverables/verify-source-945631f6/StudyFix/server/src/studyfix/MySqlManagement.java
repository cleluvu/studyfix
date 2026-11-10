package studyfix;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.assignment.protocol.AssignmentJson.mapOf;

/** MySQL là nguồn tài khoản và quản lý LAN; SQLite giữ nghiệp vụ code hiện có. */
final class MySqlManagement {
    private final ManagementConfig config;
    private final Semaphore connections=new Semaphore(8,true);
    record Account(Identity identity,String hash,String studentCode,boolean active){}
    @FunctionalInterface interface Work<T>{T run(Connection c)throws SQLException;}
    MySqlManagement(ManagementConfig config)throws SQLException{
        this.config=config;
        try{Class.forName("com.mysql.cj.jdbc.Driver");}catch(ClassNotFoundException e){throw new SQLException("Thiếu mysql-connector-j trong server/lib.");}
        migrate();
    }
    <T>T transaction(Work<T> work)throws SQLException{
        boolean acquired=false;
        try{
            acquired=connections.tryAcquire(3,TimeUnit.SECONDS);if(!acquired)throw new SQLException("MySQL đang bận.");
            Properties props=new Properties();props.setProperty("user",config.user);props.setProperty("password",config.password);props.setProperty("connectTimeout","4000");props.setProperty("socketTimeout","8000");
            try(Connection c=DriverManager.getConnection(config.url,props)){
                c.setAutoCommit(false);
                try{T result=work.run(c);c.commit();return result;}catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException ignored){}throw e;}
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new SQLException("MySQL request interrupted.",e);}
        finally{if(acquired)connections.release();}
    }
    private void migrate()throws SQLException{
        String sql;
        try(InputStream resource=MySqlManagement.class.getResourceAsStream("/studyfix/db/mysql-schema.sql")){
            sql=resource==null?Files.readString(Path.of("src/studyfix/db/mysql-schema.sql"),StandardCharsets.UTF_8):new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }catch(IOException e){throw new SQLException("Thiếu mysql-schema.sql trong JAR/source.",e);}
        transaction(c->{try(Statement s=c.createStatement()){for(String part:sql.replaceAll("(?m)^--.*$","").split(";"))if(!part.isBlank())s.execute(part);}return null;});
        transaction(c->{
            boolean pending=false;
            try(PreparedStatement p=c.prepareStatement("SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='class_members' AND COLUMN_NAME='membership_status'");ResultSet r=p.executeQuery()){pending=r.next()&&r.getString(1).contains("'PENDING'");}
            if(!pending)try(Statement p=c.createStatement()){p.execute("ALTER TABLE class_members MODIFY membership_status ENUM('ENROLLED','REMOVED','PENDING','REJECTED') NOT NULL DEFAULT 'ENROLLED'");}
            ensureColumn(c,"users","created_by","VARCHAR(36) NULL");ensureColumn(c,"classes","closed","BOOLEAN NOT NULL DEFAULT FALSE");ensureColumn(c,"attendance","visits_json","JSON NULL");
            ensureColumn(c,"attendance","ip_snapshot","VARCHAR(45) NOT NULL DEFAULT ''");ensureColumn(c,"attendance","activity_snapshot","BIGINT NOT NULL DEFAULT 0");
            try(Statement p=c.createStatement()){
                p.executeUpdate("UPDATE attendance SET visits_json=JSON_ARRAY(JSON_OBJECT('joinedAt',joined_at,'leftAt',left_at)) WHERE visits_json IS NULL");
                p.executeUpdate("UPDATE attendance a JOIN sessions s ON s.connection_id=a.connection_id SET a.ip_snapshot='',a.activity_snapshot=COALESCE(a.left_at,s.last_activity) WHERE a.activity_snapshot=0");
                p.executeUpdate("INSERT IGNORE INTO schema_migrations(version,applied_at) VALUES(2,UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000)");
                p.executeUpdate("INSERT IGNORE INTO schema_migrations(version,applied_at) VALUES(3,UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000)");
                p.executeUpdate("UPDATE class_members SET membership_status='REMOVED' WHERE membership_status='PENDING'");
                p.executeUpdate("INSERT IGNORE INTO schema_migrations(version,applied_at) VALUES(4,UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000)");
            }return null;
        });
    }
    private static void ensureColumn(Connection c,String table,String column,String type)throws SQLException{
        try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,table,column)){while(r.next())if(column.equalsIgnoreCase(r.getString("COLUMN_NAME")))return;}
        try(Statement p=c.createStatement()){p.execute("ALTER TABLE "+table+" ADD COLUMN "+column+" "+type);}
    }
    void bindCore(String coreId)throws SQLException{
        transaction(c->{
            try(PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO runtime_binding(binding_key,binding_value) VALUES('sqlite_core_id',?)")){p.setString(1,coreId);p.executeUpdate();}
            try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT binding_value FROM runtime_binding WHERE binding_key='sqlite_core_id' FOR UPDATE")){
                if(!r.next()||!coreId.equals(r.getString(1)))throw new SQLException("MySQL đã liên kết với SQLite lõi khác. Giữ đúng file core hoặc dùng database MySQL riêng; không tự ghép roomId của hai dự án.");
            }return null;
        });
    }
    Account authenticate(String login,char[] password)throws SQLException{
        try{
            Account found=transaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT user_id,username,display_name,role,password_hash,student_code,active FROM users WHERE username=? OR student_code=?")){
                p.setString(1,login);p.setString(2,login);try(ResultSet r=p.executeQuery()){
                    if(!r.next())return null;Account a=account(r);if(r.next())return null;return a;
                }
            }});
            return found!=null&&found.active()&&Passwords.verify(password,found.hash())?found:null;
        }finally{Arrays.fill(password,'\0');}
    }
    private static Account account(ResultSet r)throws SQLException{return new Account(new Identity(r.getString(1),r.getString(2),r.getString(3),Identity.Role.valueOf(r.getString(4))),r.getString(5),r.getString(6),r.getBoolean(7));}
    boolean active(String id)throws SQLException{return transaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT active FROM users WHERE user_id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()&&r.getBoolean(1);}}});}
    List<Account> accounts()throws SQLException{return transaction(c->{List<Account> rows=new ArrayList<>();try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT user_id,username,display_name,role,password_hash,student_code,active FROM users")){while(r.next())rows.add(account(r));}return rows;});}
    void importAccount(Account a)throws SQLException{
        transaction(c->{
            try(PreparedStatement p=c.prepareStatement("SELECT user_id FROM users WHERE user_id=? OR username=? OR (student_code IS NOT NULL AND student_code=?)")){
                p.setString(1,a.identity().userId());p.setString(2,a.identity().username());p.setString(3,a.studentCode());
                try(ResultSet r=p.executeQuery()){if(r.next()){if(!r.getString(1).equals(a.identity().userId()))throw new SQLException("Tài khoản MySQL trùng tên/mã nhưng khác userId; cần đối chiếu trước khi import.");return null;}}
            }
            insertAccount(c,a);return null;
        });
    }
    private static void insertAccount(Connection c,Account a)throws SQLException{
        insertAccount(c,a,null);
    }
    private static void insertAccount(Connection c,Account a,String creator)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,display_name,role,password_hash,student_code,active,created_at,created_by) VALUES(?,?,?,?,?,?,?,?,?)")){
            p.setString(1,a.identity().userId());p.setString(2,a.identity().username());p.setString(3,a.identity().displayName());p.setString(4,a.identity().role().name());p.setString(5,a.hash());p.setString(6,a.studentCode());p.setBoolean(7,a.active());p.setLong(8,System.currentTimeMillis());p.setString(9,creator);p.executeUpdate();
        }
    }
    Account createUser(String username,String code,String name,String role,char[] password)throws SQLException{
        return createUser(username,code,name,role,password,null);
    }
    Account createUser(String username,String code,String name,String role,char[] password,String creator)throws SQLException{
        try{
            if(!username.matches("[A-Za-z0-9_.-]{1,64}")||name.isBlank()||name.length()>200||password.length<12||password.length>256)throw new IllegalArgumentException("Tên tài khoản 1–64 ký tự chữ/số/._-, họ tên bắt buộc, mật khẩu 12–256 ký tự.");
            Identity.Role parsed=Identity.Role.valueOf(role);
            if(parsed==Identity.Role.STUDENT&&(code==null||!code.matches("[A-Za-z0-9_.-]{1,64}")))throw new IllegalArgumentException("Mã sinh viên 1–64 ký tự chữ/số/._-.");
            String studentCode=parsed==Identity.Role.STUDENT?code:null;
            Account a=new Account(new Identity(UUID.randomUUID().toString(),username,name,parsed),Passwords.hash(password),studentCode,true);
            transaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE username IN (?,?) OR student_code IN (?,?)")){p.setString(1,username);p.setString(2,studentCode);p.setString(3,username);p.setString(4,studentCode);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("Tài khoản hoặc mã sinh viên đã tồn tại.");}}insertAccount(c,a,creator);return null;});return a;
        }finally{Arrays.fill(password,'\0');}
    }
    void setActive(String teacher,String id,boolean active)throws SQLException{transaction(c->{MySqlTeacherRepository.requireManagedStudent(c,teacher,id);try(PreparedStatement p=c.prepareStatement("UPDATE users SET active=? WHERE user_id=?")){p.setBoolean(1,active);p.setString(2,id);if(p.executeUpdate()==0)throw new IllegalArgumentException("Không tìm thấy người dùng.");}return null;});}
    void resetPassword(String teacher,String id,char[] secret)throws SQLException{
        try{if(secret.length<12||secret.length>256)throw new IllegalArgumentException("Mật khẩu cần 12–256 ký tự.");String hash=Passwords.hash(secret);transaction(c->{MySqlTeacherRepository.requireManagedStudent(c,teacher,id);try(PreparedStatement p=c.prepareStatement("UPDATE users SET password_hash=? WHERE user_id=?")){p.setString(1,hash);p.setString(2,id);if(p.executeUpdate()==0)throw new IllegalArgumentException("Không tìm thấy người dùng.");}return null;});}finally{Arrays.fill(secret,'\0');}
    }
    void bootstrap()throws SQLException{
        if(accounts().stream().anyMatch(a->a.identity().role()==Identity.Role.TEACHER))return;
        String user=System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"),secret=System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD");
        if(user!=null&&secret!=null)createUser(user,null,System.getenv().getOrDefault("STUDYFIX_BOOTSTRAP_NAME","Giảng viên quản trị"),"TEACHER",secret.toCharArray());
    }
    void syncClass(String id,String code,String owner,boolean active)throws SQLException{
        transaction(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO classes(class_id,room_code,name,owner_id,active,created_at) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE room_code=VALUES(room_code),owner_id=VALUES(owner_id),active=VALUES(active)")){
            p.setString(1,id);p.setString(2,code);p.setString(3,"Phòng "+code);p.setString(4,owner);p.setBoolean(5,active);p.setLong(6,System.currentTimeMillis());p.executeUpdate();}return null;});
    }
    void enrollment(String teacher,String room,String user,boolean enrolled)throws SQLException{transaction(c->{
        MySqlTeacherRepository.requireOwner(c,teacher,room);
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users u JOIN classes c ON c.class_id=? WHERE u.user_id=? AND u.role='STUDENT' AND (u.active=1 OR ?=0) AND c.active=1 FOR UPDATE")){p.setString(1,room);p.setString(2,user);p.setBoolean(3,enrolled);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Lớp hoặc sinh viên không khả dụng.");}}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO class_members(class_id,user_id,membership_status,enrolled_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE membership_status=VALUES(membership_status),enrolled_at=VALUES(enrolled_at)")){p.setString(1,room);p.setString(2,user);p.setString(3,enrolled?"ENROLLED":"REMOVED");p.setLong(4,System.currentTimeMillis());p.executeUpdate();}
        if(!enrolled)try(PreparedStatement p=c.prepareStatement("UPDATE class_admissions SET status='CANCELLED' WHERE class_id=? AND user_id=? AND status IN ('PENDING','APPROVED','ENTERED')")){p.setString(1,room);p.setString(2,user);p.executeUpdate();}return null;
    });}
    boolean allowedInClass(String room,String user)throws SQLException{return transaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.class_id=? AND m.user_id=? AND m.membership_status='ENROLLED' AND k.active=1 AND k.closed=0")){p.setString(1,room);p.setString(2,user);try(ResultSet r=p.executeQuery()){return r.next();}}});}
    boolean classOpen(String room)throws SQLException{return transaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM classes WHERE class_id=? AND active=1 AND closed=0")){p.setString(1,room);try(ResultSet r=p.executeQuery()){return r.next();}}});}
    Set<String> visibleClasses(Identity who)throws SQLException{return transaction(c->{
        Set<String> ids=new HashSet<>();boolean teacher=who.role()==Identity.Role.TEACHER;String sql=teacher?"SELECT class_id FROM classes WHERE owner_id=? AND active=1 AND closed=0":"SELECT class_id FROM classes WHERE active=1 AND closed=0";
        try(PreparedStatement p=c.prepareStatement(sql)){if(teacher)p.setString(1,who.userId());try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}}return ids;
    });}
    void recover()throws SQLException{transaction(c->{try(Statement p=c.createStatement()){
        p.executeUpdate("UPDATE attendance a JOIN sessions s ON s.connection_id=a.connection_id SET a.visits_json=JSON_SET(a.visits_json,CONCAT('$[',JSON_LENGTH(a.visits_json)-1,'].leftAt'),s.last_activity),a.activity_snapshot=GREATEST(a.activity_snapshot,s.last_activity),a.left_at=s.last_activity WHERE a.left_at IS NULL");
        p.executeUpdate("UPDATE sessions SET status='OFFLINE',offline_time=last_activity,reason='SERVER_RESTART' WHERE status='ONLINE'");
        p.executeUpdate("UPDATE class_admissions SET status='CANCELLED' WHERE status IN ('PENDING','APPROVED','ENTERED')");
    }return null;});}
    void login(String connection,String session,Identity who,String ip,long time)throws SQLException{transaction(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO sessions(connection_id,session_id,user_id,ip,login_time,last_activity,status) VALUES(?,?,?,?,?,?,'ONLINE')")){p.setString(1,connection);p.setString(2,session);p.setString(3,who.userId());p.setString(4,ip);p.setLong(5,time);p.setLong(6,time);p.executeUpdate();}return null;});}
    void importLegacySession(String connection,String session,String user,String ip,long login,long activity)throws SQLException{transaction(c->{
        try(PreparedStatement p=c.prepareStatement("SELECT connection_id,session_id,user_id FROM sessions WHERE connection_id=? OR session_id=?")){p.setString(1,connection);p.setString(2,session);try(ResultSet r=p.executeQuery()){if(r.next()){if(!connection.equals(r.getString(1))||!session.equals(r.getString(2))||!user.equals(r.getString(3)))throw new SQLException("Xung đột định danh phiên khi nhập SQLite.");return null;}}}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO sessions(connection_id,session_id,user_id,ip,login_time,last_activity,status,offline_time,reason) VALUES(?,?,?,?,?,?,'OFFLINE',?,'IMPORTED_SQLITE')")){p.setString(1,connection);p.setString(2,session);p.setString(3,user);p.setString(4,ip);p.setLong(5,login);p.setLong(6,activity);p.setLong(7,activity);p.executeUpdate();}return null;
    });}
    void importLegacyAttendance(String room,String connection,String user,long joined,Long left,long activity)throws SQLException{transaction(c->{
        try(PreparedStatement p=c.prepareStatement("SELECT user_id FROM attendance WHERE class_id=? AND connection_id=?")){p.setString(1,room);p.setString(2,connection);try(ResultSet r=p.executeQuery()){if(r.next()){if(!user.equals(r.getString(1)))throw new SQLException("Xung đột người dùng trong lịch sử điểm danh SQLite.");return null;}}}
        long ended=left==null?activity:left;
        String visits=studyfix.assignment.protocol.AssignmentJson.stringify(List.of(mapOf("joinedAt",joined,"leftAt",ended)));
        try(PreparedStatement p=c.prepareStatement("INSERT INTO attendance(class_id,connection_id,user_id,joined_at,left_at,visits_json,ip_snapshot,activity_snapshot) VALUES(?,?,?,?,?,?,'',?)")){p.setString(1,room);p.setString(2,connection);p.setString(3,user);p.setLong(4,joined);p.setLong(5,ended);p.setString(6,visits);p.setLong(7,ended);p.executeUpdate();}return null;
    });}
    void observe(String connection,String room,String ip,long activity)throws SQLException{transaction(c->{
        try(PreparedStatement p=c.prepareStatement("UPDATE sessions SET ip=?,last_activity=GREATEST(last_activity,?) WHERE connection_id=? AND status='ONLINE'")){p.setString(1,ip);p.setLong(2,activity);p.setString(3,connection);if(p.executeUpdate()==0)return null;}
        closeVisits(c,connection,room,activity);
        if(!room.isBlank()){
            try(PreparedStatement p=c.prepareStatement("INSERT INTO attendance(class_id,connection_id,user_id,joined_at,visits_json,ip_snapshot,activity_snapshot) SELECT ?,connection_id,user_id,?,JSON_ARRAY(JSON_OBJECT('joinedAt',?,'leftAt',NULL)),ip,last_activity FROM sessions WHERE connection_id=? ON DUPLICATE KEY UPDATE visits_json=IF(left_at IS NULL,visits_json,JSON_ARRAY_APPEND(visits_json,'$',JSON_OBJECT('joinedAt',VALUES(joined_at),'leftAt',NULL))),joined_at=IF(left_at IS NULL,joined_at,VALUES(joined_at)),ip_snapshot=VALUES(ip_snapshot),activity_snapshot=VALUES(activity_snapshot),left_at=NULL")){p.setString(1,room);p.setLong(2,activity);p.setLong(3,activity);p.setString(4,connection);p.executeUpdate();}
        }return null;
    });}
    void offline(String connection,long activity,String reason)throws SQLException{transaction(c->{
        try(PreparedStatement p=c.prepareStatement("UPDATE sessions SET status='OFFLINE',last_activity=GREATEST(last_activity,?),offline_time=?,reason=? WHERE connection_id=? AND status='ONLINE'")){p.setLong(1,activity);p.setLong(2,System.currentTimeMillis());p.setString(3,reason);p.setString(4,connection);p.executeUpdate();}
        closeVisits(c,connection,"",activity);
        try(PreparedStatement p=c.prepareStatement("UPDATE class_admissions a JOIN sessions s ON s.session_id=a.session_id SET a.status='CANCELLED' WHERE s.connection_id=? AND a.status IN ('PENDING','APPROVED','ENTERED')")){p.setString(1,connection);p.executeUpdate();}return null;
    });}
    private static void closeVisits(Connection c,String connection,String except,long activity)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("UPDATE attendance SET visits_json=JSON_SET(visits_json,CONCAT('$[',JSON_LENGTH(visits_json)-1,'].leftAt'),?),left_at=?,activity_snapshot=GREATEST(activity_snapshot,?) WHERE connection_id=? AND left_at IS NULL AND class_id<>?")){
            p.setLong(1,activity);p.setLong(2,activity);p.setLong(3,activity);p.setString(4,connection);p.setString(5,except);p.executeUpdate();
        }
    }
    List<Map<String,Object>> students(String room)throws SQLException{return transaction(c->{
        List<Map<String,Object>> rows=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT s.connection_id,s.user_id,u.student_code,u.username,u.display_name,a.ip_snapshot,s.login_time,a.activity_snapshot,s.status,s.offline_time,s.reason,a.joined_at,a.left_at FROM attendance a JOIN sessions s ON s.connection_id=a.connection_id JOIN users u ON u.user_id=s.user_id WHERE a.class_id=? AND u.role='STUDENT' ORDER BY (s.status='ONLINE' AND a.left_at IS NULL) DESC,u.student_code,s.login_time DESC")){
            p.setString(1,room);try(ResultSet r=p.executeQuery()){while(r.next()){
                boolean online=r.getString(9).equals("ONLINE")&&r.getObject(13)==null;
                rows.add(mapOf("connectionId",r.getString(1),"userId",r.getString(2),"studentCode",r.getString(3),"displayName",r.getString(5),"ip",r.getString(6),"loginTime",r.getLong(7),"lastActivity",r.getLong(8),"online",online,"status",online?"ONLINE":"OFFLINE","connectionStatus",r.getString(9),"offlineTime",r.getObject(10),"reason",r.getObject(13)!=null&&r.getString(9).equals("ONLINE")?"LEFT_ROOM":r.getString(11),"joinedAt",r.getLong(12),"leftAt",r.getObject(13)));
            }}
        }return rows;
    });}
    Map<String,Object> dashboard(String teacher,String selected)throws SQLException{return new MySqlTeacherRepository(this).dashboard(teacher,selected);}
}
