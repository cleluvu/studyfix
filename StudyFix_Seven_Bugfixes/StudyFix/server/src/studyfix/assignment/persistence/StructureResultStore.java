package studyfix.assignment.persistence;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;
/** Per-receipt report shared by practice, assignments, trials and contests. */
public final class StructureResultStore {
 private StructureResultStore(){}
 public static void ensure(Connection c)throws SQLException{try(var s=c.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_structure_results(submission_id TEXT PRIMARY KEY,result_json TEXT NOT NULL)");}}
 public static void write(Connection c,String id,List<Map<String,Object>> report)throws SQLException{try(var p=c.prepareStatement("INSERT INTO p3_structure_results VALUES(?,?) ON CONFLICT(submission_id) DO UPDATE SET result_json=excluded.result_json")){p.setString(1,id);p.setString(2,stringify(report));p.executeUpdate();}}
 public static List<Map<String,Object>> read(Connection c,String id)throws SQLException{try(var p=c.prepareStatement("SELECT result_json FROM p3_structure_results WHERE submission_id=?")){p.setString(1,id);try(var r=p.executeQuery()){return r.next()?list(parse(r.getString(1))).stream().map(v->map(v)).toList():List.of();}}}
}
