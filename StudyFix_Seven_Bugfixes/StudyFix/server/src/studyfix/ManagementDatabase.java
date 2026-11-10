package studyfix;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.assignment.protocol.AssignmentJson.mapOf;

/** SQLite management repository; writes use IMMEDIATE transactions and foreign keys. */
final class ManagementDatabase {
    private final ManagementConfig config;
    private final Semaphore connections=new Semaphore(8,true);
    record Account(Identity identity,String hash,String studentCode,boolean active){}
    @FunctionalInterface interface Work<T>{T run(Connection c)throws SQLException;}
    ManagementDatabase(ManagementConfig config)throws SQLException{
        this.config=config;
        try{Class.forName("org.sqlite.JDBC");}catch(ClassNotFoundException e){throw new SQLException("Thiếu JDBC driver của database đã chọn.");}
        try{Files.createDirectories(Path.of(config.url.substring("jdbc:sqlite:".length())).getParent());}catch(IOException e){throw new SQLException("Không tạo được thư mục SQLite.",e);}
        migrate();
    }
    <T>T transaction(Work<T> work)throws SQLException{
        boolean acquired=false;
        try{
            acquired=connections.tryAcquire(3,TimeUnit.SECONDS);if(!acquired)throw new SQLException("Database quản lý đang bận.");
            Properties props=new Properties();props.setProperty("foreign_keys","true");props.setProperty("busy_timeout","8000");props.setProperty("transaction_mode","IMMEDIATE");
            try(Connection c=DriverManager.getConnection(config.url,props)){
                c.setAutoCommit(false);
                try{T result=work.run(c);c.commit();return result;}catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException ignored){}throw e;}
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new SQLException("Management database request interrupted.",e);}
        finally{if(acquired)connections.release();}
    }
    private void migrate()throws SQLException{
        String schema;
        try(InputStream resource=ManagementDatabase.class.getResourceAsStream("/studyfix/db/sqlite-management-schema.sql")){
            schema=resource==null?Files.readString(Path.of("src/studyfix/db/sqlite-management-schema.sql"),StandardCharsets.UTF_8):new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }catch(IOException e){throw new SQLException("Thiếu migration SQLite management.",e);}
        // Journal mode is persistent and must be set outside a transaction.
        try(Connection c=DriverManager.getConnection(config.url);Statement s=c.createStatement()){
            s.execute("PRAGMA busy_timeout=8000");s.execute("PRAGMA journal_mode=WAL");
        }
        transaction(c->{try(Statement s=c.createStatement()){
            for(String part:schema.replaceAll("(?m)^--.*$", "").split(";"))if(!part.isBlank())s.execute(part);
            boolean description=false;
            try(ResultSet r=s.executeQuery("PRAGMA table_info(classes)")){while(r.next())if("description".equals(r.getString("name")))description=true;}
            if(!description)s.execute("ALTER TABLE classes ADD COLUMN description TEXT NOT NULL DEFAULT ''");
            Set<String> userColumns=new HashSet<>();try(ResultSet r=s.executeQuery("PRAGMA table_info(users)")){while(r.next())userColumns.add(r.getString("name"));}
            if(!userColumns.contains("email"))s.execute("ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''");
            if(!userColumns.contains("account_status")){s.execute("ALTER TABLE users ADD COLUMN account_status TEXT NOT NULL DEFAULT 'ACTIVE'");s.execute("UPDATE users SET account_status=CASE WHEN active=1 THEN 'ACTIVE' ELSE 'LOCKED' END");}
        }return null;});
    }
    void bindCore(String coreId)throws SQLException{
        transaction(c->{
            try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO runtime_binding(binding_key,binding_value) VALUES('sqlite_core_id',?)")){p.setString(1,coreId);p.executeUpdate();}
            try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT binding_value FROM runtime_binding WHERE binding_key='sqlite_core_id'")){
                if(!r.next()||!coreId.equals(r.getString(1)))throw new SQLException("SQLite đã liên kết với SQLite lõi khác. Giữ đúng file core hoặc dùng database SQLite riêng; không tự ghép roomId của hai dự án.");
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
                try(ResultSet r=p.executeQuery()){if(r.next()){if(!r.getString(1).equals(a.identity().userId()))throw new SQLException("Tài khoản SQLite trùng tên/mã nhưng khác userId; cần đối chiếu trước khi import.");return null;}}
            }
            insertAccount(c,a);return null;
        });
    }
    private static void insertAccount(Connection c,Account a)throws SQLException{
        insertAccount(c,a,null);
    }
    static void insertAccount(Connection c,Account a,String creator)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,display_name,role,password_hash,student_code,active,created_at,created_by) VALUES(?,?,?,?,?,?,?,?,?)")){
            p.setString(1,a.identity().userId());p.setString(2,a.identity().username());p.setString(3,a.identity().displayName());p.setString(4,a.identity().role().name());p.setString(5,a.hash());p.setString(6,a.studentCode());p.setBoolean(7,a.active());p.setLong(8,System.currentTimeMillis());p.setString(9,creator);p.executeUpdate();
        }
    }
    Account createUser(String username,String code,String name,String role,char[] password)throws SQLException{
        return createUser(username,code,name,role,password,null);
    }
    static Account prepareAccount(String username,String code,String name,String role,char[] password){
        if(!username.matches("[A-Za-z0-9_.-]{1,64}")||name.isBlank()||name.length()>200||password.length<12||password.length>256)throw new IllegalArgumentException("Tài khoản 1–64 ký tự chữ/số/._-, họ tên bắt buộc, mật khẩu 12–256 ký tự.");
        Identity.Role parsed=Identity.Role.valueOf(role);
        String studentCode=parsed==Identity.Role.STUDENT?(code==null||code.isBlank()?username:code):null;
        if(parsed==Identity.Role.STUDENT&&!studentCode.matches("[A-Za-z0-9_.-]{1,64}"))throw new IllegalArgumentException("Mã sinh viên không hợp lệ.");
        return new Account(new Identity(UUID.randomUUID().toString(),username,name,parsed),Passwords.hash(password),studentCode,true);
    }
    static void requireAvailable(Connection c,Account a)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE username IN (?,?) OR student_code IN (?,?)")){p.setString(1,a.identity().username());p.setString(2,a.studentCode());p.setString(3,a.identity().username());p.setString(4,a.studentCode());try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("Tài khoản hoặc mã sinh viên đã tồn tại.");}}
    }
    Account createUser(String username,String code,String name,String role,char[] password,String creator)throws SQLException{
        try{Account a=prepareAccount(username,code,name,role,password);transaction(c->{requireAvailable(c,a);insertAccount(c,a,creator);return null;});return a;}
        finally{Arrays.fill(password,'\0');}
    }
    /** The entire CSV batch commits or rolls back in the authoritative SQLite database. */
    List<Account> createStudents(List<Map<String,Object>> rows,String creator)throws SQLException{
        if(rows.isEmpty()||rows.size()>100)throw new IllegalArgumentException("Mỗi lần nhập từ 1 đến 100 sinh viên.");
        List<Account> accounts=new ArrayList<>();Set<String> usernames=new HashSet<>();
        for(int i=0;i<rows.size();i++){
            Map<String,Object> row=rows.get(i);char[] secret=studyfix.assignment.protocol.AssignmentJson.text(row,"password","").toCharArray();
            try{
                if(!studyfix.assignment.protocol.AssignmentJson.text(row,"role","STUDENT").equals("STUDENT"))throw new IllegalArgumentException("Chỉ được nhập tài khoản sinh viên.");
                String username=studyfix.assignment.protocol.AssignmentJson.text(row,"username","").strip();
                if(!usernames.add(username))throw new IllegalArgumentException("Tài khoản bị trùng trong file.");
                accounts.add(prepareAccount(username,null,studyfix.assignment.protocol.AssignmentJson.text(row,"displayName","").strip(),"STUDENT",secret));
            }catch(IllegalArgumentException e){throw new IllegalArgumentException("Dòng dữ liệu "+(i+1)+": "+e.getMessage());}
            finally{Arrays.fill(secret,'\0');}
        }
        return transaction(c->{
            for(int i=0;i<accounts.size();i++){
                try{requireAvailable(c,accounts.get(i));insertAccount(c,accounts.get(i),creator);}
                catch(IllegalArgumentException e){throw new IllegalArgumentException("Dòng dữ liệu "+(i+1)+": "+e.getMessage()+" Chưa tạo tài khoản nào trong lần nhập này.");}
            }return accounts;
        });
    }
    void setActive(String teacher,String id,boolean active)throws SQLException{transaction(c->{TeacherRepository.requireManagedStudent(c,teacher,id);try(PreparedStatement p=c.prepareStatement("UPDATE users SET active=?,account_status=? WHERE user_id=?")){p.setBoolean(1,active);p.setString(2,active?"ACTIVE":"LOCKED");p.setString(3,id);if(p.executeUpdate()==0)throw new IllegalArgumentException("Không tìm thấy người dùng.");}return null;});}
    void resetPassword(String teacher,String id,char[] secret)throws SQLException{
        try{if(secret.length<12||secret.length>256)throw new IllegalArgumentException("Mật khẩu cần 12–256 ký tự.");String hash=Passwords.hash(secret);transaction(c->{TeacherRepository.requireManagedStudent(c,teacher,id);try(PreparedStatement p=c.prepareStatement("UPDATE users SET password_hash=? WHERE user_id=?")){p.setString(1,hash);p.setString(2,id);if(p.executeUpdate()==0)throw new IllegalArgumentException("Không tìm thấy người dùng.");}return null;});}finally{Arrays.fill(secret,'\0');}
    }
    void bootstrap()throws SQLException{
        if(accounts().stream().anyMatch(a->a.identity().role()==Identity.Role.TEACHER))return;
        String user=System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"),secret=System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD");
        if(user!=null&&secret!=null)createUser(user,null,System.getenv().getOrDefault("STUDYFIX_BOOTSTRAP_NAME","Giảng viên quản trị"),"TEACHER",secret.toCharArray());
    }
    void syncClass(String id,String code,String owner,boolean active)throws SQLException{
        transaction(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO classes(class_id,room_code,name,owner_id,active,created_at) VALUES(?,?,?,?,?,?) ON CONFLICT(class_id) DO UPDATE SET room_code=excluded.room_code,owner_id=excluded.owner_id,active=excluded.active")){
            p.setString(1,id);p.setString(2,code);p.setString(3,"Phòng "+code);p.setString(4,owner);p.setBoolean(5,active);p.setLong(6,System.currentTimeMillis());p.executeUpdate();}return null;});
    }
    void enrollment(String teacher,String room,String user,boolean enrolled)throws SQLException{transaction(c->{
        TeacherRepository.requireOwner(c,teacher,room);
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users u JOIN classes c ON c.class_id=? WHERE u.user_id=? AND u.role='STUDENT' AND (u.active=1 OR ?=0) AND c.active=1")){p.setString(1,room);p.setString(2,user);p.setBoolean(3,enrolled);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Lớp hoặc sinh viên không khả dụng.");}}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO class_members(class_id,user_id,membership_status,enrolled_at) VALUES(?,?,?,?) ON CONFLICT(class_id,user_id) DO UPDATE SET membership_status=excluded.membership_status,enrolled_at=excluded.enrolled_at")){p.setString(1,room);p.setString(2,user);p.setString(3,enrolled?"ENROLLED":"REMOVED");p.setLong(4,System.currentTimeMillis());p.executeUpdate();}
        if(!enrolled)try(PreparedStatement p=c.prepareStatement("UPDATE class_admissions SET status='CANCELLED' WHERE class_id=? AND user_id=? AND status IN ('PENDING','APPROVED','ENTERED')")){p.setString(1,room);p.setString(2,user);p.executeUpdate();}return null;
    });}
    boolean allowedInClass(String room,String user)throws SQLException{return transaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.class_id=? AND m.user_id=? AND m.membership_status='ENROLLED' AND k.active=1 AND k.closed=0")){p.setString(1,room);p.setString(2,user);try(ResultSet r=p.executeQuery()){return r.next();}}});}
    boolean classOpen(String room)throws SQLException{return transaction(c->{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM classes WHERE class_id=? AND active=1 AND closed=0")){p.setString(1,room);try(ResultSet r=p.executeQuery()){return r.next();}}});}
    Set<String> visibleClasses(Identity who)throws SQLException{return transaction(c->{
        Set<String> ids=new HashSet<>();boolean teacher=who.role()==Identity.Role.TEACHER;String sql=teacher?"SELECT class_id FROM classes WHERE owner_id=? AND active=1 AND closed=0":"SELECT class_id FROM classes WHERE active=1 AND closed=0";
        try(PreparedStatement p=c.prepareStatement(sql)){if(teacher)p.setString(1,who.userId());try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}}return ids;
    });}
    void recover()throws SQLException{transaction(c->{try(Statement p=c.createStatement()){
        p.executeUpdate("UPDATE attendance SET visits_json=json_set(visits_json,'$[' || (json_array_length(visits_json)-1) || '].leftAt',(SELECT last_activity FROM sessions WHERE sessions.connection_id=attendance.connection_id)),activity_snapshot=MAX(activity_snapshot,(SELECT last_activity FROM sessions WHERE sessions.connection_id=attendance.connection_id)),left_at=(SELECT last_activity FROM sessions WHERE sessions.connection_id=attendance.connection_id) WHERE left_at IS NULL");
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
        try(PreparedStatement p=c.prepareStatement("UPDATE sessions SET ip=?,last_activity=MAX(last_activity,?) WHERE connection_id=? AND status='ONLINE'")){p.setString(1,ip);p.setLong(2,activity);p.setString(3,connection);if(p.executeUpdate()==0)return null;}
        closeVisits(c,connection,room,activity);
        if(!room.isBlank()){
            try(PreparedStatement p=c.prepareStatement("INSERT INTO attendance(class_id,connection_id,user_id,joined_at,visits_json,ip_snapshot,activity_snapshot) SELECT ?,connection_id,user_id,?,JSON_ARRAY(JSON_OBJECT('joinedAt',?,'leftAt',NULL)),ip,last_activity FROM sessions WHERE connection_id=? ON CONFLICT(class_id,connection_id) DO UPDATE SET visits_json=CASE WHEN left_at IS NULL THEN visits_json ELSE json_insert(visits_json,'$[#]',json_object('joinedAt',excluded.joined_at,'leftAt',NULL)) END,joined_at=CASE WHEN left_at IS NULL THEN joined_at ELSE excluded.joined_at END,ip_snapshot=excluded.ip_snapshot,activity_snapshot=excluded.activity_snapshot,left_at=NULL")){p.setString(1,room);p.setLong(2,activity);p.setLong(3,activity);p.setString(4,connection);p.executeUpdate();}
        }return null;
    });}
    void offline(String connection,long activity,String reason)throws SQLException{transaction(c->{
        try(PreparedStatement p=c.prepareStatement("UPDATE sessions SET status='OFFLINE',last_activity=MAX(last_activity,?),offline_time=?,reason=? WHERE connection_id=? AND status='ONLINE'")){p.setLong(1,activity);p.setLong(2,System.currentTimeMillis());p.setString(3,reason);p.setString(4,connection);p.executeUpdate();}
        closeVisits(c,connection,"",activity);
        try(PreparedStatement p=c.prepareStatement("UPDATE class_admissions SET status='CANCELLED' WHERE session_id IN (SELECT session_id FROM sessions WHERE connection_id=?) AND status IN ('PENDING','APPROVED','ENTERED')")){p.setString(1,connection);p.executeUpdate();}return null;
    });}
    private static void closeVisits(Connection c,String connection,String except,long activity)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("UPDATE attendance SET visits_json=JSON_SET(visits_json,('$[' || (json_array_length(visits_json)-1) || '].leftAt'),?),left_at=?,activity_snapshot=MAX(activity_snapshot,?) WHERE connection_id=? AND left_at IS NULL AND class_id<>?")){
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
    Map<String,Object> dashboard(String teacher,String selected)throws SQLException{return new TeacherRepository(this).dashboard(teacher,selected);}
}
