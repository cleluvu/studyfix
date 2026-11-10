package studyfix;

import java.sql.*;
import java.util.*;
import studyfix.assignment.transport.AssignmentTcpServer;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Combines already authorized class information and learning aggregates. No client-supplied owner IDs. */
final class TeacherDashboard {
 private final ManagementDatabase db;
 private final AssignmentTcpServer assignments;
 TeacherDashboard(ManagementDatabase db,AssignmentTcpServer assignments){this.db=db;this.assignments=assignments;}
 private Map<String,Object> preferences(String teacher)throws SQLException {
  return db.transaction(c->{try(var p=c.prepareStatement("SELECT preferences_json,read_at FROM teacher_dashboard_preferences WHERE user_id=?")){p.setString(1,teacher);try(var r=p.executeQuery()){return r.next()?mapOf("settings",map(parse(r.getString(1))),"readAt",r.getLong(2)):mapOf("settings",mapOf("days",30,"compact",false,"notifications",true),"readAt",0);}}});
 }
 Map<String,Object> update(Identity who,Map<String,Object> body)throws SQLException {
  AdminManagement.requireAdmin(who);if(db==null)throw new IllegalStateException("SQLite chưa sẵn sàng.");
  var previous=preferences(who.userId());Map<String,Object> settings=new LinkedHashMap<>(map(previous.get("settings")));long read=number(previous,"readAt",0);
  if("READ".equals(text(body,"action","")))read=Math.max(read,Math.min(System.currentTimeMillis(),number(body,"through",0)));
  else if("SETTINGS".equals(text(body,"action",""))){int days=integer(body,"days",30);if(!Set.of(7,30,90).contains(days))throw new IllegalArgumentException("Khoảng thời gian không hợp lệ.");settings=mapOf("days",days,"compact",Boolean.TRUE.equals(body.get("compact")),"notifications",!Boolean.FALSE.equals(body.get("notifications")));}
  else throw new IllegalArgumentException("Thao tác không hợp lệ.");
  final long cutoff=read;final String json=stringify(settings);
  db.transaction(c->{try(var p=c.prepareStatement("INSERT INTO teacher_dashboard_preferences(user_id,preferences_json,read_at) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET preferences_json=excluded.preferences_json,read_at=MAX(teacher_dashboard_preferences.read_at,excluded.read_at)")){p.setString(1,who.userId());p.setString(2,json);p.setLong(3,cutoff);p.executeUpdate();}return null;});return preferences(who.userId());
 }
 Map<String,Object> view(Identity who,Map<String,Object> base,Map<String,Object> request)throws SQLException {
  AdminManagement.requireAdmin(who);if(db==null)throw new IllegalStateException("SQLite chưa sẵn sàng.");
  var classes=list(base.get("classes")).stream().map(v->map(v)).toList();var roomIds=classes.stream().map(r->text(r,"classId","")).toList();
  var members=db.transaction(c->{var out=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement("SELECT m.class_id,m.user_id,m.enrolled_at,u.display_name,u.student_code FROM class_members m JOIN classes k ON k.class_id=m.class_id JOIN users u ON u.user_id=m.user_id WHERE k.owner_id=? AND k.active=1 AND m.membership_status='ENROLLED' AND u.role='STUDENT'")){p.setString(1,who.userId());try(var r=p.executeQuery()){while(r.next())out.add(mapOf("classId",r.getString(1),"userId",r.getString(2),"createdAt",r.getLong(3),"name",r.getString(4),"code",r.getString(5)));}}return out;});
  var prefs=preferences(who.userId());var req=new LinkedHashMap<>(request);if(!req.containsKey("days"))req.put("days",integer(map(prefs.get("settings")),"days",30));
  String selected=text(req,"classId","");if(!selected.isEmpty()&&!roomIds.contains(selected))throw new SecurityException("Lớp ngoài phạm vi quản lý.");
  var students=members.stream().map(m->text(m,"userId","")).distinct().toList();
  var learning=assignments.teacherDashboard(who,roomIds,students,members,req);
  Map<String,String> names=new HashMap<>();for(var m:members)names.put(text(m,"userId",""),text(m,"code",text(m,"name","")));
  for(String key:List.of("submissions","recentSubmissions"))for(Object raw:list(learning.get(key))){var row=map(raw);String user=text(row,"userId","");if(names.containsKey(user))row.put("student",names.get(user));}

  List<Map<String,Object>> activity=new ArrayList<>();
  for(Object raw:list(base.get("recentActivity"))){var row=map(raw);activity.add(mapOf("id","lesson:"+text(row,"roomCode","")+":"+number(row,"createdAt",0)+":"+text(row,"id",""),"kind","lesson","title",text(row,"message",text(row,"kind","Hoạt động phiên học")),"context",text(row,"roomCode",""),"at",number(row,"createdAt",0),"classId",classes.stream().filter(k->text(k,"roomCode","").equals(text(row,"roomCode",""))).map(k->text(k,"classId","")).findFirst().orElse("")));}
  for(var m:members)activity.add(mapOf("id","member:"+m.get("classId")+":"+m.get("userId"),"kind","member","title",text(m,"name","")+" đã tham gia lớp","context",m.get("classId"),"at",m.get("createdAt"),"classId",m.get("classId")));
  for(Object raw:list(learning.get("recentSubmissions"))){var s=map(raw);activity.add(mapOf("id","submission:"+s.get("id"),"kind","submission","title",text(s,"student","")+" đã nộp bài "+text(s,"title",""),"context",text(s,"verdict",""),"at",s.get("submittedAt"),"classId",s.get("classId")));}
  for(Object raw:list(learning.get("assignments"))){var a=map(raw);activity.add(mapOf("id","assignment:"+a.get("id"),"kind","assignment","title","Đã giao bài “"+text(a,"title","")+"”","context",a.get("classId"),"at",a.get("createdAt"),"classId",a.get("classId")));}
  for(Object raw:list(learning.get("finished"))){var e=map(raw);activity.add(mapOf("id","exam:"+e.get("id"),"kind","exam","title","Kỳ thi “"+text(e,"title","")+"” đã kết thúc","context","","at",e.get("endAt"),"classId","","contestId",e.get("id")));}
  activity.removeIf(a->number(a,"at",0)<=0);activity.sort(Comparator.comparingLong(a->-number(a,"at",0)));
  long readAt=number(prefs,"readAt",0);
  var stats=mapOf("classes",classes.stream().filter(r->Boolean.TRUE.equals(r.get("active"))).count(),"students",students.size(),"assignments",learning.get("assignmentCount"),"exams",learning.get("examCount"),"liveLessons",map(base.get("stats")).getOrDefault("liveLessons",0));
  return mapOf("stats",stats,"classes",classes,"learning",learning,"activity",activity.stream().limit(50).toList(),"joinRequests",base.getOrDefault("joinRequests",List.of()),"settings",prefs.get("settings"),"readAt",readAt,"unread",Boolean.FALSE.equals(map(prefs.get("settings")).get("notifications"))?0:activity.stream().limit(50).filter(a->number(a,"at",0)>readAt).count(),"serverTime",System.currentTimeMillis());
 }
}
