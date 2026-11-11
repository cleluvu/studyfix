package studyfix.assignment.persistence;
import studyfix.Identity;
import java.sql.*;
import java.security.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Bounded authenticated binary storage; never served from the public static directory. */
public final class ProblemBuilderAssets {
 public record Asset(String id,String draftId,String name,String media,byte[] bytes){}
 private final AssignmentRepository repo;private final ProblemBuilderRepository drafts;
 public static final int MAX_BYTES=2*1024*1024;
 public ProblemBuilderAssets(AssignmentRepository repo,ProblemBuilderRepository drafts)throws SQLException{this.repo=repo;this.drafts=drafts;try(Connection c=repo.open();Statement s=c.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_builder_assets(id TEXT PRIMARY KEY,draft_id TEXT NOT NULL REFERENCES p3_builder_drafts(id),file_name TEXT NOT NULL,media_type TEXT NOT NULL,size_bytes INTEGER NOT NULL,sha256 TEXT NOT NULL,content BLOB NOT NULL,created_at INTEGER NOT NULL)");s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_builder_asset_draft ON p3_builder_assets(draft_id)");}}
 private static String media(String name,byte[] data){
  String lower=name.toLowerCase(Locale.ROOT);if(data.length<4||data.length>MAX_BYTES||name.length()>160||name.contains("/")||name.contains("\\")||name.indexOf(0)>=0||name.contains("\r")||name.contains("\n"))throw new ProblemBuilderRepository.Failure("INVALID_REQUEST","Tên hoặc kích thước file không hợp lệ (tối đa 2 MiB).");
  if(lower.endsWith(".png")&&data.length>=8&&Arrays.equals(Arrays.copyOf(data,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
  if((lower.endsWith(".jpg")||lower.endsWith(".jpeg"))&&(data[0]&255)==255&&(data[1]&255)==216&&(data[2]&255)==255)return "image/jpeg";
  if(lower.endsWith(".pdf")&&data.length>=5&&new String(data,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return "application/pdf";
  if(lower.endsWith(".zip")&&data[0]==80&&data[1]==75&&data[2]==3&&data[3]==4)return "application/zip";
  throw new ProblemBuilderRepository.Failure("INVALID_REQUEST","Chỉ nhận PNG, JPEG, PDF hoặc ZIP đúng định dạng.");
 }
 public Map<String,Object> upload(Identity who,String draft,String name,byte[] bytes)throws SQLException{
  String media=media(name,bytes),id=UUID.randomUUID().toString(),digest;try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  try(Connection c=repo.open();Statement tx=c.createStatement()){tx.execute("BEGIN IMMEDIATE");try{drafts.owned(c,who,draft);try(PreparedStatement p=c.prepareStatement("SELECT COALESCE(SUM(size_bytes),0),COUNT(*) FROM p3_builder_assets WHERE draft_id=?")){p.setString(1,draft);try(ResultSet r=p.executeQuery()){r.next();if(r.getLong(1)+bytes.length>8*1024*1024||r.getInt(2)>=100)throw new ProblemBuilderRepository.Failure("QUOTA_EXCEEDED","Tối đa 8 MiB / 100 file mỗi bản nháp.");}}
   try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_builder_assets VALUES(?,?,?,?,?,?,?,?)")){p.setString(1,id);p.setString(2,draft);p.setString(3,name);p.setString(4,media);p.setInt(5,bytes.length);p.setString(6,digest);p.setBytes(7,bytes);p.setLong(8,System.currentTimeMillis());p.executeUpdate();}tx.execute("COMMIT");return mapOf("id",id,"name",name,"mediaType",media,"size",bytes.length,"sha256",digest);
  }catch(SQLException|RuntimeException e){tx.execute("ROLLBACK");throw e;}}
 }
 public Asset get(Identity who,String id)throws SQLException {
  try(Connection c=repo.open();PreparedStatement p=c.prepareStatement("SELECT draft_id,file_name,media_type,content FROM p3_builder_assets WHERE id=?")){
   p.setString(1,id);try(ResultSet r=p.executeQuery()){
    if(!r.next())throw new ProblemBuilderRepository.Failure("NOT_FOUND","Asset not found.");
    String draft=r.getString(1);
    try{drafts.owned(c,who,draft);}catch(ProblemBuilderRepository.Failure denied){
     boolean allowed=false;
     try(PreparedStatement published=c.prepareStatement("SELECT 1 FROM p3_builder_public_assets a JOIN p3_question_bank b ON b.exercise_id=a.exercise_id WHERE a.asset_id=? AND b.publication='PUBLISHED' AND b.deleted=0 AND (b.bank_scope='PRACTICE' OR (?='TEACHER' AND b.owner_id=?))")){
      published.setString(1,id);published.setString(2,who.role().name());published.setString(3,who.userId());try(ResultSet access=published.executeQuery()){allowed=access.next();}
     }
     if(!allowed&&who.role()==Identity.Role.TEACHER){
      try(PreparedStatement review=c.prepareStatement("SELECT 1 FROM p3_builder_workflow w JOIN p3_builder_drafts d ON d.id=w.draft_id WHERE d.id=? AND w.reviewer_id=? AND w.state='IN_REVIEW' AND w.review_hash=d.content_hash AND d.deleted=0")){
       review.setString(1,draft);review.setString(2,who.userId());try(ResultSet access=review.executeQuery()){allowed=access.next();}
      }
     }
     if(!allowed)throw denied;
    }
    return new Asset(id,draft,r.getString(2),r.getString(3),r.getBytes(4));
   }
  }
 }
 public List<Map<String,Object>> list(Identity who,String draft)throws SQLException{List<Map<String,Object>> out=new ArrayList<>();try(Connection c=repo.open()){drafts.owned(c,who,draft);try(PreparedStatement p=c.prepareStatement("SELECT id,file_name,media_type,size_bytes,sha256 FROM p3_builder_assets WHERE draft_id=? ORDER BY created_at,id")){p.setString(1,draft);try(ResultSet r=p.executeQuery()){while(r.next())out.add(mapOf("id",r.getString(1),"name",r.getString(2),"mediaType",r.getString(3),"size",r.getLong(4),"sha256",r.getString(5)));}}}return out;}
}
