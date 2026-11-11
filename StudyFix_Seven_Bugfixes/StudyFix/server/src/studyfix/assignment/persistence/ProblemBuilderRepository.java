package studyfix.assignment.persistence;

import studyfix.Identity;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Mutable author workspace, kept separate from immutable published exercises. */
public final class ProblemBuilderRepository {
 public static final class Failure extends RuntimeException { public final String code;public Failure(String code,String message){super(message);this.code=code;} }
 private final AssignmentRepository repo;
 private static final Set<String> KEYS=Set.of("schemaVersion","title","code","javaStructure","difficulty","estimatedMinutes","weekIds","learningOutcome","summary","visibility","statement","template","tests","grading","reference","incorrectSample","testGroups","topic","bankScope");
 public ProblemBuilderRepository(AssignmentRepository repo)throws SQLException{
  this.repo=repo;try(Connection c=repo.open();Statement s=c.createStatement()){
   s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_builder_drafts(id TEXT PRIMARY KEY,owner_id TEXT NOT NULL,base_exercise_id TEXT REFERENCES p3_exercises(id),base_revision INTEGER NOT NULL DEFAULT 0,code TEXT NOT NULL,revision INTEGER NOT NULL,current_step INTEGER NOT NULL,payload_json TEXT NOT NULL,content_hash TEXT NOT NULL,request_key TEXT NOT NULL,creation_hash TEXT NOT NULL,deleted INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(owner_id,request_key))");
   boolean creationHash=false;try(ResultSet columns=s.executeQuery("PRAGMA table_info(p3_builder_drafts)")){while(columns.next())if("creation_hash".equals(columns.getString("name")))creationHash=true;}
   if(!creationHash){s.executeUpdate("ALTER TABLE p3_builder_drafts ADD COLUMN creation_hash TEXT NOT NULL DEFAULT ''");s.executeUpdate("UPDATE p3_builder_drafts SET creation_hash=content_hash WHERE creation_hash=''");}
   s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS p3_builder_code ON p3_builder_drafts(code) WHERE deleted=0 AND base_exercise_id IS NULL AND code<>''");
   s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_builder_author ON p3_builder_drafts(owner_id,deleted,updated_at)");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_builder_history(draft_id TEXT NOT NULL REFERENCES p3_builder_drafts(id),revision INTEGER NOT NULL,current_step INTEGER NOT NULL,payload_json TEXT NOT NULL,content_hash TEXT NOT NULL,actor_id TEXT NOT NULL,created_at INTEGER NOT NULL,PRIMARY KEY(draft_id,revision))");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_builder_sequences(name TEXT PRIMARY KEY,next_value INTEGER NOT NULL)");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_builder_checks(id INTEGER PRIMARY KEY AUTOINCREMENT,draft_id TEXT NOT NULL REFERENCES p3_builder_drafts(id),content_hash TEXT NOT NULL,check_kind TEXT NOT NULL,result_json TEXT NOT NULL,created_at INTEGER NOT NULL)");
   s.executeUpdate("INSERT OR IGNORE INTO p3_builder_sequences VALUES('JAVA',1)");
  }
 }
 private static void teacher(Identity who){if(who.role()!=Identity.Role.TEACHER)throw new Failure("FORBIDDEN","Chỉ giáo viên được tạo đề.");}
 private Map<String,Object> row(ResultSet r,boolean payload)throws SQLException{
  Map<String,Object> out=mapOf("id",r.getString("id"),"code",r.getString("code"),"baseExerciseId",Objects.toString(r.getString("base_exercise_id"),""),"baseRevision",r.getInt("base_revision"),"revision",r.getInt("revision"),"currentStep",r.getInt("current_step"),"contentHash",r.getString("content_hash"),"workflow","DRAFT","createdAt",r.getLong("created_at"),"updatedAt",r.getLong("updated_at"));
  Map<String,Object> data=map(parse(r.getString("payload_json")));out.put("title",text(data,"title",""));out.put("javaStructure",text(data,"javaStructure","PROGRAM"));if(payload)out.put("payload",data);return out;
 }
 Map<String,Object> owned(Connection c,Identity who,String id)throws SQLException{
  teacher(who);try(PreparedStatement p=c.prepareStatement("SELECT * FROM p3_builder_drafts WHERE id=? AND owner_id=? AND deleted=0")){p.setString(1,id);p.setString(2,who.userId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new Failure("NOT_FOUND","Không tìm thấy bản nháp của bạn.");return row(r,true);}}
 }
 public Map<String,Object> get(Identity who,String id)throws SQLException{try(Connection c=repo.open()){return owned(c,who,id);}}
 public void recordCheck(Identity who,String id,String hash,String kind,Map<String,Object> result)throws SQLException{try(Connection c=repo.open()){owned(c,who,id);try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_builder_checks(draft_id,content_hash,check_kind,result_json,created_at) VALUES(?,?,?,?,?)")){p.setString(1,id);p.setString(2,hash);p.setString(3,kind);p.setString(4,stringify(result));p.setLong(5,System.currentTimeMillis());p.executeUpdate();}}}
 public Map<String,Object> latestCheck(Identity who,String id,String kind)throws SQLException{try(Connection c=repo.open()){owned(c,who,id);try(PreparedStatement p=c.prepareStatement("SELECT content_hash,result_json,created_at FROM p3_builder_checks WHERE draft_id=? AND check_kind=? ORDER BY id DESC LIMIT 1")){p.setString(1,id);p.setString(2,kind);try(ResultSet r=p.executeQuery()){if(!r.next())return Map.of();Map<String,Object> out=new LinkedHashMap<>(map(parse(r.getString(2))));out.put("contentHash",r.getString(1));out.put("createdAt",r.getLong(3));return out;}}}}
 public List<Map<String,Object>> list(Identity who,int page)throws SQLException{teacher(who);List<Map<String,Object>> out=new ArrayList<>();try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_builder_drafts WHERE owner_id=? AND deleted=0 ORDER BY updated_at DESC,id LIMIT 20 OFFSET ?")){p.setString(1,who.userId());p.setInt(2,Math.max(0,Math.min(10000,page-1))*20);try(ResultSet r=p.executeQuery()){while(r.next())out.add(row(r,false));}}return out;}
 private static Object canonical(Object v,int depth){
  if(depth>12)throw new Failure("INVALID_REQUEST","Dữ liệu nháp lồng quá nhiều cấp.");
  if(v instanceof Map<?,?> m){if(m.size()>100)throw new Failure("INVALID_REQUEST","Quá nhiều trường trong một đối tượng.");Map<String,Object> out=new TreeMap<>();for(var e:m.entrySet()){String key=String.valueOf(e.getKey());if(key.length()>100)throw new Failure("INVALID_REQUEST","Tên trường quá dài.");out.put(key,canonical(e.getValue(),depth+1));}return out;}
  if(v instanceof List<?> a){if(a.size()>200)throw new Failure("INVALID_REQUEST","Danh sách nháp tối đa 200 phần tử.");return a.stream().map(x->canonical(x,depth+1)).toList();}
  if(v instanceof Number n&&!Double.isFinite(n.doubleValue()))throw new Failure("INVALID_REQUEST","Giá trị số vượt giới hạn.");
  if(v==null||v instanceof String||v instanceof Number||v instanceof Boolean)return v;
  throw new Failure("INVALID_REQUEST","Kiểu dữ liệu không hợp lệ.");
 }
 public static Map<String,Object> normalize(Map<String,Object> raw){
  for(String key:raw.keySet())if(!KEYS.contains(key))throw new Failure("INVALID_REQUEST","Trường nháp không hỗ trợ: "+key);
  Map<String,Object> data=new TreeMap<>(map(canonical(raw,0)));data.put("schemaVersion",1);data.put("bankScope",QuestionBankRepository.scope(text(data,"bankScope","PRACTICE")));
  if(data.containsKey("testGroups")){if(!(data.get("testGroups") instanceof List<?> groups)||groups.isEmpty()||groups.size()>20||new HashSet<>(groups).size()!=groups.size()||groups.stream().anyMatch(x->!(x instanceof String name)||name.isBlank()||name.length()>60))throw new Failure("INVALID_REQUEST","Cần 1–20 tên nhóm test riêng biệt, tối đa 60 ký tự.");}
  for(String key:List.of("title","code","learningOutcome","summary","difficulty","javaStructure","visibility","reference","incorrectSample")){Object v=data.get(key);if(v!=null&&!(v instanceof String))throw new Failure("INVALID_REQUEST",key+" phải là chuỗi.");}
  String code=text(data,"code","").strip();if(!code.isEmpty()&&!code.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw new Failure("INVALID_REQUEST","Mã bài chỉ gồm chữ, số, _ và -, tối đa 64 ký tự.");data.put("code",code);
  if(text(data,"title","").length()>200||text(data,"summary","").length()>4000||text(data,"learningOutcome","").length()>4000)throw new Failure("INVALID_REQUEST","Tên tối đa 200; mô tả/chuẩn đầu ra tối đa 4000 ký tự.");
  if(!Set.of("PROGRAM","METHOD","CLASS").contains(text(data,"javaStructure","PROGRAM")))throw new Failure("INVALID_REQUEST","Cấu trúc Java không hợp lệ.");
  if(!Set.of("PRIVATE","SHARED","PUBLIC").contains(text(data,"visibility","PRIVATE")))throw new Failure("INVALID_REQUEST","Quyền truy cập không hợp lệ.");
  for(String key:List.of("statement","template","grading"))if(data.containsKey(key)&&!(data.get(key) instanceof Map<?,?>))throw new Failure("INVALID_REQUEST",key+" phải là đối tượng.");
  Map<String,Object> statement=map(data.get("statement"));for(String key:List.of("description","inputFormat","outputFormat","constraints","sampleInput","sampleOutput","sampleExplanation","edgeCases")){Object value=statement.get(key);if(value!=null&&(!(value instanceof String)||((String)value).length()>12000))throw new Failure("INVALID_REQUEST","Nội dung "+key+" phải là chuỗi tối đa 12000 ký tự.");}
  if(statement.containsKey("attachments")&&!(statement.get("attachments") instanceof List<?>))throw new Failure("INVALID_REQUEST","File đính kèm phải là danh sách.");
  for(String key:List.of("weekIds","tests"))if(data.containsKey(key)&&!(data.get(key) instanceof List<?>))throw new Failure("INVALID_REQUEST",key+" phải là danh sách.");
  if(map(data.get("grading")).containsKey("structure"))map(data.get("grading")).put("structure",studyfix.assignment.grading.StructureRules.normalize(map(data.get("grading")).get("structure")));
  ProblemTemplate.validate(map(data.get("template")));
  if(stringify(data).getBytes(StandardCharsets.UTF_8).length>96*1024)throw new Failure("INVALID_REQUEST","Nháp JSON vượt 96 KiB; file lớn cần qua upload riêng.");return data;
 }
 public static String hash(Map<String,Object> data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stringify(canonical(data,0)).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 private void uniqueCode(Connection c,String code,String id,String base)throws SQLException{
  if(code.isEmpty())return;
  try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_question_bank WHERE display_code=? AND (?='' OR root_id<>(SELECT root_id FROM p3_question_bank WHERE exercise_id=?)) LIMIT 1")){p.setString(1,code);p.setString(2,base);p.setString(3,base);try(ResultSet r=p.executeQuery()){if(r.next())throw new Failure("DUPLICATE_CODE","Mã bài đã tồn tại trong ngân hàng.");}}
  try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_builder_drafts d WHERE code=? AND id<>? AND deleted=0 AND base_exercise_id IS NULL AND NOT EXISTS(SELECT 1 FROM p3_builder_publications pub WHERE pub.draft_id=d.id) LIMIT 1")){p.setString(1,code);p.setString(2,id);try(ResultSet r=p.executeQuery()){if(r.next())throw new Failure("DUPLICATE_CODE","Mã đang được dùng bởi bản nháp khác.");}}
 }
 private void weeks(Connection c,Identity who,Map<String,Object> data)throws SQLException{
  Set<String> seen=new HashSet<>();for(Object v:studyfix.assignment.protocol.AssignmentJson.list(data.get("weekIds"))){if(!(v instanceof String id)||!seen.add(id))throw new Failure("INVALID_REQUEST","Danh sách Week không hợp lệ hoặc trùng.");try(PreparedStatement p=c.prepareStatement("SELECT owner_id FROM p3_question_weeks WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||!who.userId().equals(r.getString(1)))throw new Failure("FORBIDDEN","Chỉ gắn đề vào Week do bạn quản lý.");}}}
 }
 private void history(Connection c,Identity who,String id,int revision,int step,Map<String,Object> data,long now)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_builder_history VALUES(?,?,?,?,?,?,?)")){p.setString(1,id);p.setInt(2,revision);p.setInt(3,step);p.setString(4,stringify(data));p.setString(5,hash(data));p.setString(6,who.userId());p.setLong(7,now);p.executeUpdate();}}
 public Map<String,Object> create(Identity who,String key,String base,int baseRevision,Map<String,Object> input)throws SQLException{
  teacher(who);if(!key.matches("[A-Za-z0-9_-]{8,100}"))throw new Failure("INVALID_REQUEST","Thiếu mã yêu cầu tạo nháp hợp lệ.");Map<String,Object> data=normalize(input);long now=System.currentTimeMillis();String id=UUID.randomUUID().toString();
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{
   try(PreparedStatement p=c.prepareStatement("SELECT id,creation_hash,base_exercise_id FROM p3_builder_drafts WHERE owner_id=? AND request_key=?")){p.setString(1,who.userId());p.setString(2,key);try(ResultSet r=p.executeQuery()){if(r.next()){if(!hash(data).equals(r.getString(2))||!base.equals(Objects.toString(r.getString(3),"")))throw new Failure("DUPLICATE_REQUEST","Mã yêu cầu đã dùng cho dữ liệu khác.");Map<String,Object> existing=owned(c,who,r.getString(1));tx.execute("COMMIT");return existing;}}}
   try(PreparedStatement quota=c.prepareStatement("SELECT COUNT(*) FROM p3_builder_drafts WHERE owner_id=? AND deleted=0")){quota.setString(1,who.userId());try(ResultSet count=quota.executeQuery()){count.next();if(count.getInt(1)>=100)throw new Failure("QUOTA_EXCEEDED","Tối đa 100 bản nháp đang mở.");}}
   uniqueCode(c,text(data,"code",""),id,base);weeks(c,who,data);
   try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_builder_drafts(id,owner_id,base_exercise_id,base_revision,code,revision,current_step,payload_json,content_hash,request_key,creation_hash,created_at,updated_at) VALUES(?,?,?,?,?,1,1,?,?,?,?,?,?)")){int i=1;p.setString(i++,id);p.setString(i++,who.userId());p.setString(i++,base.isEmpty()?null:base);p.setInt(i++,baseRevision);p.setString(i++,text(data,"code",""));p.setString(i++,stringify(data));p.setString(i++,hash(data));p.setString(i++,key);p.setString(i++,hash(data));p.setLong(i++,now);p.setLong(i,now);p.executeUpdate();}
   history(c,who,id,1,1,data,now);Map<String,Object> result=owned(c,who,id);tx.execute("COMMIT");return result;
  }catch(SQLException|RuntimeException ex){tx.execute("ROLLBACK");throw ex;}}
 }
 public Map<String,Object> save(Identity who,String id,int expected,int step,Map<String,Object> input)throws SQLException{
  teacher(who);if(step<1||step>7)throw new Failure("INVALID_REQUEST","Bước phải từ 1 đến 7.");Map<String,Object> data=normalize(input);long now=System.currentTimeMillis();
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{Map<String,Object> old=owned(c,who,id);if(integer(old,"currentStep",0)==step&&hash(data).equals(old.get("contentHash"))&&(integer(old,"revision",0)==expected||integer(old,"revision",0)==expected+1)){tx.execute("COMMIT");return old;}if(integer(old,"revision",0)!=expected)throw new Failure("CONFLICT","Bản nháp đã thay đổi ở cửa sổ khác. Hãy mở lại, không ghi đè.");boolean publishedDraft=false;try(PreparedStatement published=c.prepareStatement("SELECT 1 FROM p3_builder_publications WHERE draft_id=? LIMIT 1")){published.setString(1,id);try(ResultSet locked=published.executeQuery()){publishedDraft=locked.next();if(publishedDraft&&!hash(data).equals(old.get("contentHash")))throw new Failure("PUBLISHED_LOCKED","Published snapshot is immutable; create a new version.");}}String base=text(old,"baseExerciseId","");if(!base.isEmpty()&&!text(old,"code","").equals(text(data,"code","")))throw new Failure("INVALID_REQUEST","Giữ mã bài khi chỉnh sửa phiên bản có sẵn.");if(!publishedDraft){uniqueCode(c,text(data,"code",""),id,base);weeks(c,who,data);}
   int next=expected+1;try(PreparedStatement p=c.prepareStatement("UPDATE p3_builder_drafts SET code=?,revision=?,current_step=?,payload_json=?,content_hash=?,updated_at=? WHERE id=?")){p.setString(1,text(data,"code",""));p.setInt(2,next);p.setInt(3,step);p.setString(4,stringify(data));p.setString(5,hash(data));p.setLong(6,now);p.setString(7,id);p.executeUpdate();}history(c,who,id,next,step,data,now);Map<String,Object> result=owned(c,who,id);tx.execute("COMMIT");return result;
  }catch(SQLException|RuntimeException ex){tx.execute("ROLLBACK");throw ex;}}
 }
 public List<Map<String,Object>> history(Identity who,String id,int before)throws SQLException{List<Map<String,Object>> out=new ArrayList<>();try(Connection c=repo.open()){owned(c,who,id);try(PreparedStatement p=c.prepareStatement("SELECT revision,current_step,content_hash,created_at FROM p3_builder_history WHERE draft_id=? AND revision<? ORDER BY revision DESC LIMIT 20")){p.setString(1,id);p.setInt(2,before<1?Integer.MAX_VALUE:before);try(ResultSet r=p.executeQuery()){while(r.next())out.add(mapOf("revision",r.getInt(1),"currentStep",r.getInt(2),"contentHash",r.getString(3),"createdAt",r.getLong(4)));}}}return out;}
 public Map<String,Object> restore(Identity who,String id,int expected,int source)throws SQLException{
  Map<String,Object> data;int step;try(Connection c=repo.open()){owned(c,who,id);try(PreparedStatement p=c.prepareStatement("SELECT payload_json,current_step FROM p3_builder_history WHERE draft_id=? AND revision=?")){p.setString(1,id);p.setInt(2,source);try(ResultSet r=p.executeQuery()){if(!r.next())throw new Failure("NOT_FOUND","Không tìm thấy phiên bản nháp.");data=map(parse(r.getString(1)));step=r.getInt(2);}}}return save(who,id,expected,step,data);
 }
 public void delete(Identity who,String id,int expected)throws SQLException{try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{Map<String,Object> row=owned(c,who,id);if(integer(row,"revision",0)!=expected)throw new Failure("CONFLICT","Bản nháp đã thay đổi, hãy tải lại.");try(PreparedStatement p=c.prepareStatement("UPDATE p3_builder_drafts SET deleted=1,updated_at=? WHERE id=?")){p.setLong(1,System.currentTimeMillis());p.setString(2,id);p.executeUpdate();}tx.execute("COMMIT");}catch(SQLException|RuntimeException ex){tx.execute("ROLLBACK");throw ex;}}}
 public String suggestCode(Identity who)throws SQLException{teacher(who);try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{int n;try(ResultSet r=tx.executeQuery("SELECT next_value FROM p3_builder_sequences WHERE name='JAVA'")){r.next();n=r.getInt(1);}String code;while(true){code=String.format(Locale.ROOT,"JAVA%04d",n++);try{uniqueCode(c,code,"","");break;}catch(Failure e){if(!e.code.equals("DUPLICATE_CODE"))throw e;}}try(PreparedStatement p=c.prepareStatement("UPDATE p3_builder_sequences SET next_value=? WHERE name='JAVA'")){p.setInt(1,n);p.executeUpdate();}tx.execute("COMMIT");return code;}catch(SQLException|RuntimeException ex){tx.execute("ROLLBACK");throw ex;}}}
}
