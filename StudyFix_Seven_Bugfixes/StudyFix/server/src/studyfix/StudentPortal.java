package studyfix;

import java.util.*;
import java.sql.*;
import studyfix.assignment.transport.AssignmentTcpServer;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Student scope comes from the authenticated identity and durable enrollment. */
final class StudentPortal {
 private final Server core;
 private final AssignmentTcpServer assignments;
 StudentPortal(Server core,AssignmentTcpServer assignments){this.core=core;this.assignments=assignments;}
 static void requireEntry(Server core,Identity who,Room room)throws SQLException {
  var db=core.store().management();
  if(db==null)return;
  boolean enrolled=db.transaction(c->{try(var p=c.prepareStatement("SELECT 1 FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.user_id=? AND m.class_id=? AND m.membership_status='ENROLLED' AND k.active=1 AND k.closed=0")){p.setString(1,who.userId());p.setString(2,room.getRoomId());try(var r=p.executeQuery()){return r.next();}}});
  if(!enrolled)throw new SecurityException("Bạn chưa được thêm vào lớp hoặc lớp đã đóng.");
  String phase=new ClassroomRepository(core.store()).session(room.getRoomId()).phase();
  if(Set.of("WAITING","ENDED").contains(phase)||room.memberDetails().stream().noneMatch(m->m.online()&&"TEACHER".equals(m.role())))throw new IllegalStateException("Phiên học chưa mở, đã kết thúc hoặc giảng viên đã rời phòng.");
 }
 Map<String,Object> view(Identity who,Map<String,Object> request)throws SQLException {
  if(who.role()!=Identity.Role.STUDENT)throw new SecurityException("Chỉ học sinh được xem trang này.");
  var db=core.store().management();if(db==null)throw new IllegalStateException("Chưa cấu hình dữ liệu học sinh.");
  var classes=db.transaction(c->{var out=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement("SELECT k.class_id classId,k.room_code roomCode,k.name,k.description,k.closed,u.display_name teacher,(SELECT COUNT(*) FROM class_members x WHERE x.class_id=k.class_id AND x.membership_status='ENROLLED') memberCount FROM class_members m JOIN classes k ON k.class_id=m.class_id JOIN users u ON u.user_id=k.owner_id WHERE m.user_id=? AND m.membership_status='ENROLLED' AND k.active=1 ORDER BY k.room_code")){p.setString(1,who.userId());try(var r=p.executeQuery()){while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}}}return out;});
  for(var row:classes){String id=text(row,"classId","");var session=new ClassroomRepository(core.store()).session(id);Room room=core.rooms.get(id);boolean online=room!=null&&room.memberDetails().stream().anyMatch(m->m.online()&&"TEACHER".equals(m.role()));row.put("phase",session.phase());row.put("canJoin",number(row,"closed",0)==0&&online&&!Set.of("WAITING","ENDED").contains(session.phase()));}
  String selected=text(request,"classId","");if(!selected.isEmpty()&&classes.stream().noneMatch(c->selected.equals(c.get("classId"))))throw new SecurityException("Bạn không thuộc lớp học này.");
  var people=db.transaction(c->{var out=new ArrayList<Map<String,Object>>();String sql=selected.isEmpty()?"SELECT user_id userId,display_name displayName FROM users WHERE role='STUDENT' AND active=1":"SELECT u.user_id userId,u.display_name displayName FROM users u JOIN class_members m ON m.user_id=u.user_id WHERE u.role='STUDENT' AND u.active=1 AND m.class_id=? AND m.membership_status='ENROLLED'";try(var p=c.prepareStatement(sql)){if(!selected.isEmpty())p.setString(1,selected);try(var r=p.executeQuery()){while(r.next())out.add(mapOf("userId",r.getString(1),"displayName",r.getString(2)));}}return out;});
  var learning=assignments.studentPortal(who,selected.isEmpty()?classes.stream().map(c->text(c,"classId","")).toList():List.of(selected),people,request);
  return mapOf("classes",classes,"learning",learning,"serverTime",System.currentTimeMillis());
 }
}
