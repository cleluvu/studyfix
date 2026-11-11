package studyfix;

import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Teacher-owned reusable buggy exercises; assigned rooms receive independent snapshots. */
final class DebugLibraryRepository {
    private final Store store;
    DebugLibraryRepository(Store store){this.store=store;}
    static void migrate(Connection c)throws SQLException {
        try(Statement s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS classroom_debug_library(id TEXT PRIMARY KEY,owner_id TEXT NOT NULL,version INTEGER NOT NULL,status TEXT NOT NULL,body TEXT NOT NULL,deleted INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS debug_library_owner ON classroom_debug_library(owner_id,deleted,updated_at DESC)");
            s.execute("UPDATE classroom_debug_library SET status='FAILED' WHERE status IN ('QUEUED','RUNNING')");
        }
    }
    Map<String,Object> get(String id,String owner)throws SQLException {
        return store.classroomTransaction(c->{
            try(PreparedStatement s=c.prepareStatement("SELECT * FROM classroom_debug_library WHERE id=? AND owner_id=? AND deleted=0")){
                s.setString(1,id);s.setString(2,owner);
                try(ResultSet r=s.executeQuery()){
                    if(!r.next())throw new SecurityException("Không tìm thấy bài lỗi trong danh sách của bạn.");
                    Map<String,Object> row=object(r.getString("body"));row.put("id",id);row.put("version",r.getLong("version"));row.put("status",r.getString("status"));row.put("createdAt",r.getLong("created_at"));row.put("updatedAt",r.getLong("updated_at"));return row;
                }
            }
        });
    }
    List<Map<String,Object>> list(String owner)throws SQLException {
        return store.classroomTransaction(c->{
            List<Map<String,Object>> out=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT * FROM classroom_debug_library WHERE owner_id=? AND deleted=0 ORDER BY updated_at DESC,id")){
                s.setString(1,owner);try(ResultSet r=s.executeQuery()){while(r.next()){
                    Map<String,Object> body=object(r.getString("body"));
                    out.add(mapOf("id",r.getString("id"),"title",text(body,"title","Bài sửa lỗi"),"sourceId",text(body,"sourceId",""),"sourceTitle",text(body,"sourceTitle",""),"topic",text(body,"topic",""),"difficulty",text(body,"difficulty",""),"version",r.getLong("version"),"status",r.getString("status"),"updatedAt",r.getLong("updated_at"),"message",text(body,"message",""),"validation",body.getOrDefault("validation",Map.of())));
                }}
            }return out;
        });
    }
    boolean busy(String owner)throws SQLException {
        return store.classroomTransaction(c->{try(PreparedStatement s=c.prepareStatement("SELECT 1 FROM classroom_debug_library WHERE owner_id=? AND deleted=0 AND status IN ('QUEUED','RUNNING') LIMIT 1")){s.setString(1,owner);try(ResultSet r=s.executeQuery()){return r.next();}}});
    }
    void save(String id,String owner,Map<String,Object> body,long expected)throws SQLException {
        store.classroomTransaction(c->{long now=System.currentTimeMillis();
            if(expected==0){try(PreparedStatement s=c.prepareStatement("INSERT INTO classroom_debug_library(id,owner_id,version,status,body,created_at,updated_at) VALUES(?,?,1,'QUEUED',?,?,?)")){s.setString(1,id);s.setString(2,owner);s.setString(3,stringify(body));s.setLong(4,now);s.setLong(5,now);s.executeUpdate();}}
            else {try(PreparedStatement s=c.prepareStatement("UPDATE classroom_debug_library SET version=version+1,status='QUEUED',body=?,updated_at=? WHERE id=? AND owner_id=? AND version=? AND deleted=0 AND status NOT IN ('QUEUED','RUNNING')")){s.setString(1,stringify(body));s.setLong(2,now);s.setString(3,id);s.setString(4,owner);s.setLong(5,expected);if(s.executeUpdate()!=1)throw new IllegalStateException("Bài đã thay đổi hoặc đang kiểm chứng. Hãy tải lại trước khi lưu.");}}
            return null;
        });
    }
    void result(String id,String owner,long version,String status,Map<String,Object> body)throws SQLException {
        store.classroomTransaction(c->{try(PreparedStatement s=c.prepareStatement("UPDATE classroom_debug_library SET status=?,body=?,updated_at=? WHERE id=? AND owner_id=? AND version=? AND deleted=0")){s.setString(1,status);s.setString(2,stringify(body));s.setLong(3,System.currentTimeMillis());s.setString(4,id);s.setString(5,owner);s.setLong(6,version);s.executeUpdate();}return null;});
    }
    void delete(String id,String owner,long expected)throws SQLException {
        store.classroomTransaction(c->{try(PreparedStatement s=c.prepareStatement("UPDATE classroom_debug_library SET deleted=1,updated_at=? WHERE id=? AND owner_id=? AND version=? AND deleted=0 AND status NOT IN ('QUEUED','RUNNING')")){s.setLong(1,System.currentTimeMillis());s.setString(2,id);s.setString(3,owner);s.setLong(4,expected);if(s.executeUpdate()!=1)throw new IllegalStateException("Bài đã thay đổi hoặc đang kiểm chứng. Hãy tải lại trước khi xóa.");}return null;});
    }
}