package studyfix;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Student mutations are atomic in the authoritative management SQLite file. */
final class StudentManagement {
 private final Server core;private final ManagementDatabase db;private final Consumer<String> revoke;
 StudentManagement(Server core,ManagementDatabase db,Consumer<String> revoke){this.core=core;this.db=db;this.revoke=revoke;}
 private static void execute(Connection c,String sql,Object... args)throws SQLException{try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();}}
 private static String status(Map<String,Object> body){String value=text(body,"accountStatus","ACTIVE");if(!Set.of("ACTIVE","LOCKED","DISABLED").contains(value))throw new IllegalArgumentException("Trạng thái tài khoản không hợp lệ.");return value;}
 private static String email(Map<String,Object> body){String value=text(body,"email","").strip();if(value.length()>254||(!value.isEmpty()&&!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw new IllegalArgumentException("Email không hợp lệ.");return value;}
 private static void openClass(Connection c,String teacher,String id)throws SQLException{TeacherRepository.requireOwner(c,teacher,id);try(var p=c.prepareStatement("SELECT 1 FROM classes WHERE class_id=? AND active=1 AND closed=0")){p.setString(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Lớp đã đóng hoặc không còn hoạt động.");}}}
 private static void visible(Connection c,String teacher,String id)throws SQLException{try(var p=c.prepareStatement("SELECT 1 FROM users u WHERE u.user_id=? AND u.role='STUDENT' AND (u.created_by=? OR EXISTS(SELECT 1 FROM class_members m JOIN classes k ON k.class_id=m.class_id WHERE m.user_id=u.user_id AND m.membership_status='ENROLLED' AND k.owner_id=? AND k.active=1))")){p.setString(1,id);p.setString(2,teacher);p.setString(3,teacher);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Sinh viên ngoài phạm vi quản lý.");}}}
 private static void enroll(Connection c,String room,String user)throws SQLException{execute(c,"INSERT INTO class_members(class_id,user_id,membership_status,enrolled_at) VALUES(?,?,'ENROLLED',?) ON CONFLICT(class_id,user_id) DO UPDATE SET membership_status='ENROLLED',enrolled_at=CASE WHEN class_members.membership_status='ENROLLED' THEN class_members.enrolled_at ELSE excluded.enrolled_at END",room,user,System.currentTimeMillis());}
 private static void remove(Connection c,String room,String user)throws SQLException{execute(c,"UPDATE class_members SET membership_status='REMOVED' WHERE class_id=? AND user_id=?",room,user);execute(c,"UPDATE class_admissions SET status='CANCELLED' WHERE class_id=? AND user_id=? AND status IN ('PENDING','APPROVED','ENTERED')",room,user);}
 private static List<String> ids(Map<String,Object> body){var ids=list(body.get("userIds")).stream().map(String::valueOf).toList();if(ids.isEmpty()||ids.size()>100||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Chọn từ 1–100 sinh viên khác nhau.");return ids;}
 private ManagementDatabase.Account account(String id)throws SQLException{return db.transaction(c->{try(var p=c.prepareStatement("SELECT username,display_name,password_hash,student_code,active FROM users WHERE user_id=? AND role='STUDENT'")){p.setString(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy sinh viên.");return new ManagementDatabase.Account(new Identity(id,r.getString(1),r.getString(2),Identity.Role.STUDENT),r.getString(3),r.getString(4),r.getBoolean(5));}}});}
 private List<Map<String,Object>> directory(String teacher)throws SQLException{return list(db.dashboard(teacher,"").get("users")).stream().map(v->map(v)).toList();}
 private static String key(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT);}
 Map<String,Object> handle(Identity who,Map<String,Object> body)throws SQLException{
  AdminManagement.requireAdmin(who);String teacher=who.userId(),action=text(body,"action","");
  if(action.equals("STUDENT_CREATE")||action.equals("STUDENT_IMPORT")){
   List<Map<String,Object>> rows=action.equals("STUDENT_CREATE")?List.of(body):list(body.get("students")).stream().map(v->map(v)).toList();
   if(rows.isEmpty()||rows.size()>100)throw new IllegalArgumentException("Mỗi lần tạo từ 1–100 sinh viên.");
   List<ManagementDatabase.Account> accounts=new ArrayList<>();List<String> emails=new ArrayList<>(),statuses=new ArrayList<>();
   for(var row:rows){if(!"STUDENT".equals(text(row,"role","STUDENT")))throw new IllegalArgumentException("Chỉ tạo tài khoản sinh viên.");String code=text(row,"studentCode",text(row,"username","")).strip();if(code.isEmpty())throw new IllegalArgumentException("MSSV bắt buộc.");char[] secret=text(row,"password","").toCharArray();try{accounts.add(ManagementDatabase.prepareAccount(text(row,"username","").strip(),code,text(row,"displayName","").strip(),"STUDENT",secret));}finally{Arrays.fill(secret,'\0');}emails.add(email(row));statuses.add(status(row));}
   db.transaction(c->{for(int i=0;i<accounts.size();i++){var a=accounts.get(i);ManagementDatabase.requireAvailable(c,a);String room=text(rows.get(i),"classId","");String code=text(rows.get(i),"classCode","").strip();if(room.isEmpty()&&code.isEmpty())room=text(body,"classId","");if(room.isEmpty()&&!code.isEmpty())try(var p=c.prepareStatement("SELECT class_id FROM classes WHERE owner_id=? AND room_code=? AND active=1")){p.setString(1,teacher);p.setString(2,code);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy lớp của bạn: "+code);room=r.getString(1);}}
    if(!room.isEmpty())openClass(c,teacher,room);ManagementDatabase.insertAccount(c,a,teacher);execute(c,"UPDATE users SET email=?,account_status=?,active=? WHERE user_id=?",emails.get(i),statuses.get(i),statuses.get(i).equals("ACTIVE"),a.identity().userId());if(!room.isEmpty())enroll(c,room,a.identity().userId());
   }return null;});
   for(var a:accounts)core.store().mirrorAccount(account(a.identity().userId()));return mapOf("createdCount",accounts.size(),"userIds",accounts.stream().map(a->a.identity().userId()).toList());
  }
  if(action.equals("STUDENT_UPDATE")){
   String id=text(body,"userId",""),name=text(body,"displayName","").strip(),username=text(body,"username","").strip(),mail=email(body),status=status(body),target=text(body,"classId","__KEEP__"),source=text(body,"fromClassId","");
   if(!username.matches("[A-Za-z0-9_.-]{1,64}")||name.isEmpty()||name.length()>200)throw new IllegalArgumentException("Tên đăng nhập hoặc họ tên không hợp lệ.");
   db.transaction(c->{TeacherRepository.requireManagedStudent(c,teacher,id);try(var p=c.prepareStatement("SELECT student_code FROM users WHERE user_id=?")){p.setString(1,id);try(var r=p.executeQuery()){r.next();if(body.containsKey("studentCode")&&!text(body,"studentCode","").equals(r.getString(1)))throw new IllegalArgumentException("MSSV được giữ cố định.");}}
    try(var p=c.prepareStatement("SELECT 1 FROM users WHERE user_id<>? AND (username=? OR student_code=?)")){p.setString(1,id);p.setString(2,username);p.setString(3,username);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("Tên đăng nhập đã được sử dụng.");}}
    if(!target.equals("__KEEP__")&&!target.isEmpty()){openClass(c,teacher,target);if(!source.isEmpty()&&!source.equals(target)){TeacherRepository.requireOwner(c,teacher,source);remove(c,source,id);}enroll(c,target,id);}
    execute(c,"UPDATE users SET username=?,display_name=?,email=?,account_status=?,active=? WHERE user_id=?",username,name,mail,status,status.equals("ACTIVE"),id);return null;});
   revoke.accept(id);core.store().mirrorAccount(account(id));return mapOf("updated",true);
  }
  if(action.equals("STUDENT_STATUS")){
   var ids=ids(body);String status=status(body);db.transaction(c->{for(String id:ids)TeacherRepository.requireManagedStudent(c,teacher,id);for(String id:ids)execute(c,"UPDATE users SET account_status=?,active=? WHERE user_id=?",status,status.equals("ACTIVE"),id);return null;});
   if(!status.equals("ACTIVE"))for(String id:ids)revoke.accept(id);return mapOf("updatedCount",ids.size());
  }
  if(action.equals("STUDENT_ASSIGN")){
   var ids=ids(body);String target=text(body,"classId",""),source=text(body,"fromClassId","");boolean move=Boolean.TRUE.equals(body.get("move"));if(move&&(source.isEmpty()||source.equals(target)))throw new IllegalArgumentException("Chọn lớp nguồn và lớp đích khác nhau.");
   db.transaction(c->{openClass(c,teacher,target);if(move)TeacherRepository.requireOwner(c,teacher,source);for(String id:ids){visible(c,teacher,id);if(move)try(var p=c.prepareStatement("SELECT 1 FROM class_members WHERE class_id=? AND user_id=? AND membership_status='ENROLLED'")){p.setString(1,source);p.setString(2,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Có sinh viên không thuộc lớp nguồn; chưa chuyển ai.");}}}for(String id:ids){if(move)remove(c,source,id);enroll(c,target,id);}return null;});
   if(move)for(var client:core.clients.values())if(client.identity()!=null&&ids.contains(client.identity().userId()))client.leaveClassIfCurrent(source);return mapOf("updatedCount",ids.size());
  }
  var all=directory(teacher);
  if(action.equals("STUDENT_DETAIL")){String id=text(body,"userId","");return mapOf("student",all.stream().filter(u->id.equals(u.get("userId"))).findFirst().orElseThrow(()->new SecurityException("Sinh viên ngoài phạm vi quản lý.")));}
  if(action.equals("STUDENT_EXPORT")){
   String room=text(body,"classId",""),needle=key(text(body,"query","")),filter=text(body,"filterStatus","");if(!room.isEmpty())db.transaction(c->{TeacherRepository.requireOwner(c,teacher,room);return null;});
   var selected=list(body.get("userIds")).stream().map(String::valueOf).toList();var filtered=all.stream().filter(u->(selected.isEmpty()||selected.contains(u.get("userId")))&&(room.isEmpty()||list(u.get("classes")).stream().anyMatch(v->room.equals(map(v).get("id"))))&&(filter.isEmpty()||filter.equals(u.get("accountStatus")))&&matchesDates(u,body)&&(!Boolean.TRUE.equals(body.get("unassigned"))||list(u.get("classes")).isEmpty())&&key(text(u,"studentCode","")+" "+text(u,"username","")+" "+text(u,"displayName","")).contains(needle)).toList();
   List<List<String>> sheet=new ArrayList<>();sheet.add(List.of("MSSV","Họ và tên","Tên đăng nhập","Email","Lớp","Ngày tạo","Trạng thái"));for(var u:filtered)sheet.add(List.of(text(u,"studentCode",""),text(u,"displayName",""),text(u,"username",""),text(u,"email",""),text(u,"classNames",""),java.time.Instant.ofEpochMilli(number(u,"createdAt",0)).atZone(java.time.ZoneId.of("Asia/Bangkok")).toLocalDate().toString(),switch(text(u,"accountStatus","ACTIVE")){case "LOCKED"->"Tạm khóa";case "DISABLED"->"Đã vô hiệu hóa";default->"Đang hoạt động";}));
   boolean xlsx=text(body,"format","CSV").equals("XLSX");byte[] bytes=xlsx?StudentSpreadsheet.write(sheet):csv(sheet);return mapOf("base64",Base64.getEncoder().encodeToString(bytes),"filename","sinh_vien."+(xlsx?"xlsx":"csv"),"mediaType",xlsx?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/csv;charset=utf-8","count",filtered.size());
  }
  throw new IllegalArgumentException("Thao tác sinh viên không hợp lệ.");
 }
 private static boolean matchesDates(Map<String,Object> row,Map<String,Object> filter){String day=java.time.Instant.ofEpochMilli(number(row,"createdAt",0)).atZone(java.time.ZoneId.of("Asia/Bangkok")).toLocalDate().toString(),from=text(filter,"createdFrom",""),to=text(filter,"createdTo","");return (from.isEmpty()||day.compareTo(from)>=0)&&(to.isEmpty()||day.compareTo(to)<=0);}
 private static byte[] csv(List<List<String>> rows){StringBuilder out=new StringBuilder("\ufeff");for(var row:rows){out.append(String.join(",",row.stream().map(s->{String v=s;if(!v.stripLeading().isEmpty()&&"=+@-".indexOf(v.stripLeading().charAt(0))>=0)v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}).toList())).append("\r\n");}return out.toString().getBytes(StandardCharsets.UTF_8);}
}
