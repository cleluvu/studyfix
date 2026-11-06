package studyfix.assignment.persistence;

import studyfix.Identity;
import java.sql.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Exam signals use the existing learning database; no clipboard/source content is stored. */
public final class ExamMonitoringRepository {
    public static final class Failure extends RuntimeException { public final String code;public Failure(String code,String message){super(message);this.code=code;} }
    private final AssignmentRepository repo;
    private final boolean classroom;
    private String sql(String query){return classroom?query.replace(" REFERENCES p3_contests(id)", "").replace("p3_exam_", "p3_branch_exam_"):query;}
    public ExamMonitoringRepository(AssignmentRepository repo)throws SQLException{this(repo,false);}
    public ExamMonitoringRepository(AssignmentRepository repo,boolean classroom)throws SQLException{
        this.repo=repo;this.classroom=classroom;
        try(Connection c=repo.open();Statement s=c.createStatement()){
            s.executeUpdate(sql("CREATE TABLE IF NOT EXISTS p3_exam_modes(contest_id TEXT PRIMARY KEY REFERENCES p3_contests(id),enabled INTEGER NOT NULL DEFAULT 0,generation TEXT NOT NULL,tab_limit INTEGER NOT NULL DEFAULT 3,paste_limit INTEGER NOT NULL DEFAULT 3,updated_at INTEGER NOT NULL)"));
            s.executeUpdate(sql("CREATE TABLE IF NOT EXISTS p3_exam_sessions(id TEXT PRIMARY KEY,contest_id TEXT NOT NULL REFERENCES p3_contests(id),generation TEXT NOT NULL,user_id TEXT NOT NULL,connection_hash TEXT NOT NULL,username TEXT NOT NULL,display_name TEXT NOT NULL,started_at INTEGER NOT NULL,last_activity INTEGER NOT NULL,away INTEGER NOT NULL DEFAULT 0,away_since INTEGER NOT NULL DEFAULT 0,absence_ms INTEGER NOT NULL DEFAULT 0,tab_count INTEGER NOT NULL DEFAULT 0,paste_count INTEGER NOT NULL DEFAULT 0,UNIQUE(contest_id,generation,user_id,connection_hash))"));
            s.executeUpdate(sql("CREATE TABLE IF NOT EXISTS p3_exam_events(id INTEGER PRIMARY KEY AUTOINCREMENT,exam_session_id TEXT NOT NULL REFERENCES p3_exam_sessions(id),event_id TEXT NOT NULL,type TEXT NOT NULL,source TEXT NOT NULL,client_at INTEGER NOT NULL,received_at INTEGER NOT NULL,duration_ms INTEGER NOT NULL DEFAULT 0,UNIQUE(exam_session_id,event_id))"));
            s.executeUpdate(sql("CREATE INDEX IF NOT EXISTS p3_exam_events_session_time ON p3_exam_events(exam_session_id,received_at)"));
            s.executeUpdate(sql("CREATE TABLE IF NOT EXISTS p3_exam_migrations(version INTEGER PRIMARY KEY,applied_at INTEGER NOT NULL)"));
            s.executeUpdate(sql("INSERT OR IGNORE INTO p3_exam_migrations VALUES(1,")+System.currentTimeMillis()+")");
        }
    }
    private static String hash(String session){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(session.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public Map<String,Object> mode(String contest)throws SQLException{try(Connection c=repo.open()){return mode(c,contest);}}
    private Map<String,Object> mode(Connection c,String contest)throws SQLException{
        Map<String,Object> result=mapOf("contestId",contest,"enabled",false,"generation","","tabLimit",3,"pasteLimit",3);
        try(PreparedStatement p=c.prepareStatement(sql("SELECT enabled,generation,tab_limit,paste_limit,updated_at FROM p3_exam_modes WHERE contest_id=?"))){p.setString(1,contest);try(ResultSet r=p.executeQuery()){if(r.next())result.putAll(mapOf("enabled",r.getBoolean(1),"generation",r.getString(2),"tabLimit",r.getInt(3),"pasteLimit",r.getInt(4),"updatedAt",r.getLong(5)));}}return result;
    }
    public synchronized Map<String,Object> configure(String contest,boolean enabled,int tabs,int pastes)throws SQLException{
        if(tabs<1||tabs>100||pastes<1||pastes>100)throw new IllegalArgumentException("Ngưỡng cảnh báo phải từ 1 đến 100.");
        try(Connection c=repo.open()){
            c.setAutoCommit(false);Map<String,Object> old=mode(c,contest);String generation=enabled&&!Boolean.TRUE.equals(old.get("enabled"))?UUID.randomUUID().toString():text(old,"generation","");
            try(PreparedStatement p=c.prepareStatement(sql("INSERT INTO p3_exam_modes(contest_id,enabled,generation,tab_limit,paste_limit,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(contest_id) DO UPDATE SET enabled=excluded.enabled,generation=excluded.generation,tab_limit=excluded.tab_limit,paste_limit=excluded.paste_limit,updated_at=excluded.updated_at"))){
                p.setString(1,contest);p.setBoolean(2,enabled);p.setString(3,generation);p.setInt(4,tabs);p.setInt(5,pastes);p.setLong(6,System.currentTimeMillis());p.executeUpdate();
            }
            if(!enabled)try(PreparedStatement p=c.prepareStatement(sql("UPDATE p3_exam_sessions SET absence_ms=absence_ms+CASE WHEN away=1 THEN MAX(0,?-away_since) ELSE 0 END,away=0 WHERE contest_id=?"))){p.setLong(1,System.currentTimeMillis());p.setString(2,contest);p.executeUpdate();}
            c.commit();return mode(c,contest);
        }
    }
    public synchronized Map<String,Object> status(Map<String,Object> contest,Identity who,String loginSession)throws SQLException{
        String id=text(contest,"id","");long now=System.currentTimeMillis();Map<String,Object> result=mode(id);
        boolean live=now>=number(contest,"startAt",Long.MAX_VALUE)&&now<number(contest,"endAt",0);
        result.putAll(mapOf("live",live,"serverTime",now,"endAt",contest.get("endAt"),"startAt",contest.get("startAt")));
        if(who.role()!=Identity.Role.STUDENT||!live||!Boolean.TRUE.equals(result.get("enabled")))return result;
        try(Connection c=repo.open()){
            c.setAutoCommit(false);String generation=text(result,"generation","");
            try(PreparedStatement p=c.prepareStatement(sql("INSERT OR IGNORE INTO p3_exam_sessions(id,contest_id,generation,user_id,connection_hash,username,display_name,started_at,last_activity) VALUES(?,?,?,?,?,?,?,?,?)"))){
                p.setString(1,UUID.randomUUID().toString());p.setString(2,id);p.setString(3,generation);p.setString(4,who.userId());p.setString(5,hash(loginSession));p.setString(6,who.username());p.setString(7,who.displayName());p.setLong(8,now);p.setLong(9,now);p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement(sql("UPDATE p3_exam_sessions SET last_activity=? WHERE contest_id=? AND generation=? AND user_id=? AND connection_hash=?"))){p.setLong(1,now);p.setString(2,id);p.setString(3,generation);p.setString(4,who.userId());p.setString(5,hash(loginSession));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement(sql("SELECT id,tab_count,paste_count,away FROM p3_exam_sessions WHERE contest_id=? AND generation=? AND user_id=? AND connection_hash=?"))){p.setString(1,id);p.setString(2,generation);p.setString(3,who.userId());p.setString(4,hash(loginSession));try(ResultSet r=p.executeQuery()){if(r.next())result.putAll(mapOf("examSessionId",r.getString(1),"tabCount",r.getInt(2),"pasteCount",r.getInt(3),"away",r.getBoolean(4)));}}
            c.commit();return result;
        }
    }
    public synchronized Map<String,Object> event(Map<String,Object> contest,Identity who,String loginSession,Map<String,Object> event)throws SQLException{
        if(who.role()!=Identity.Role.STUDENT)throw new Failure("FORBIDDEN","Chỉ Student gửi tín hiệu làm bài.");
        String id=text(event,"examSessionId",""),eventId=text(event,"eventId",""),type=text(event,"eventType",""),source=text(event,"source","");
        if(!eventId.matches("[A-Za-z0-9-]{16,64}")||!Set.of("PASTE_ATTEMPT","TAB_SWITCH","TAB_RETURN").contains(type))throw new IllegalArgumentException("Sự kiện kiểm tra không hợp lệ.");
        if(!Set.of("hidden","blur","focus","paste","shortcut","beforeinput","drop","pagehide","navigation").contains(source))throw new IllegalArgumentException("Nguồn sự kiện không hợp lệ.");
        long now=System.currentTimeMillis(),clientAt=Math.max(0,Math.min(now+300000,number(event,"clientAt",now)));
        try(Connection c=repo.open()){
            c.setAutoCommit(false);String cid=text(contest,"id","");Map<String,Object> config=mode(c,cid);
            if(!Boolean.TRUE.equals(config.get("enabled"))||now<number(contest,"startAt",Long.MAX_VALUE)||now>=number(contest,"endAt",0))throw new Failure("EXAM_INACTIVE","Chế độ kiểm tra không còn hoạt động.");
            boolean away;long since;
            try(PreparedStatement p=c.prepareStatement(sql("SELECT away,away_since FROM p3_exam_sessions WHERE id=? AND contest_id=? AND generation=? AND user_id=? AND connection_hash=?"))){
                p.setString(1,id);p.setString(2,cid);p.setString(3,text(config,"generation",""));p.setString(4,who.userId());p.setString(5,hash(loginSession));try(ResultSet r=p.executeQuery()){if(!r.next())throw new Failure("EXAM_SESSION_INVALID","Phiên làm bài không thuộc kết nối này.");away=r.getBoolean(1);since=r.getLong(2);}
            }
            try(PreparedStatement p=c.prepareStatement(sql("SELECT 1 FROM p3_exam_events WHERE exam_session_id=? AND event_id=?"))){p.setString(1,id);p.setString(2,eventId);try(ResultSet r=p.executeQuery()){if(r.next()){c.commit();return mapOf("accepted",true,"duplicate",true);}}}
            if((type.equals("TAB_SWITCH")&&away)||(type.equals("TAB_RETURN")&&!away)){c.commit();return mapOf("accepted",true,"duplicate",true);}
            try(PreparedStatement p=c.prepareStatement(sql("SELECT COUNT(*) FROM p3_exam_events WHERE exam_session_id=? AND received_at>?"))){p.setString(1,id);p.setLong(2,now-10000);try(ResultSet r=p.executeQuery()){if(r.next()&&r.getInt(1)>=30)throw new Failure("EXAM_RATE_LIMIT","Quá nhiều sự kiện; sẽ thử đồng bộ lại.");}}
            long duration=type.equals("TAB_RETURN")?Math.max(0,now-since):0;
            try(PreparedStatement p=c.prepareStatement(sql("INSERT INTO p3_exam_events(exam_session_id,event_id,type,source,client_at,received_at,duration_ms) VALUES(?,?,?,?,?,?,?)"))){
                p.setString(1,id);p.setString(2,eventId);p.setString(3,type);p.setString(4,source);p.setLong(5,clientAt);p.setLong(6,now);p.setLong(7,duration);p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement(sql("UPDATE p3_exam_sessions SET last_activity=?,tab_count=tab_count+?,paste_count=paste_count+?,away=?,away_since=?,absence_ms=absence_ms+? WHERE id=?"))){
                p.setLong(1,now);p.setInt(2,type.equals("TAB_SWITCH")?1:0);p.setInt(3,type.equals("PASTE_ATTEMPT")?1:0);p.setBoolean(4,type.equals("TAB_SWITCH")||(away&&!type.equals("TAB_RETURN")));p.setLong(5,type.equals("TAB_SWITCH")?now:since);p.setLong(6,duration);p.setString(7,id);p.executeUpdate();
            }c.commit();return mapOf("accepted",true,"duplicate",false);
        }
    }
    public Map<String,Object> monitor(String contest)throws SQLException{
        Map<String,Object> config=mode(contest);List<Map<String,Object>> students=new ArrayList<>();long now=System.currentTimeMillis();
        try(Connection c=repo.open();PreparedStatement p=c.prepareStatement(sql("SELECT user_id,MAX(username),MAX(display_name),SUM(tab_count),SUM(paste_count),MAX(CASE WHEN last_activity>? THEN away ELSE 0 END),MAX(last_activity),COUNT(*) FROM p3_exam_sessions WHERE contest_id=? AND generation=? GROUP BY user_id ORDER BY MAX(last_activity) DESC"))){
            p.setLong(1,now-30000);p.setString(2,contest);p.setString(3,text(config,"generation",""));try(ResultSet r=p.executeQuery()){while(r.next())students.add(mapOf("userId",r.getString(1),"username",r.getString(2),"displayName",r.getString(3),"tabCount",r.getInt(4),"pasteCount",r.getInt(5),"away",r.getBoolean(6),"lastActivity",r.getLong(7),"online",now-r.getLong(7)<30000,"connections",r.getInt(8),"warning",r.getInt(4)>=number(config,"tabLimit",3)||r.getInt(5)>=number(config,"pasteLimit",3)));}
        }
        return mapOf("mode",config,"students",students,"serverTime",now,"recentEvents",history(contest,"","",Long.MAX_VALUE).stream().filter(e->Objects.equals(e.get("generation"),config.get("generation"))).limit(20).toList());
    }
    public List<Map<String,Object>> history(String contest,String student,String type,long before)throws SQLException{
        if(!type.isEmpty()&&!Set.of("PASTE_ATTEMPT","TAB_SWITCH","TAB_RETURN").contains(type))throw new IllegalArgumentException("Loại sự kiện không hợp lệ.");
        List<Map<String,Object>> result=new ArrayList<>();
        try(Connection c=repo.open();PreparedStatement p=c.prepareStatement(sql("SELECT e.id,s.user_id,s.display_name,e.type,e.source,e.client_at,e.received_at,e.duration_ms,s.generation FROM p3_exam_events e JOIN p3_exam_sessions s ON s.id=e.exam_session_id WHERE s.contest_id=? AND (?='' OR s.user_id=?) AND (?='' OR e.type=?) AND e.id<? ORDER BY e.id DESC LIMIT 100"))){
            p.setString(1,contest);p.setString(2,student);p.setString(3,student);p.setString(4,type);p.setString(5,type);p.setLong(6,before<=0?Long.MAX_VALUE:before);try(ResultSet r=p.executeQuery()){while(r.next())result.add(mapOf("id",r.getLong(1),"userId",r.getString(2),"displayName",r.getString(3),"type",r.getString(4),"source",r.getString(5),"clientAt",r.getLong(6),"receivedAt",r.getLong(7),"durationMs",r.getLong(8),"generation",r.getString(9)));}
        }return result;
    }
}
