package studyfix.assignment.persistence;

import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Collapse the former version history into one editable definition per exam code. */
final class ExamBankConsolidation {
 private ExamBankConsolidation() {}
 static void freezeContestMetadata(Connection c,String id)throws SQLException {
  try(PreparedStatement p=c.prepareStatement("UPDATE p3_exam_contests SET config_json=json_set(config_json,'$.examTitle',COALESCE(json_extract(config_json,'$.examTitle'),(SELECT json_extract(document_json,'$.title') FROM p3_exam_versions WHERE id=?)),'$.examCode',COALESCE(json_extract(config_json,'$.examCode'),(SELECT b.code FROM p3_exam_versions v JOIN p3_exam_bank b ON b.id=v.bank_id WHERE v.id=?)),'$.scoringMode',COALESCE(json_extract(config_json,'$.scoringMode'),(SELECT json_extract(document_json,'$.scoringMode') FROM p3_exam_versions WHERE id=?),'WEIGHTED')) WHERE version_id=?")) {
   for(int i=1;i<=4;i++)p.setString(i,id);p.executeUpdate();
  }
 }
 static void run(AssignmentRepository repo)throws SQLException {
  try(Connection c=repo.open();Statement tx=c.createStatement()) {
   tx.execute("BEGIN IMMEDIATE");try {
    Map<String,String> replacements=new LinkedHashMap<>();
    try(ResultSet r=tx.executeQuery("SELECT old.id,(SELECT current.id FROM p3_exam_versions current WHERE current.bank_id=old.bank_id ORDER BY current.version_no DESC LIMIT 1) AS current_id FROM p3_exam_versions old WHERE old.version_no<(SELECT MAX(v.version_no) FROM p3_exam_versions v WHERE v.bank_id=old.bank_id)")) {while(r.next())replacements.put(r.getString(1),r.getString(2));}
    for(var entry:replacements.entrySet()) {
     String old=entry.getKey(),current=entry.getValue();freezeContestMetadata(c,old);
     update(c,"UPDATE p3_exam_contests SET version_id=? WHERE version_id=?",current,old);
     update(c,"DELETE FROM p3_exam_history WHERE version_id=?",old);
     update(c,"DELETE FROM p3_exam_version_items WHERE version_id=?",old);
     update(c,"DELETE FROM p3_exam_versions WHERE id=?",old);
    }
    if(!replacements.isEmpty()) {
     rewrite(c,"p3_eo_compositions","version_id","sources_json",replacements);
     try(ResultSet r=tx.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='p3_eo_drafts'")){if(r.next())rewrite(c,"p3_eo_drafts","id","document_json",replacements);}
    }
    tx.executeUpdate("UPDATE p3_exam_versions SET version_no=1 WHERE version_no<>1");
    tx.execute("COMMIT");
   }catch(SQLException|RuntimeException e){tx.execute("ROLLBACK");throw e;}
  }
 }
 private static void rewrite(Connection c,String table,String key,String column,Map<String,String> replacements)throws SQLException {
  Map<String,String> updates=new LinkedHashMap<>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT "+key+","+column+" FROM "+table)){while(r.next()){Object value=parse(r.getString(2));replace(value,replacements);updates.put(r.getString(1),stringify(value));}}
  for(var e:updates.entrySet())update(c,"UPDATE "+table+" SET "+column+"=? WHERE "+key+"=?",e.getValue(),e.getKey());
 }
 @SuppressWarnings("unchecked") private static void replace(Object value,Map<String,String> replacements) {
  if(value instanceof Map<?,?> raw){Map<String,Object> m=(Map<String,Object>)raw;for(var e:m.entrySet()){if(Set.of("versionId","examVersionId").contains(e.getKey())&&e.getValue() instanceof String id&&replacements.containsKey(id))e.setValue(replacements.get(id));else replace(e.getValue(),replacements);}}
  else if(value instanceof List<?> values){
   for(Object child:values)replace(child,replacements);
   // Older drafts could select two versions of the same exam; merge their quotas.
   Map<String,Map<String,Object>> seen=new LinkedHashMap<>();List<Object> merged=new ArrayList<>();boolean duplicate=false;
   for(Object child:values){if(child instanceof Map<?,?> raw&&raw.get("versionId") instanceof String id&&raw.get("count") instanceof Number){Map<String,Object> row=(Map<String,Object>)raw,prior=seen.putIfAbsent(id,row);if(prior!=null){duplicate=true;prior.put("count",integer(prior,"count",0)+integer(row,"count",0));if(row.containsKey("exerciseIds")){Set<Object> ids=new LinkedHashSet<>(list(prior.get("exerciseIds")));ids.addAll(list(row.get("exerciseIds")));prior.put("exerciseIds",new ArrayList<>(ids));}continue;}}merged.add(child);}
   if(duplicate){List<Object> mutable=(List<Object>)values;mutable.clear();mutable.addAll(merged);}
  }
 }
 private static void update(Connection c,String sql,Object... values)throws SQLException {try(PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();}}
}
