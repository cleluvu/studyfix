package studyfix;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.check;

/** Tạo schema v1 chỉ khi database _test chưa có bảng; tuyệt đối không DROP/DELETE dữ liệu. */
final class MySqlMigrationChecks {
    static String[] legacy;
    static void prepareIfEmpty(ManagementConfig config)throws Exception{
        Class.forName("com.mysql.cj.jdbc.Driver");
        try(Connection c=DriverManager.getConnection(config.url,config.user,config.password)){
            try(ResultSet r=c.getMetaData().getTables(c.getCatalog(),null,"users",new String[]{"TABLE"})){if(r.next())return;}
            String sql=Files.readString(Path.of("src/studyfix/db/mysql-schema.sql"));
            sql=sql.replace(",created_by VARCHAR(36) NULL","").replace("closed BOOLEAN NOT NULL DEFAULT FALSE,","").replace(",visits_json JSON NULL","").replace(",ip_snapshot VARCHAR(45) NOT NULL DEFAULT ''","").replace(",activity_snapshot BIGINT NOT NULL DEFAULT 0","");
            try(Statement p=c.createStatement()){for(String item:sql.replaceAll("(?m)^--.*$","").split(";"))if(!item.isBlank())p.execute(item);}
            String teacher=UUID.randomUUID().toString(),student=UUID.randomUUID().toString(),connection=UUID.randomUUID().toString(),hash=Passwords.hash(("M8_"+UUID.randomUUID()).toCharArray());
            long time=System.currentTimeMillis()-10000;
            try(PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,student_code,display_name,password_hash,role,created_at) VALUES(?,?,?,?,?,?,?)")){
                p.setString(1,teacher);p.setString(2,System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"));p.setString(3,null);p.setString(4,"Giảng viên quản trị");p.setString(5,Passwords.hash(System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD").toCharArray()));p.setString(6,"TEACHER");p.setLong(7,time);p.executeUpdate();
                p.setString(1,student);p.setString(2,"migration_student");p.setString(3,"MIGRATION_SV");p.setString(4,"Sinh viên dữ liệu cũ");p.setString(5,hash);p.setString(6,"STUDENT");p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO classes(class_id,room_code,name,owner_id,active,created_at) VALUES('legacy-class','LEGACY','Lớp cũ đã lưu trữ',?,0,?)")){p.setString(1,teacher);p.setLong(2,time);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO sessions(connection_id,session_id,user_id,ip,login_time,last_activity,status,offline_time) VALUES(?,?,?,'127.0.0.1',?,?,'OFFLINE',?)")){p.setString(1,connection);p.setString(2,UUID.randomUUID().toString());p.setString(3,student);p.setLong(4,time);p.setLong(5,time+1000);p.setLong(6,time+1000);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO attendance(class_id,connection_id,user_id,joined_at,left_at) VALUES('legacy-class',?,?,?,?)")){p.setString(1,connection);p.setString(2,student);p.setLong(3,time);p.setLong(4,time+1000);p.executeUpdate();}
            legacy=new String[]{student,hash,connection};
        }
    }
    static void verify(MySqlManagement sql)throws Exception{
        if(legacy==null)return;
        sql.transaction(c->{
            try(PreparedStatement p=c.prepareStatement("SELECT password_hash,created_by FROM users WHERE user_id=?")){p.setString(1,legacy[0]);try(ResultSet r=p.executeQuery()){check(r.next()&&legacy[1].equals(r.getString(1))&&r.getString(2)==null,"v1 migration preserves userId/hash and does not invent account owner");}}
            try(PreparedStatement p=c.prepareStatement("SELECT JSON_LENGTH(visits_json),joined_at,left_at FROM attendance WHERE connection_id=?")){p.setString(1,legacy[2]);try(ResultSet r=p.executeQuery()){check(r.next()&&r.getInt(1)==1&&r.getLong(3)>r.getLong(2),"v1 attendance survives migration with initial historical visit");}}
            try(PreparedStatement p=c.prepareStatement("SELECT a.ip_snapshot,s.ip,a.activity_snapshot,a.left_at FROM attendance a JOIN sessions s ON s.connection_id=a.connection_id WHERE a.connection_id=?")){p.setString(1,legacy[2]);try(ResultSet r=p.executeQuery()){check(r.next()&&r.getString(1).isEmpty()&&r.getString(2).equals("127.0.0.1")&&r.getLong(3)==r.getLong(4),"migration preserves raw session IP but does not invent historical class IP");}}
            return null;
        });
    }
}
