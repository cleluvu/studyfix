package studyfix.assignment.persistence;

import java.sql.*;
import java.util.*;
import java.security.SecureRandom;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Persist a single random draw per authenticated user, atomically across connections. */
public final class ContestDistributionRepository {
 private static final SecureRandom RANDOM=new SecureRandom();
 public static void init(Connection c)throws SQLException{try(Statement s=c.createStatement()){
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_audience(contest_id TEXT PRIMARY KEY REFERENCES p3_contests(id),restricted INTEGER NOT NULL DEFAULT 1)");
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_distribution(contest_id TEXT PRIMARY KEY REFERENCES p3_contests(id) ON DELETE CASCADE,mode TEXT NOT NULL CHECK(mode IN ('ALL','RANDOM')),problem_count INTEGER NOT NULL CHECK(problem_count>=0))");
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_draws(contest_id TEXT NOT NULL REFERENCES p3_contests(id),user_id TEXT NOT NULL,created_at INTEGER NOT NULL,username TEXT NOT NULL,display_name TEXT NOT NULL,PRIMARY KEY(contest_id,user_id))");
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_student_problems(contest_id TEXT NOT NULL,user_id TEXT NOT NULL,exercise_id TEXT NOT NULL,ordinal_no INTEGER NOT NULL,PRIMARY KEY(contest_id,user_id,exercise_id),UNIQUE(contest_id,user_id,ordinal_no),FOREIGN KEY(contest_id,user_id) REFERENCES p3_contest_draws(contest_id,user_id))");
 }}
 static void policy(Connection c,String id,String mode,int count)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_distribution VALUES(?,?,?)")){p.setString(1,id);p.setString(2,mode);p.setInt(3,count);p.executeUpdate();}}
 static Map<String,Object> config(Connection c,String id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT mode,problem_count FROM p3_contest_distribution WHERE contest_id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?mapOf("distributionMode",r.getString(1),"problemsPerStudent",r.getInt(2)):mapOf("distributionMode","ALL","problemsPerStudent",0);}}}
 public static List<String> studentProblems(AssignmentRepository repo,String id,studyfix.Identity student)throws SQLException{
  String user=student.userId();
  try(Connection c=repo.open();Statement tx=c.createStatement()){
   tx.execute("BEGIN IMMEDIATE");
   try{
    Map<String,Object> config=config(c,id);List<String> pool=new ArrayList<>();
    try(PreparedStatement p=c.prepareStatement("SELECT exercise_id FROM p3_contest_problems WHERE contest_id=? ORDER BY rowid")){p.setString(1,id);try(ResultSet r=p.executeQuery()){while(r.next())pool.add(r.getString(1));}}
    boolean drawn;try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_contest_draws WHERE contest_id=? AND user_id=?")){p.setString(1,id);p.setString(2,user);try(ResultSet r=p.executeQuery()){drawn=r.next();}}
    if(!drawn&&!allowed(c,id,user))throw new SecurityException("Bạn chưa được giao kỳ thi này.");
    if(!drawn&&!"RANDOM".equals(config.get("distributionMode"))){tx.execute("COMMIT");return pool;}
    if(!drawn){
     int count=((Number)config.get("problemsPerStudent")).intValue();if(count<1||count>pool.size())throw new SQLException("Invalid random problem count");
     Collections.shuffle(pool,RANDOM);
     try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_draws VALUES(?,?,?,?,?)")){p.setString(1,id);p.setString(2,user);p.setLong(3,System.currentTimeMillis());p.setString(4,student.username());p.setString(5,student.displayName());p.executeUpdate();}
     try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_student_problems VALUES(?,?,?,?)")){for(int i=0;i<count;i++){p.setString(1,id);p.setString(2,user);p.setString(3,pool.get(i));p.setInt(4,i+1);p.addBatch();}p.executeBatch();}
    }
    List<String> result=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT exercise_id FROM p3_contest_student_problems WHERE contest_id=? AND user_id=? ORDER BY ordinal_no")){p.setString(1,id);p.setString(2,user);try(ResultSet r=p.executeQuery()){while(r.next())result.add(r.getString(1));}}
    tx.execute("COMMIT");return result;
   }catch(Exception e){tx.execute("ROLLBACK");if(e instanceof SQLException x)throw x;throw new SQLException(e);}
  }
 }
 private static boolean allowed(Connection c,String id,String user)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM p3_contest_audience WHERE contest_id=? AND restricted=1) OR EXISTS(SELECT 1 FROM p3_contest_draws WHERE contest_id=? AND user_id=?)")){p.setString(1,id);p.setString(2,id);p.setString(3,user);try(ResultSet r=p.executeQuery()){return r.next();}}}
 public static int allocatedCount(AssignmentRepository repo,String id,String user)throws SQLException{try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM p3_contest_student_problems WHERE contest_id=? AND user_id=?")){p.setString(1,id);p.setString(2,user);try(ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):0;}}}
 public static boolean canAccess(AssignmentRepository repo,String id,String user)throws SQLException{try(Connection c=repo.open()){return allowed(c,id,user);}}
 /** Append recipients; previously drawn or submitted exams never get silently replaced. */
 public static void assign(AssignmentRepository repo,String id,List<studyfix.Identity> students,List<String> exercises)throws SQLException{
  try(Connection c=repo.open();Statement tx=c.createStatement()){
   tx.execute("BEGIN IMMEDIATE");try{
    for(studyfix.Identity student:students){
     boolean exists;try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_contest_draws WHERE contest_id=? AND user_id=?")){p.setString(1,id);p.setString(2,student.userId());try(ResultSet r=p.executeQuery()){exists=r.next();}}
     if(exists)continue;
     // Preserve previously submitted problems if an open contest is assigned later.
     List<String> submitted=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT DISTINCT exercise_id FROM p3_contest_problem_submissions WHERE contest_id=? AND user_id=?")){p.setString(1,id);p.setString(2,student.userId());try(ResultSet r=p.executeQuery()){while(r.next())submitted.add(r.getString(1));}}
     if(!exercises.containsAll(submitted))throw new IllegalArgumentException("Sinh viên đã nộp bài khác trong kỳ thi; hãy tạo kỳ thi mới để giao bộ đề khác.");
     try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_draws VALUES(?,?,?,?,?)")){p.setString(1,id);p.setString(2,student.userId());p.setLong(3,System.currentTimeMillis());p.setString(4,student.username());p.setString(5,student.displayName());p.executeUpdate();}
     try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_student_problems VALUES(?,?,?,?)")){for(int i=0;i<exercises.size();i++){p.setString(1,id);p.setString(2,student.userId());p.setString(3,exercises.get(i));p.setInt(4,i+1);p.addBatch();}p.executeBatch();}
    }
    try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_audience VALUES(?,1) ON CONFLICT(contest_id) DO UPDATE SET restricted=1")){p.setString(1,id);p.executeUpdate();}
    tx.execute("COMMIT");
   }catch(Exception e){tx.execute("ROLLBACK");if(e instanceof SQLException x)throw x;if(e instanceof IllegalArgumentException x)throw x;throw new SQLException(e);}
  }
 }
 public static List<Map<String,Object>> draws(AssignmentRepository repo,String id)throws SQLException{
  List<Map<String,Object>> result=new ArrayList<>();try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT p.user_id,p.exercise_id,p.ordinal_no,d.username,d.display_name FROM p3_contest_student_problems p JOIN p3_contest_draws d ON d.contest_id=p.contest_id AND d.user_id=p.user_id WHERE p.contest_id=? ORDER BY p.user_id,p.ordinal_no")){p.setString(1,id);try(ResultSet r=p.executeQuery()){while(r.next())result.add(mapOf("userId",r.getString(1),"exerciseId",r.getString(2),"ordinal",r.getInt(3),"username",r.getString(4),"displayName",r.getString(5)));}}return result;
 }
}
