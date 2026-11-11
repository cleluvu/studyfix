package studyfix;

import studyfix.assignment.model.*;
import studyfix.assignment.protocol.AssignmentJson;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Immutable, room-scoped lesson snapshots, including private answers and tests. */
final class DebugLessonRepository {
    private final Store store;
    DebugLessonRepository(Store store) { this.store = store; }
    static void migrate(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS classroom_debug_lessons(id TEXT PRIMARY KEY,room_id INTEGER NOT NULL REFERENCES rooms(room_id),owner_id TEXT NOT NULL,body TEXT NOT NULL,created_at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS classroom_debug_room ON classroom_debug_lessons(room_id,created_at)");
        }
    }
    void save(String id, String room, String owner, Map<String,Object> body) throws SQLException {
        store.classroomTransaction(c -> {
            try (PreparedStatement s = c.prepareStatement("INSERT INTO classroom_debug_lessons VALUES(?,?,?,?,?)")) {
                s.setString(1,id); s.setString(2,room); s.setString(3,owner); s.setString(4,stringify(body)); s.setLong(5,System.currentTimeMillis()); s.executeUpdate();
            }
            return null;
        });
    }
    Map<String,Object> get(String id) throws SQLException {
        return store.classroomTransaction(c -> {
            try (PreparedStatement s = c.prepareStatement("SELECT body FROM classroom_debug_lessons WHERE id=?")) {
                s.setString(1,id);
                try (ResultSet r = s.executeQuery()) { return r.next() ? object(r.getString(1)) : null; }
            }
        });
    }
    List<Map<String,Object>> recent(String room,String owner) throws SQLException {
        return store.classroomTransaction(c -> {
            List<Map<String,Object>> out=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("SELECT id,body,created_at FROM classroom_debug_lessons WHERE room_id=? AND owner_id=? ORDER BY created_at DESC LIMIT 20")) {
                s.setString(1,room);s.setString(2,owner);
                try(ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> data=object(r.getString(2));out.add(mapOf("id",r.getString(1),"title",text(data,"title","Bài sửa lỗi"),"status","READY","createdAt",r.getLong(3)));}}
            }
            return out;
        });
    }
    static Map<String,Object> snapshot(Exercise source, String id, String code) {
        Map<String,Object> data = new LinkedHashMap<>(source.publicView());
        data.put("id",id); data.put("mode","DEBUG_COLLAB"); data.put("template",code); data.put("starterCode",code); data.put("hints","");
        data.put("referenceSolution",source.referenceSolution());
        data.put("tests",source.tests().stream().map(t -> mapOf("input",t.input(),"expected",t.expected(),"weight",t.weight(),"hidden",t.hidden())).toList());
        return data;
    }
    @SuppressWarnings("unchecked")
    static Exercise exercise(Map<String,Object> data) {
        List<Exercise.TestCase> tests = new ArrayList<>();
        for (Object item : (List<?>)data.get("tests")) {
            Map<String,Object> t = (Map<String,Object>)item;
            tests.add(new Exercise.TestCase(text(t,"input",""),text(t,"expected",""),integer(t,"weight",1),Boolean.TRUE.equals(t.get("hidden"))));
        }
        return new Exercise(text(data,"id",""),text(data,"title",""),text(data,"topic",""),text(data,"difficulty",""),text(data,"statement",""),text(data,"inputFormat",""),text(data,"outputFormat",""),text(data,"constraints",""),text(data,"template",""),"",text(data,"referenceSolution",""),ExerciseMode.DEBUG_COLLAB,text(data,"starterCode",""),tests);
    }
}