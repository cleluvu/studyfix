package studyfix;

import studyfix.assignment.protocol.AssignmentJson;
import java.sql.*;
import java.util.*;

/** Lưu dữ liệu phòng học vào database. Các lần ghi chạy tuần tự qua Store và được hoàn tác nếu có lỗi. */
final class ClassroomRepository {
    record Session(String room, String phase, int round, String exerciseId, long baseVersion, long updatedAt) { }
    record Branch(String room, int round, String userId, String username, String name,
                  String code, long version, long baseVersion, long updatedAt) { }
    record Check(String id, String room, int round, String userId, long branchVersion,
                 String code, String exerciseId, long createdAt, String status, String result) { }
    private final Store store;
    ClassroomRepository(Store store) { this.store = store; }

    static void migrate(Connection db) throws SQLException {
        DebugLessonRepository.migrate(db);
        DebugLibraryRepository.migrate(db);
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS classroom_schema(version INTEGER PRIMARY KEY)");
            s.execute("CREATE TABLE IF NOT EXISTS classroom_sessions(room_id INTEGER PRIMARY KEY REFERENCES rooms(room_id), phase TEXT NOT NULL CHECK(phase IN ('WAITING','LECTURE','BRANCHING','MERGING','ENDED')), round INTEGER NOT NULL, exercise_id TEXT NOT NULL, base_version INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS classroom_branches(room_id INTEGER NOT NULL REFERENCES rooms(room_id), round INTEGER NOT NULL, user_id TEXT NOT NULL REFERENCES users(user_id), username TEXT NOT NULL, display_name TEXT NOT NULL, code TEXT NOT NULL, version INTEGER NOT NULL, base_version INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(room_id,round,user_id))");
            s.execute("CREATE TABLE IF NOT EXISTS classroom_checks(id TEXT PRIMARY KEY,room_id INTEGER NOT NULL,round INTEGER NOT NULL,user_id TEXT NOT NULL,branch_version INTEGER NOT NULL,code TEXT NOT NULL,exercise_id TEXT NOT NULL,created_at INTEGER NOT NULL,status TEXT NOT NULL,result TEXT NOT NULL,FOREIGN KEY(room_id,round,user_id) REFERENCES classroom_branches(room_id,round,user_id))");
            s.execute("CREATE INDEX IF NOT EXISTS classroom_check_branch ON classroom_checks(room_id,round,user_id,created_at DESC)");
            s.execute("CREATE TABLE IF NOT EXISTS classroom_events(id INTEGER PRIMARY KEY AUTOINCREMENT,room_id INTEGER NOT NULL REFERENCES rooms(room_id),round INTEGER NOT NULL,actor_id TEXT NOT NULL,kind TEXT NOT NULL,detail TEXT NOT NULL,created_at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS classroom_event_room ON classroom_events(room_id,id DESC)");
            s.execute("CREATE TABLE IF NOT EXISTS classroom_chat(id INTEGER PRIMARY KEY AUTOINCREMENT,room_id INTEGER NOT NULL REFERENCES rooms(room_id),user_id TEXT NOT NULL REFERENCES users(user_id),display_name TEXT NOT NULL,message TEXT NOT NULL,created_at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS classroom_chat_room ON classroom_chat(room_id,id DESC)");
            s.execute("INSERT OR IGNORE INTO classroom_schema(version) VALUES(1)");
            s.execute("INSERT OR IGNORE INTO classroom_schema(version) VALUES(2)");
            // Khi chạy lại chương trình, không được xem lượt chấm còn dang dở là đã thành công.
            s.execute("UPDATE classroom_checks SET status='JUDGE_ERROR',result='{\"verdict\":\"JUDGE_ERROR\",\"feedback\":\"Server đã dừng trong lúc chấm. Hãy chạy kiểm tra lại.\"}' WHERE status IN ('QUEUED','RUNNING')");
        }
    }
    Session session(String room) throws SQLException {
        return store.classroomTransaction(c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT * FROM classroom_sessions WHERE room_id=?")) {
                s.setString(1,room);
                try (ResultSet r=s.executeQuery()) {
                    return r.next() ? new Session(room,r.getString("phase"),r.getInt("round"),r.getString("exercise_id"),r.getLong("base_version"),r.getLong("updated_at"))
                            : new Session(room,"WAITING",0,"",0,0);
                }
            }
        });
    }
    private void saveSession(Connection c, Session x) throws SQLException {
        try (PreparedStatement s=c.prepareStatement("INSERT INTO classroom_sessions VALUES(?,?,?,?,?,?) ON CONFLICT(room_id) DO UPDATE SET phase=excluded.phase,round=excluded.round,exercise_id=excluded.exercise_id,base_version=excluded.base_version,updated_at=excluded.updated_at")) {
            s.setString(1,x.room());s.setString(2,x.phase());s.setInt(3,x.round());s.setString(4,x.exerciseId());s.setLong(5,x.baseVersion());s.setLong(6,x.updatedAt());s.executeUpdate();
        }
    }
    void change(Session x, Identity actor, String kind, String detail, List<Branch> branches, CodeRevision revision) throws SQLException {
        store.classroomTransaction(c -> {
            if(revision!=null) store.persistRevision(x.room(),revision.getPreviousVersion(),revision);
            saveSession(c,x);
            for(Branch b:branches) insertBranch(c,b);
            try(PreparedStatement s=c.prepareStatement("INSERT INTO classroom_events(room_id,round,actor_id,kind,detail,created_at) VALUES(?,?,?,?,?,?)")) {
                s.setString(1,x.room());s.setInt(2,x.round());s.setString(3,actor.userId());s.setString(4,kind);s.setString(5,detail);s.setLong(6,x.updatedAt());s.executeUpdate();
            }
            return null;
        });
    }
    private void insertBranch(Connection c, Branch b) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT OR IGNORE INTO classroom_branches VALUES(?,?,?,?,?,?,?,?,?)")) {
            s.setString(1,b.room());s.setInt(2,b.round());s.setString(3,b.userId());s.setString(4,b.username());s.setString(5,b.name());s.setString(6,b.code());s.setLong(7,b.version());s.setLong(8,b.baseVersion());s.setLong(9,b.updatedAt());s.executeUpdate();
        }
    }
    List<Branch> branches(Session x) throws SQLException {
        return store.classroomTransaction(c -> {
            List<Branch> out=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT * FROM classroom_branches WHERE room_id=? AND round=? ORDER BY username")) {
                s.setString(1,x.room());s.setInt(2,x.round());
                try(ResultSet r=s.executeQuery()){while(r.next())out.add(new Branch(x.room(),x.round(),r.getString("user_id"),r.getString("username"),r.getString("display_name"),r.getString("code"),r.getLong("version"),r.getLong("base_version"),r.getLong("updated_at")));}
            }
            return List.copyOf(out);
        });
    }
    void saveBranch(Branch b, long expected) throws SQLException {
        store.classroomTransaction(c -> {
            try(PreparedStatement s=c.prepareStatement("UPDATE classroom_branches SET code=?,version=?,updated_at=? WHERE room_id=? AND round=? AND user_id=? AND version=?")) {
                s.setString(1,b.code());s.setLong(2,b.version());s.setLong(3,b.updatedAt());s.setString(4,b.room());s.setInt(5,b.round());s.setString(6,b.userId());s.setLong(7,expected);
                if(s.executeUpdate()!=1)throw new SQLException("Nhánh đã đổi phiên bản; tải lại trước khi lưu.");
            }
            return null;
        });
    }
    List<Check> checks(Session x, String userId, int limit) throws SQLException {
        return store.classroomTransaction(c -> {
            List<Check> out=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT * FROM classroom_checks WHERE room_id=? AND round=? AND user_id=? ORDER BY created_at DESC,rowid DESC LIMIT ?")) {
                s.setString(1,x.room());s.setInt(2,x.round());s.setString(3,userId);s.setInt(4,limit);
                try(ResultSet r=s.executeQuery()){while(r.next())out.add(new Check(r.getString("id"),x.room(),x.round(),userId,r.getLong("branch_version"),r.getString("code"),r.getString("exercise_id"),r.getLong("created_at"),r.getString("status"),r.getString("result")));}
            }
            return List.copyOf(out);
        });
    }
    void createCheck(Check x) throws SQLException {
        store.classroomTransaction(c -> {
            try(PreparedStatement s=c.prepareStatement("INSERT INTO classroom_checks VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                s.setString(1,x.id());s.setString(2,x.room());s.setInt(3,x.round());s.setString(4,x.userId());s.setLong(5,x.branchVersion());s.setString(6,x.code());s.setString(7,x.exerciseId());s.setLong(8,x.createdAt());s.setString(9,x.status());s.setString(10,x.result());s.executeUpdate();
            }return null;
        });
    }
    Check check(String room, String id) throws SQLException {
        return store.classroomTransaction(c -> {
            try(PreparedStatement s=c.prepareStatement("SELECT * FROM classroom_checks WHERE room_id=? AND id=?")) {
                s.setString(1,room);s.setString(2,id);
                try(ResultSet r=s.executeQuery()) {
                    return r.next()?new Check(id,room,r.getInt("round"),r.getString("user_id"),r.getLong("branch_version"),r.getString("code"),r.getString("exercise_id"),r.getLong("created_at"),r.getString("status"),r.getString("result")):null;
                }
            }
        });
    }
    void result(String id, String status, Map<String,Object> result) throws SQLException {
        store.classroomTransaction(c -> {
            try(PreparedStatement s=c.prepareStatement("UPDATE classroom_checks SET status=?,result=? WHERE id=?")) {
                s.setString(1,status);s.setString(2,AssignmentJson.stringify(result));s.setString(3,id);s.executeUpdate();
            }return null;
        });
    }
    List<Map<String,Object>> report(String room) throws SQLException {
        return store.classroomTransaction(c -> {
            List<Map<String,Object>> out=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT id,round,actor_id,kind,detail,created_at FROM classroom_events WHERE room_id=? ORDER BY id DESC LIMIT 100")) {
                s.setString(1,room);
                try(ResultSet r=s.executeQuery()){while(r.next())out.add(AssignmentJson.mapOf("id",r.getLong(1),"round",r.getInt(2),"actorId",r.getString(3),"kind",r.getString(4),"detail",r.getString(5),"createdAt",r.getLong(6)));}
            }return out;
        });
    }
    void appendChat(String room, Identity who, String message) throws SQLException {
        store.classroomTransaction(c -> {
            try(PreparedStatement s=c.prepareStatement("INSERT INTO classroom_chat(room_id,user_id,display_name,message,created_at) VALUES(?,?,?,?,?)")) {
                s.setString(1,room);s.setString(2,who.userId());s.setString(3,who.displayName());s.setString(4,message);s.setLong(5,System.currentTimeMillis());s.executeUpdate();
            }return null;
        });
    }
    List<Map<String,Object>> chat(String room) throws SQLException {
        return store.classroomTransaction(c -> readChat(c, room));
    }
    private List<Map<String,Object>> readChat(Connection c, String room) throws SQLException {
            List<Map<String,Object>> out=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT id,display_name,message,created_at,user_id FROM classroom_chat WHERE room_id=? ORDER BY id DESC LIMIT 80")) {
                s.setString(1,room);
                try(ResultSet r=s.executeQuery()){while(r.next())out.add(AssignmentJson.mapOf("id",r.getLong(1),"name",r.getString(2),"text",r.getString(3),"timestamp",r.getLong(4),"userId",r.getString(5),"system",false));}
            }Collections.reverse(out);return out;
    }
    Map<String,Object> chatStatus(String room, String userId, long after) throws SQLException {
        return store.classroomTransaction(c -> {
            List<Map<String,Object>> messages=readChat(c,room);
            long latest=messages.isEmpty()?0:((Number)messages.getLast().get("id")).longValue();
            long unread;
            try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM classroom_chat WHERE room_id=? AND user_id<>? AND id>?")) {
                s.setString(1,room);s.setString(2,userId);s.setLong(3,Math.max(0,after));
                try(ResultSet r=s.executeQuery()){r.next();unread=r.getLong(1);}
            }
            return AssignmentJson.mapOf("ok",true,"roomId",room,"messages",messages,"latestId",latest,"unreadCount",unread);
        });
    }
}
