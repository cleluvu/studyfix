package studyfix.assignment.persistence;

import studyfix.Identity;
import studyfix.assignment.model.Exercise;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Additive catalog metadata. Exercise rows/testcases are immutable once created. */
public final class QuestionBankRepository {
 private final AssignmentRepository repo;
 public static void init(Connection c)throws SQLException{try(Statement s=c.createStatement()){
  s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_question_bank(exercise_id TEXT PRIMARY KEY REFERENCES p3_exercises(id),root_id TEXT NOT NULL,display_code TEXT NOT NULL,owner_id TEXT NOT NULL DEFAULT '',publication TEXT NOT NULL CHECK(publication IN ('DRAFT','PUBLISHED')),deleted INTEGER NOT NULL DEFAULT 0,is_current INTEGER NOT NULL DEFAULT 1,revision INTEGER NOT NULL DEFAULT 1,updated_at INTEGER NOT NULL)");
  boolean hasScope=false;try(ResultSet columns=s.executeQuery("PRAGMA table_info(p3_question_bank)")){while(columns.next())if("bank_scope".equals(columns.getString("name")))hasScope=true;}
  if(!hasScope)s.executeUpdate("ALTER TABLE p3_question_bank ADD COLUMN bank_scope TEXT NOT NULL DEFAULT 'PRACTICE' CHECK(bank_scope IN ('PRACTICE','EXAM_PRIVATE'))");
  s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS p3_bank_current ON p3_question_bank(root_id) WHERE is_current=1");
 }}
 public QuestionBankRepository(AssignmentRepository repo)throws SQLException{this.repo=repo;try(Connection c=repo.open();Statement s=c.createStatement()){
  s.executeUpdate("INSERT OR IGNORE INTO p3_question_bank(exercise_id,root_id,display_code,publication,updated_at) SELECT id,id,id,'PUBLISHED',created_at FROM p3_exercises");
 }}
 private static void teacher(Identity who){if(who.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ giáo viên được quản lý ngân hàng đề.");}
 public static String scope(String value){if(!Set.of("PRACTICE","EXAM_PRIVATE").contains(value))throw new IllegalArgumentException("Nơi lưu bài tập không hợp lệ.");return value;}
 private static boolean manages(Map<String,Object> row,Identity who){return who.role()==Identity.Role.TEACHER&&(text(row,"ownerId","").isBlank()||who.userId().equals(row.get("ownerId")));}
 private Map<String,Object> metadata(Connection c,String id)throws SQLException{
  try(PreparedStatement p=c.prepareStatement("SELECT b.*,e.title,e.topic,e.difficulty,e.mode,e.java_structure,e.entry_file FROM p3_question_bank b JOIN p3_exercises e ON e.id=b.exercise_id WHERE b.exercise_id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy đề.");return mapOf("id",id,"rootId",r.getString("root_id"),"displayCode",r.getString("display_code"),"ownerId",r.getString("owner_id"),"bankScope",r.getString("bank_scope"),"publication",r.getString("publication"),"deleted",r.getInt("deleted")!=0,"current",r.getInt("is_current")!=0,"revision",r.getInt("revision"),"updatedAt",r.getLong("updated_at"),"title",r.getString("title"),"topic",r.getString("topic"),"difficulty",r.getString("difficulty"),"mode",r.getString("mode"),"javaStructure",r.getString("java_structure"),"entryFile",r.getString("entry_file"));}}
 }
 public Map<String,Object> info(String id,Identity who)throws SQLException{teacher(who);try(Connection c=repo.open()){Map<String,Object> row=metadata(c,id);if(Boolean.TRUE.equals(row.get("deleted"))||!Boolean.TRUE.equals(row.get("current"))||(!manages(row,who)&&(!"PUBLISHED".equals(row.get("publication"))||"EXAM_PRIVATE".equals(row.get("bankScope")))))throw new SecurityException("Đề không còn khả dụng hoặc bạn không có quyền xem.");row.put("canManage",manages(row,who));row.remove("ownerId");return row;}}
 public List<Map<String,Object>> list(Identity who)throws SQLException{teacher(who);List<Map<String,Object>> out=new ArrayList<>();try(Connection c=repo.open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT exercise_id FROM p3_question_bank WHERE deleted=0 AND is_current=1 ORDER BY display_code")){
  while(r.next()){Map<String,Object> row=metadata(c,r.getString(1));if(!manages(row,who)&&(!"PUBLISHED".equals(row.get("publication"))||"EXAM_PRIVATE".equals(row.get("bankScope"))))continue;
   try(PreparedStatement p=c.prepareStatement("SELECT COUNT(DISTINCT cp.contest_id) FROM p3_contest_problems cp JOIN p3_question_bank v ON v.exercise_id=cp.exercise_id WHERE v.root_id=?")){p.setString(1,text(row,"rootId",""));try(ResultSet n=p.executeQuery()){row.put("usageCount",n.next()?n.getInt(1):0);}}
   row.put("canManage",manages(row,who));row.remove("ownerId");out.add(row);
  }}return out;
 }
 public void requirePublished(String id)throws SQLException{try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_question_bank WHERE exercise_id=? AND publication='PUBLISHED' AND deleted=0 AND is_current=1")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Đề chưa xuất bản, đã thay phiên bản hoặc đã xóa. Hãy tải lại ngân hàng.");}}}
 public void requirePracticePublished(String id)throws SQLException{requirePublished(id);try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_question_bank WHERE exercise_id=? AND bank_scope='EXAM_PRIVATE'")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(r.next())throw new SecurityException("Bài tập riêng tư không thuộc Luyện tập.");}}}
 public String save(Identity who,Exercise e,String publication,boolean update,int expected)throws SQLException{
  return save(who,e,publication,update,expected,null);
 }
 public String save(Identity who,Exercise e,String publication,boolean update,int expected,String bankScope)throws SQLException{
  teacher(who);if(!Set.of("DRAFT","PUBLISHED").contains(publication))throw new IllegalArgumentException("Trạng thái xuất bản không hợp lệ.");
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{
   String id=e.id(),root=id,code=id;int revision=1;String targetScope=scope(bankScope==null?"PRACTICE":bankScope);
   if(update){Map<String,Object> old=metadata(c,id);if(!manages(old,who))throw new SecurityException("Bạn không có quyền sửa đề này.");if(!Boolean.TRUE.equals(old.get("current"))||Boolean.TRUE.equals(old.get("deleted"))||integer(old,"revision",0)!=expected)throw new IllegalArgumentException("Đề đã thay đổi. Hãy mở lại trước khi sửa.");if(bankScope==null)targetScope=text(old,"bankScope","PRACTICE");root=text(old,"rootId",id);code=text(old,"displayCode",id);revision=expected+1;id=code.substring(0,Math.min(code.length(),35))+"_v"+revision+"_"+UUID.randomUUID().toString().substring(0,8);
    try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_bank SET is_current=0 WHERE exercise_id=?")){p.setString(1,e.id());p.executeUpdate();}
   }
   try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_exercises(id,title,topic,difficulty,statement,input_format,output_format,constraints_text,template,hints,reference_solution,catalog_version,created_at,mode,starter_code) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
    int i=1;p.setString(i++,id);p.setString(i++,e.title());p.setString(i++,e.topic());p.setString(i++,e.difficulty());p.setString(i++,e.statement());p.setString(i++,e.inputFormat());p.setString(i++,e.outputFormat());p.setString(i++,e.constraints());p.setString(i++,e.template());p.setString(i++,e.hints());p.setString(i++,e.referenceSolution());p.setInt(i++,revision);p.setLong(i++,System.currentTimeMillis());p.setString(i++,e.mode().name());p.setString(i,e.starterCode());p.executeUpdate();
   }
   try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_test_cases(exercise_id,ordinal_no,input_text,expected_output,weight,hidden) VALUES(?,?,?,?,?,?)")){int n=0;for(Exercise.TestCase t:e.tests()){p.setString(1,id);p.setInt(2,++n);p.setString(3,t.input());p.setString(4,t.expected());p.setInt(5,t.weight());p.setInt(6,t.hidden()?1:0);p.addBatch();}p.executeBatch();}
   try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_question_bank(exercise_id,root_id,display_code,owner_id,publication,revision,updated_at,bank_scope) VALUES(?,?,?,?,?,?,?,?)")){p.setString(1,id);p.setString(2,root);p.setString(3,code);p.setString(4,who.userId());p.setString(5,publication);p.setInt(6,revision);p.setLong(7,System.currentTimeMillis());p.setString(8,targetScope);p.executeUpdate();}
   if(update)try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_week_items SET exercise_id=? WHERE exercise_id=?")){p.setString(1,id);p.setString(2,e.id());p.executeUpdate();}
   tx.execute("COMMIT");return id;
  }catch(SQLException|RuntimeException ex){tx.execute("ROLLBACK");throw ex;}}
 }
 public void delete(Identity who,String id,int expected)throws SQLException{teacher(who);try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{Map<String,Object> row=metadata(c,id);if(!manages(row,who))throw new SecurityException("Bạn không có quyền xóa đề này.");if(!Boolean.TRUE.equals(row.get("current"))||Boolean.TRUE.equals(row.get("deleted"))||integer(row,"revision",0)!=expected)throw new IllegalArgumentException("Đề đã thay đổi. Hãy tải lại danh sách.");try(PreparedStatement p=c.prepareStatement("UPDATE p3_question_bank SET deleted=1,updated_at=? WHERE root_id=?")){p.setLong(1,System.currentTimeMillis());p.setString(2,text(row,"rootId",""));p.executeUpdate();}tx.execute("COMMIT");}catch(SQLException|RuntimeException ex){tx.execute("ROLLBACK");throw ex;}}}
}
