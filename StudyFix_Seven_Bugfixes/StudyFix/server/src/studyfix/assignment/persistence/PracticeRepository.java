package studyfix.assignment.persistence;

import studyfix.Identity;
import studyfix.assignment.model.GradingResult;
import studyfix.assignment.protocol.AssignmentJson;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Lưu các phiên bản luyện tập riêng trong database học tập hiện có, dùng chung ngân hàng đề. */
public final class PracticeRepository {
    private final String url;
    public PracticeRepository(Path database)throws SQLException {
        url="jdbc:sqlite:"+database.toAbsolutePath();
        try(Connection c=open();Statement s=c.createStatement()) {
            StructureResultStore.ensure(c);s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_practice_submissions(id TEXT PRIMARY KEY,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id),user_id TEXT NOT NULL,version INTEGER NOT NULL,is_run INTEGER NOT NULL,source_code TEXT NOT NULL,submitted_at INTEGER NOT NULL,status TEXT NOT NULL,verdict TEXT NOT NULL,score REAL NOT NULL DEFAULT 0,passed_tests INTEGER NOT NULL DEFAULT 0,total_tests INTEGER NOT NULL DEFAULT 0,feedback TEXT NOT NULL DEFAULT '',duration_ms INTEGER NOT NULL DEFAULT 0,test_results TEXT NOT NULL DEFAULT '[]',request_key TEXT NOT NULL,UNIQUE(user_id,request_key))");
            s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS p3_practice_version ON p3_practice_submissions(user_id,exercise_id,version) WHERE is_run=0");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_practice_history ON p3_practice_submissions(user_id,exercise_id,is_run,submitted_at)");
        }
    }
    private Connection open()throws SQLException {
        org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
        // Reserve the writer before reading MAX(version), avoiding a deferred read-to-write upgrade race.
        config.setTransactionMode(org.sqlite.SQLiteConfig.TransactionMode.IMMEDIATE);
        Connection c=DriverManager.getConnection(url,config.toProperties());
        try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA busy_timeout=5000");}return c;
    }
    private Map<String,Object> row(ResultSet r)throws SQLException {
        return AssignmentJson.mapOf("id",r.getString("id"),"exerciseId",r.getString("exercise_id"),"userId",r.getString("user_id"),"version",r.getInt("version"),"isRun",r.getInt("is_run")!=0,"sourceCode",r.getString("source_code"),"submittedAt",r.getLong("submitted_at"),"status",r.getString("status"),"verdict",r.getString("verdict"),"score",r.getDouble("score"),"maxScore",100,"passedTests",r.getInt("passed_tests"),"totalTests",r.getInt("total_tests"),"feedback",r.getString("feedback"),"durationMs",r.getLong("duration_ms"),"testResults",AssignmentJson.parse(r.getString("test_results")));
    }
    public Map<String,Object> get(String id)throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_practice_submissions WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())return null;var result=row(r);result.put("structureResults",StructureResultStore.read(c,id));return result;}}
    }
    public Map<String,Object> byKey(String user,String key)throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_practice_submissions WHERE user_id=? AND request_key=?")){p.setString(1,user);p.setString(2,key);try(ResultSet r=p.executeQuery()){return r.next()?row(r):null;}}
    }
    public synchronized Map<String,Object> create(Identity user,String exercise,String source,boolean run,String key,int tests)throws SQLException {
        String id=UUID.randomUUID().toString();
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                int version=0;
                if(!run)try(PreparedStatement p=c.prepareStatement("SELECT COALESCE(MAX(version),0)+1 FROM p3_practice_submissions WHERE user_id=? AND exercise_id=? AND is_run=0")){p.setString(1,user.userId());p.setString(2,exercise);try(ResultSet r=p.executeQuery()){r.next();version=r.getInt(1);}}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_practice_submissions(id,exercise_id,user_id,version,is_run,source_code,submitted_at,status,verdict,total_tests,request_key) VALUES(?,?,?,?,?,?,?,'QUEUED','QUEUED',?,?)")) {
                    p.setString(1,id);p.setString(2,exercise);p.setString(3,user.userId());p.setInt(4,version);p.setInt(5,run?1:0);p.setString(6,source);p.setLong(7,System.currentTimeMillis());p.setInt(8,tests);p.setString(9,key);p.executeUpdate();
                }
                c.commit();
            }catch(SQLException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
        }
        return get(id);
    }
    public void running(String id)throws SQLException {try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_practice_submissions SET status='RUNNING',verdict='RUNNING' WHERE id=? AND status='QUEUED'")){p.setString(1,id);p.executeUpdate();}}
    public void finish(String id,GradingResult result)throws SQLException {
        // Phản hồi chỉ giữ thông tin lỗi được phép công khai, không chứa đầu vào hay đáp án test ẩn.
        List<Map<String,Object>> tests=new ArrayList<>();int ordinal=0;
        for(Map<String,Object> test:result.publicTests()) {
            ++ordinal;boolean hidden=Boolean.TRUE.equals(test.get("hidden"));
            tests.add(AssignmentJson.mapOf("index",ordinal,"hidden",hidden,"passed",Boolean.TRUE.equals(test.get("passed")),"message",hidden?"Test ẩn: "+(Boolean.TRUE.equals(test.get("passed"))?"đạt":"không đạt"):String.valueOf(test.getOrDefault("message","")),"durationMs",test.getOrDefault("durationMs",0)));
        }
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_practice_submissions SET status='DONE',verdict=?,score=?,passed_tests=?,total_tests=?,feedback=?,duration_ms=?,test_results=? WHERE id=? AND status IN ('QUEUED','RUNNING')")) {
            c.setAutoCommit(false);p.setString(1,result.verdict());p.setDouble(2,result.score());p.setInt(3,result.passed());p.setInt(4,result.total());p.setString(5,result.feedback());p.setLong(6,result.durationMillis());p.setString(7,AssignmentJson.stringify(tests));p.setString(8,id);if(p.executeUpdate()>0)StructureResultStore.write(c,id,result.structureResults());c.commit();
        }
    }
    public List<String> recover()throws SQLException {
        try(Connection c=open();Statement s=c.createStatement()) {
            s.executeUpdate("UPDATE p3_practice_submissions SET status='QUEUED',verdict='QUEUED' WHERE status='RUNNING'");
            try(ResultSet r=s.executeQuery("SELECT id FROM p3_practice_submissions WHERE status='QUEUED' ORDER BY submitted_at")){List<String> ids=new ArrayList<>();while(r.next())ids.add(r.getString(1));return ids;}
        }
    }
    public Map<String,Map<String,Object>> progress(String user)throws SQLException {
        Map<String,Map<String,Object>> out=new HashMap<>();
        String sql="SELECT exercise_id,MAX(CASE WHEN verdict='ACCEPTED' THEN 1 ELSE 0 END) ac,MAX(CASE WHEN verdict IN ('WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED','MEMORY_LIMIT_EXCEEDED','OUTPUT_LIMIT_EXCEEDED') THEN 1 ELSE 0 END) failed,MAX(CASE WHEN status IN ('QUEUED','RUNNING') THEN 1 ELSE 0 END) pending,COUNT(*) attempts FROM p3_practice_submissions WHERE user_id=? AND is_run=0 GROUP BY exercise_id";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,user);try(ResultSet r=p.executeQuery()){while(r.next()){String status=r.getInt("ac")!=0?"AC":r.getInt("pending")!=0?"PENDING":r.getInt("failed")!=0?"FAILED":"ERROR";out.put(r.getString(1),AssignmentJson.mapOf("practiceStatus",status,"acceptedByMe",r.getInt("ac")!=0,"attempts",r.getInt("attempts")));}}}return out;
    }
    public Map<String,Object> history(String user,String exercise,int requestedPage)throws SQLException {
        try(Connection c=open()) {
            int total;
            try(PreparedStatement p=c.prepareStatement("SELECT count(*) FROM p3_practice_submissions WHERE user_id=? AND exercise_id=? AND is_run=0")){p.setString(1,user);p.setString(2,exercise);try(ResultSet r=p.executeQuery()){r.next();total=r.getInt(1);}}
            int pages=Math.max(1,(total+9)/10),page=Math.max(1,Math.min(pages,requestedPage));List<Map<String,Object>> rows=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("SELECT * FROM p3_practice_submissions WHERE user_id=? AND exercise_id=? AND is_run=0 ORDER BY version DESC LIMIT 10 OFFSET ?")){p.setString(1,user);p.setString(2,exercise);p.setInt(3,(page-1)*10);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> row=row(r);row.remove("sourceCode");row.remove("userId");row.remove("testResults");rows.add(row);}}}
            return AssignmentJson.mapOf("submissions",rows,"page",page,"total",total,"totalPages",pages);
        }
    }
}
