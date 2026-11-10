package studyfix.assignment.persistence;
import studyfix.Identity;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;
/** Week groups reference the existing question bank, never copy or delete questions. */
public final class QuestionWeekRepository {
 private final AssignmentRepository repo;
 public QuestionWeekRepository(AssignmentRepository repo)throws SQLException{
  this.repo=repo;try(Connection c=repo.open();Statement s=c.createStatement()){
   s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_question_weeks(id TEXT PRIMARY KEY,week_no INTEGER NOT NULL UNIQUE,title TEXT NOT NULL,description TEXT NOT NULL,owner_id TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_question_week_items(week_id TEXT NOT NULL REFERENCES p3_question_weeks(id) ON DELETE CASCADE,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id),added_at INTEGER NOT NULL,PRIMARY KEY(week_id,exercise_id))");
  }
 }
 private static void teacher(Identity who){if(who.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ giáo viên được quản lý Week.");}
 private Map<String,Object> read(Connection c,String id,Identity who)throws SQLException{
  try(PreparedStatement p=c.prepareStatement("SELECT * FROM p3_question_weeks WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Week không còn tồn tại.");return mapOf("id",id,"weekNo",r.getInt("week_no"),"title",r.getString("title"),"description",r.getString("description"),"isMine",who.role()==Identity.Role.TEACHER&&who.userId().equals(r.getString("owner_id")),"createdAt",r.getLong("created_at"),"updatedAt",r.getLong("updated_at"));}}
 }
 private void owned(Connection c,String id,Identity who)throws SQLException{teacher(who);if(!Boolean.TRUE.equals(read(c,id,who).get("isMine")))throw new SecurityException("Chỉ giáo viên tạo Week được chỉnh sửa.");}
 public Set<String> exerciseIds(String id)throws SQLException{return exerciseIds(id,new Identity("","","",Identity.Role.STUDENT));}
 public Set<String> exerciseIds(String id,Identity who)throws SQLException{
  try(Connection c=repo.open()){read(c,id,who);return items(c,id,who);}
 }
 private Set<String> items(Connection c,String id,Identity who)throws SQLException{Set<String> out=new LinkedHashSet<>();try(PreparedStatement p=c.prepareStatement("SELECT w.exercise_id FROM p3_question_week_items w LEFT JOIN p3_question_bank b ON b.exercise_id=w.exercise_id WHERE w.week_id=? AND (b.exercise_id IS NULL OR (b.deleted=0 AND b.is_current=1 AND (b.publication='PUBLISHED' OR (?='TEACHER' AND (b.owner_id='' OR b.owner_id=?))))) ORDER BY w.added_at,w.exercise_id")){p.setString(1,id);p.setString(2,who.role().name());p.setString(3,who.userId());try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getString(1));}}return out;}
 public List<Map<String,Object>> list(Identity who)throws SQLException{
  List<Map<String,Object>> out=new ArrayList<>();try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT id FROM p3_question_weeks ORDER BY week_no")){try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> row=read(c,r.getString(1),who);Set<String> ids=items(c,r.getString(1),who);row.put("exerciseIds",new ArrayList<>(ids));row.put("exerciseCount",ids.size());out.add(row);}}}return out;
 }
 public String save(Identity who,String id,int number,String title,String description)throws SQLException{
  teacher(who);if(number<1||number>99||title.isBlank()||title.length()>200||description.length()>4000)throw new IllegalArgumentException("Week 1–99; tên 1–200 ký tự; nội dung tối đa 4000 ký tự.");
  String target=id.isBlank()?UUID.randomUUID().toString():id;long now=System.currentTimeMillis();
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{
   if(!id.isBlank())owned(c,id,who);
   try(PreparedStatement p=c.prepareStatement("SELECT id FROM p3_question_weeks WHERE week_no=? AND id<>?")){p.setInt(1,number);p.setString(2,target);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("Số Week đã tồn tại.");}}
   if(id.isBlank())try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_question_weeks VALUES(?,?,?,?,?,?,?)")){p.setString(1,target);p.setInt(2,number);p.setString(3,title);p.setString(4,description);p.setString(5,who.userId());p.setLong(6,now);p.setLong(7,now);p.executeUpdate();}
   else try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_weeks SET week_no=?,title=?,description=?,updated_at=? WHERE id=?")){p.setInt(1,number);p.setString(2,title);p.setString(3,description);p.setLong(4,now);p.setString(5,target);p.executeUpdate();}
   tx.execute("COMMIT");return target;
  }catch(SQLException|RuntimeException e){tx.execute("ROLLBACK");throw e;}}
 }
 public void changeItems(Identity who,String id,List<String> requested,boolean add)throws SQLException{
  teacher(who);Set<String> ids=new LinkedHashSet<>(requested);if(ids.isEmpty()||ids.size()>100)throw new IllegalArgumentException("Chọn 1–100 câu hỏi.");
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{owned(c,id,who);
   for(String exercise:ids){try(PreparedStatement p=c.prepareStatement(add?"SELECT 1 FROM p3_exercises e LEFT JOIN p3_question_bank b ON b.exercise_id=e.id WHERE e.id=? AND (b.exercise_id IS NULL OR (b.deleted=0 AND b.is_current=1 AND (b.publication='PUBLISHED' OR b.owner_id='' OR b.owner_id=?)))":"SELECT 1 FROM p3_exercises WHERE id=?")){p.setString(1,exercise);if(add)p.setString(2,who.userId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Mã câu hỏi không tồn tại: "+exercise);}}}
   try(PreparedStatement p=c.prepareStatement(add?"INSERT OR IGNORE INTO p3_question_week_items VALUES(?,?,?)":"DELETE FROM p3_question_week_items WHERE week_id=? AND exercise_id=?")){for(String exercise:ids){p.setString(1,id);p.setString(2,exercise);if(add)p.setLong(3,System.currentTimeMillis());p.addBatch();}p.executeBatch();}
   tx.execute("COMMIT");
  }catch(SQLException|RuntimeException e){tx.execute("ROLLBACK");throw e;}}
 }
 public void delete(Identity who,String id)throws SQLException{teacher(who);try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{owned(c,id,who);try(PreparedStatement p=c.prepareStatement("DELETE FROM p3_question_weeks WHERE id=?")){p.setString(1,id);p.executeUpdate();}tx.execute("COMMIT");}catch(SQLException|RuntimeException e){tx.execute("ROLLBACK");throw e;}}}
}
