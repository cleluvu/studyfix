package studyfix.assignment.persistence;
import studyfix.Identity;
import studyfix.assignment.catalog.OjPackage;
import studyfix.assignment.model.Exercise;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Private, immutable judge snapshots plus an explicitly replaced active catalog. */
public final class OjRepository {
 private final AssignmentRepository repo;
 public OjRepository(AssignmentRepository repo)throws SQLException{this.repo=repo;try(Connection c=repo.open();Statement s=c.createStatement()){
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_oj_exercises(exercise_id TEXT PRIMARY KEY REFERENCES p3_exercises(id),package_sha TEXT NOT NULL,canonical_code TEXT NOT NULL,config_json TEXT NOT NULL,metadata_json TEXT NOT NULL,validation_json TEXT NOT NULL DEFAULT '{}',validated_at INTEGER NOT NULL DEFAULT 0)");
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_oj_imports(package_sha TEXT PRIMARY KEY,owner_id TEXT NOT NULL,imported_at INTEGER NOT NULL,report_json TEXT NOT NULL)");
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_oj_settings(name TEXT PRIMARY KEY,value TEXT NOT NULL)");
 }}
 public boolean active()throws SQLException{try(Connection c=repo.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT 1 FROM p3_oj_settings WHERE name='active_package'")){return r.next();}}
 public Map<String,Object> config(String exercise)throws SQLException{try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT config_json FROM p3_oj_exercises WHERE exercise_id=?")){p.setString(1,exercise);try(ResultSet r=p.executeQuery()){return r.next()?object(r.getString(1)):null;}}}
 public Map<String,Object> validation(String id)throws SQLException{try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT validation_json FROM p3_oj_exercises WHERE exercise_id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?object(r.getString(1)):Map.of();}}}
 public void validation(String id,Map<String,Object> result)throws SQLException{try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("UPDATE p3_oj_exercises SET validation_json=?,validated_at=? WHERE exercise_id=?")){p.setString(1,stringify(result));p.setLong(2,System.currentTimeMillis());p.setString(3,id);p.executeUpdate();}}
 public List<String> activeIds()throws SQLException{try(Connection c=repo.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT o.exercise_id FROM p3_oj_exercises o JOIN p3_question_bank b ON b.exercise_id=o.exercise_id WHERE b.is_current=1 AND b.deleted=0 ORDER BY o.canonical_code")){List<String> ids=new ArrayList<>();while(r.next())ids.add(r.getString(1));return ids;}}
 public Map<String,Object> importPackage(OjPackage pack,Identity teacher,boolean replaceAll)throws SQLException{
  if(teacher.role()!=Identity.Role.TEACHER)throw new SecurityException("Teacher only");Map<String,String> mapping=new LinkedHashMap<>();int inserted=0,reused=0;long now=System.currentTimeMillis();
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{
   if(replaceAll)tx.executeUpdate("UPDATE p3_question_bank SET deleted=1 WHERE is_current=1");
   for(var problem:pack.problems){String id=null,root=problem.code();int revision=1;
    try(PreparedStatement p=c.prepareStatement("SELECT exercise_id FROM p3_oj_exercises WHERE canonical_code=? AND package_sha=?")){p.setString(1,problem.code());p.setString(2,pack.sha256);try(ResultSet r=p.executeQuery()){if(r.next())id=r.getString(1);}}
    if(id!=null){
     try(PreparedStatement p=c.prepareStatement("SELECT owner_id,is_current FROM p3_question_bank WHERE exercise_id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getBoolean(2))throw new IllegalStateException("Gói này là phiên bản cũ, không khôi phục đè phiên bản mới.");if(!replaceAll&&!teacher.userId().equals(r.getString(1)))throw new SecurityException("Không được thay gói của giáo viên khác.");}}
     repairKnownPendingFixture(c,id,problem);
     try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_bank SET deleted=0 WHERE exercise_id=? AND is_current=1")){p.setString(1,id);p.executeUpdate();}mapping.put(problem.code(),id);reused++;continue;}
    try(PreparedStatement p=c.prepareStatement("SELECT root_id,revision,owner_id FROM p3_question_bank WHERE display_code=? ORDER BY revision DESC LIMIT 1")){p.setString(1,problem.code());try(ResultSet r=p.executeQuery()){if(r.next()){if(!replaceAll&&!r.getString(3).isBlank()&&!teacher.userId().equals(r.getString(3)))throw new SecurityException("Không được tạo version đè đề của giáo viên khác.");root=r.getString(1);revision=r.getInt(2)+1;}}}
    id=problem.code();try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_exercises WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(r.next())id=problem.code()+".oj."+pack.sha256.substring(0,12);}}
    try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_bank SET is_current=0 WHERE root_id=?")){p.setString(1,root);p.executeUpdate();}
    Exercise e=problem.exercise();try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_exercises(id,title,topic,difficulty,statement,input_format,output_format,constraints_text,template,hints,reference_solution,catalog_version,created_at,mode,starter_code,java_structure,entry_file) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
     int n=1;for(String value:List.of(id,e.title(),e.topic(),e.difficulty(),e.statement(),e.inputFormat(),e.outputFormat(),e.constraints(),e.template(),e.hints(),e.referenceSolution()))p.setString(n++,value);p.setInt(n++,revision);p.setLong(n++,now);p.setString(n++,e.mode().name());p.setString(n++,e.starterCode());p.setString(n++,problem.structure());p.setString(n,e.entryFile());p.executeUpdate();}
    try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_test_cases(exercise_id,ordinal_no,input_text,expected_output,weight,hidden) VALUES(?,?,?,?,?,?)")){int ordinal=0;for(var test:e.tests()){p.setString(1,id);p.setInt(2,++ordinal);p.setString(3,test.input());p.setString(4,test.expected());p.setInt(5,test.weight());p.setBoolean(6,test.hidden());p.addBatch();}p.executeBatch();}
    try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_question_bank(exercise_id,root_id,display_code,owner_id,publication,revision,updated_at) VALUES(?,?,?,?,'DRAFT',?,?)")){p.setString(1,id);p.setString(2,root);p.setString(3,problem.code());p.setString(4,teacher.userId());p.setInt(5,revision);p.setLong(6,now);p.executeUpdate();}
    try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_oj_exercises(exercise_id,package_sha,canonical_code,config_json,metadata_json) VALUES(?,?,?,?,?)")){p.setString(1,id);p.setString(2,pack.sha256);p.setString(3,problem.code());p.setString(4,stringify(problem.config()));p.setString(5,stringify(problem.metadata()));p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("UPDATE OR IGNORE p3_question_week_items SET exercise_id=? WHERE exercise_id IN (SELECT exercise_id FROM p3_question_bank WHERE root_id=? AND exercise_id<>?)")){p.setString(1,id);p.setString(2,root);p.setString(3,id);p.executeUpdate();}mapping.put(problem.code(),id);inserted++;
   }
   // Keep existing Week members; package imports add links, never clear unrelated links.
   for(var week:pack.weeks){int number=integer(week,"week_number",0);String id=null;try(PreparedStatement p=c.prepareStatement("SELECT id,owner_id FROM p3_question_weeks WHERE week_no=?")){p.setInt(1,number);try(ResultSet r=p.executeQuery()){if(r.next()){if(!replaceAll&&!teacher.userId().equals(r.getString(2)))throw new SecurityException("Không được thay nội dung Week của giáo viên khác.");id=r.getString(1);}}}
    if(id==null){id=UUID.randomUUID().toString();try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_question_weeks(id,week_no,title,description,owner_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")){p.setString(1,id);p.setInt(2,number);p.setString(3,text(week,"title",""));p.setString(4,text(week,"learning_topics",""));p.setString(5,teacher.userId());p.setLong(6,now);p.setLong(7,now);p.executeUpdate();}}
    else try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_weeks SET title=?,description=?,updated_at=? WHERE id=?")){p.setString(1,text(week,"title",""));p.setString(2,text(week,"learning_topics",""));p.setLong(3,now);p.setString(4,id);p.executeUpdate();}
    try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO p3_question_week_items VALUES(?,?,?)")){for(Object code:list(week.get("problem_ids"))){p.setString(1,id);p.setString(2,mapping.get(String.valueOf(code)));p.setLong(3,now);p.addBatch();}p.executeBatch();}
   }

   Map<String,Object> report=mapOf("inserted",inserted,"reused",reused,"problems",pack.problems.size(),"weeks",pack.weeks.size(),"tests",pack.tests,"sha256",pack.sha256,"publication","DRAFT");
   try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO p3_oj_imports VALUES(?,?,?,?)")){p.setString(1,pack.sha256);p.setString(2,teacher.userId());p.setLong(3,now);p.setString(4,stringify(report));p.executeUpdate();}
   try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_oj_settings VALUES('active_package',?) ON CONFLICT(name) DO UPDATE SET value=excluded.value")){p.setString(1,pack.sha256);p.executeUpdate();}tx.execute("COMMIT");return report;
  }catch(SQLException|RuntimeException e){tx.execute("ROLLBACK");throw e;}}
 }
 private void repairKnownPendingFixture(Connection c,String id,OjPackage.Problem problem)throws SQLException{
  if(!problem.code().equals("JAVA092")||!problem.metadata().containsKey("import_corrections"))return;List<String> actual=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT input_text FROM p3_test_cases WHERE exercise_id=? ORDER BY ordinal_no")){p.setString(1,id);try(ResultSet r=p.executeQuery()){while(r.next())actual.add(r.getString(1));}}
  if(actual.equals(problem.exercise().tests().stream().map(Exercise.TestCase::input).toList()))return;
  try(PreparedStatement p=c.prepareStatement("SELECT publication FROM p3_question_bank WHERE exercise_id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||!"DRAFT".equals(r.getString(1)))throw new IllegalStateException("Không sửa fixture snapshot đã phát hành; cần version mới.");}}
  for(String table:List.of("p3_assignments","p3_contest_problems","p3_practice_submissions","p3_teacher_trials")){try(PreparedStatement exists=c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")){exists.setString(1,table);try(ResultSet present=exists.executeQuery()){if(!present.next())continue;}}
   try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM "+table+" WHERE exercise_id=? LIMIT 1")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalStateException("Fixture đã được sử dụng; từ chối ghi đè.");}}
  }
  try(PreparedStatement p=c.prepareStatement("UPDATE p3_test_cases SET input_text=? WHERE exercise_id=? AND ordinal_no=?")){int ordinal=0;for(var test:problem.exercise().tests()){p.setString(1,test.input());p.setString(2,id);p.setInt(3,++ordinal);p.addBatch();}p.executeBatch();}
  try(PreparedStatement p=c.prepareStatement("UPDATE p3_oj_exercises SET metadata_json=?,validation_json='{}',validated_at=0 WHERE exercise_id=?")){p.setString(1,stringify(problem.metadata()));p.setString(2,id);p.executeUpdate();}
 }
 public void publish(Identity who,String id)throws SQLException{publish(who,List.of(id));}
 public void publish(Identity who,List<String> ids)throws SQLException{
  if(who.role()!=Identity.Role.TEACHER||ids.isEmpty())throw new SecurityException("Chọn đề cần duyệt.");
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{for(String id:new LinkedHashSet<>(ids)){
   Map<String,Object> validation;try(PreparedStatement p=c.prepareStatement("SELECT b.owner_id,b.deleted,b.is_current,o.validation_json FROM p3_question_bank b JOIN p3_oj_exercises o ON o.exercise_id=b.exercise_id WHERE b.exercise_id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||!who.userId().equals(r.getString(1))||r.getBoolean(2)||!r.getBoolean(3))throw new SecurityException("Không được phát hành đề này.");validation=object(r.getString(4));}}
   if(!Boolean.TRUE.equals(validation.get("referencePassed"))||!Boolean.TRUE.equals(validation.get("wrongDetected")))throw new IllegalStateException("Đề chưa qua reference và kiểm tra starter sai: "+id);
   validation.put("educationalReview","APPROVED");validation.put("approvedBy",who.userId());validation.put("approvedAt",System.currentTimeMillis());
   try(PreparedStatement p=c.prepareStatement("UPDATE p3_oj_exercises SET validation_json=? WHERE exercise_id=?")){p.setString(1,stringify(validation));p.setString(2,id);p.executeUpdate();}
   try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_bank SET publication='PUBLISHED' WHERE exercise_id=?")){p.setString(1,id);p.executeUpdate();}
  }tx.execute("COMMIT");}catch(SQLException|RuntimeException failure){tx.execute("ROLLBACK");throw failure;}}
 }
}
