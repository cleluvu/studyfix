package studyfix.assignment.persistence;

import studyfix.Identity;
import java.sql.*;
import java.time.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Read-only learning aggregates. Scope identities are supplied by the authenticated management gateway. */
public final class TeacherDashboardRepository {
 private final AssignmentRepository repo;
 public TeacherDashboardRepository(AssignmentRepository repo){this.repo=repo;}
 private static List<Map<String,Object>> rows(Connection c,String sql,List<?> args)throws SQLException {
  try(var p=c.prepareStatement(sql)){for(int i=0;i<args.size();i++)p.setObject(i+1,args.get(i));try(var r=p.executeQuery()){var out=new ArrayList<Map<String,Object>>();var m=r.getMetaData();while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}
 }
 private static String values(List<String> ids,List<Object> args){if(ids.isEmpty())return "SELECT NULL WHERE 0";args.addAll(ids);return "VALUES "+String.join(",",Collections.nCopies(ids.size(),"(?)"));}
 public Map<String,Object> view(Identity who,List<String> roomIds,List<String> students,List<Map<String,Object>> members,Map<String,Object> request)throws SQLException {
  if(who.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ giảng viên được xem tổng quan.");
  int days=integer(request,"days",30);if(!Set.of(7,30,90).contains(days))throw new IllegalArgumentException("Khoảng thời gian phải là 7, 30 hoặc 90 ngày.");
  String selected=text(request,"classId","");if(!selected.isEmpty()&&!roomIds.contains(selected))throw new SecurityException("Lớp ngoài phạm vi quản lý.");
  List<String> rooms=selected.isEmpty()?roomIds:List.of(selected);
  List<String> people=selected.isEmpty()?students:members.stream().filter(m->selected.equals(m.get("classId"))).map(m->text(m,"userId","")).distinct().toList();
  ZoneId zone=ZoneId.of("Asia/Bangkok");LocalDate today=LocalDate.now(zone),first=today.minusDays(days-1);long since=first.atStartOfDay(zone).toInstant().toEpochMilli(),until=today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
  List<Object> scope=new ArrayList<>();String cte="WITH rooms(id) AS ("+values(rooms,scope)+"), people(id) AS ("+values(people,scope)+"), all_submissions AS ("+
   "SELECT s.id,s.user_id userId,s.username student,s.submitted_at submittedAt,s.status,CASE upper(s.verdict) WHEN 'ACCEPTED' THEN 'AC' WHEN 'PASS' THEN 'AC' ELSE upper(s.verdict) END verdict,s.score,a.exercise_id exerciseId,a.room_id classId,'Giao bài' source,'' contestId FROM p3_submissions s JOIN p3_assignments a ON a.id=s.assignment_id WHERE a.room_id IN rooms AND a.created_by_user_id=? AND NOT EXISTS(SELECT 1 FROM p3_contest_submissions cs WHERE cs.submission_id=s.id) "+
   "UNION ALL SELECT s.id,s.user_id,s.username,s.submitted_at,s.status,upper(s.verdict),s.score,s.exercise_id,s.room_id,'Kỳ thi',s.contest_id FROM p3_contest_problem_submissions s JOIN p3_contests t ON t.id=s.contest_id WHERE t.created_by_user_id=? AND (?='' OR s.room_id IN rooms OR EXISTS(SELECT 1 FROM p3_eo_candidates ec WHERE ec.contest_id=t.id AND ec.user_id=s.user_id AND ec.class_id IN rooms)) "+
   "UNION ALL SELECT s.id,s.user_id,COALESCE(s.user_id,''),s.submitted_at,s.status,CASE upper(s.verdict) WHEN 'ACCEPTED' THEN 'AC' WHEN 'PASS' THEN 'AC' ELSE upper(s.verdict) END,s.score,s.exercise_id,'','Luyện tập','' FROM p3_practice_submissions s WHERE s.is_run=0 AND s.user_id IN people), filtered AS (SELECT s.*,e.title FROM all_submissions s JOIN p3_exercises e ON e.id=s.exerciseId WHERE s.submittedAt>=? AND s.submittedAt<?";
  scope.add(who.userId());scope.add(who.userId());scope.add(selected);var eventArgs=new ArrayList<>(scope);String eventCte=cte.substring(0,cte.indexOf(", filtered AS"))+" ";scope.add(since);scope.add(until);
  String week=text(request,"weekId","");if(!week.isEmpty()){cte+=" AND EXISTS(SELECT 1 FROM p3_question_week_items wi JOIN p3_question_weeks w ON w.id=wi.week_id WHERE wi.exercise_id=s.exerciseId AND w.owner_id=? AND w.id=?)";scope.add(who.userId());scope.add(week);}
  cte+=") ";
  try(Connection c=repo.open()){
   c.setAutoCommit(false);
   try{
    var recentSubmissions=rows(c,eventCte+"SELECT s.id,s.userId,s.student,s.submittedAt,s.verdict,s.classId,e.title FROM all_submissions s JOIN p3_exercises e ON e.id=s.exerciseId ORDER BY s.submittedAt DESC LIMIT 30",eventArgs);
    var daily=rows(c,cte+"SELECT strftime('%Y-%m-%d',submittedAt/1000,'unixepoch','+7 hours') day,COUNT(*) submissions,SUM(CASE WHEN status='DONE' AND verdict='AC' THEN 1 ELSE 0 END) accepted FROM filtered WHERE source<>'Kỳ thi' GROUP BY day ORDER BY day",scope);
    var verdicts=rows(c,cte+"SELECT CASE WHEN status<>'DONE' THEN 'PENDING' WHEN verdict IN ('AC','WA','TLE','CE','RE') THEN verdict ELSE 'OTHER' END verdict,COUNT(*) count FROM filtered GROUP BY 1",scope);
    String verdict=text(request,"verdict","");if(!Set.of("","AC","WA","TLE","CE","RE","OTHER","PENDING").contains(verdict))throw new IllegalArgumentException("Kết quả không hợp lệ.");
    List<Object> args=new ArrayList<>(scope);String filter=verdict.isEmpty()?"":" WHERE "+(verdict.equals("PENDING")?"status<>'DONE'":verdict.equals("OTHER")?"status='DONE' AND verdict NOT IN ('AC','WA','TLE','CE','RE')":"status='DONE' AND verdict=?");if(!verdict.isEmpty()&&!Set.of("OTHER","PENDING").contains(verdict))args.add(verdict);
    long total=number(rows(c,cte+"SELECT COUNT(*) total FROM filtered"+filter,args).getFirst(),"total",0);int pages=Math.max(1,(int)((total+19)/20)),page=Math.max(1,Math.min(pages,integer(request,"page",1)));List<Object> paged=new ArrayList<>(args);paged.add((page-1)*20);
    var submissions=rows(c,cte+"SELECT id,userId,student,submittedAt,status,verdict,score,exerciseId,classId,source,contestId,title FROM filtered"+filter+" ORDER BY submittedAt DESC,id LIMIT 20 OFFSET ?",paged);
    var roomArgs=new ArrayList<Object>();String roomCte="WITH rooms(id) AS ("+values(rooms,roomArgs)+") ";roomArgs.add(who.userId());
    var assignments=rows(c,roomCte+"SELECT a.id,a.exercise_id exerciseId,a.room_id classId,a.created_at createdAt,a.deadline_at deadlineAt,a.status,e.title FROM p3_assignments a JOIN p3_exercises e ON e.id=a.exercise_id WHERE a.room_id IN rooms AND a.created_by_user_id=? ORDER BY a.created_at DESC LIMIT 100",roomArgs);
    long assignmentCount=number(rows(c,roomCte+"SELECT COUNT(*) total FROM p3_assignments WHERE room_id IN rooms AND created_by_user_id=?",roomArgs).getFirst(),"total",0);
    long examCount=number(rows(c,"SELECT COUNT(*) total FROM p3_exam_bank b WHERE owner_id=? AND EXISTS(SELECT 1 FROM p3_exam_versions v WHERE v.bank_id=b.id AND v.status<>'ARCHIVED')",List.of(who.userId())).getFirst(),"total",0);
    var finished=rows(c,"SELECT id,title,end_at endAt FROM p3_contests WHERE created_by_user_id=? AND end_at<=? AND status NOT IN ('CANCELLED') ORDER BY end_at DESC LIMIT 10",List.of(who.userId(),System.currentTimeMillis()));
    var upcoming=rows(c,"SELECT t.id,t.title,t.start_at startAt,t.end_at endAt,COALESCE(e.classes_json,'[]') classesJson FROM p3_contests t LEFT JOIN p3_eo_exams e ON e.contest_id=t.id WHERE t.created_by_user_id=? AND t.start_at>? AND t.status NOT IN ('CANCELLED','FINISHED','ENDED') AND COALESCE(e.state,'') NOT IN ('CANCELLED','POSTPONED') ORDER BY t.start_at LIMIT 3",List.of(who.userId(),System.currentTimeMillis()));
    var weeks=rows(c,"SELECT id,week_no weekNo,title FROM p3_question_weeks WHERE owner_id=? ORDER BY week_no DESC",List.of(who.userId()));
    var progressArgs=new ArrayList<Object>();String progressCte="WITH rooms(id) AS ("+values(rooms,progressArgs)+") ";progressArgs.add(who.userId());progressArgs.add(who.userId());
    var progress=rows(c,progressCte+"""
      ,pairs AS (
       SELECT DISTINCT w.id,w.week_no weekNo,w.title,a.room_id classId,
        COALESCE((SELECT root_id FROM p3_question_bank WHERE exercise_id=a.exercise_id),a.exercise_id) exerciseId,r.user_id userId
       FROM p3_question_weeks w JOIN p3_question_week_items wi ON wi.week_id=w.id
       JOIN p3_assignments a ON COALESCE((SELECT root_id FROM p3_question_bank WHERE exercise_id=a.exercise_id),a.exercise_id)=COALESCE((SELECT root_id FROM p3_question_bank WHERE exercise_id=wi.exercise_id),wi.exercise_id)
       JOIN p3_assignment_recipients r ON r.assignment_id=a.id
       WHERE a.room_id IN rooms AND a.created_by_user_id=? AND w.owner_id=?
      ) SELECT p.id,p.weekNo,p.title,COUNT(*) total,
       SUM(CASE WHEN EXISTS(SELECT 1 FROM p3_submissions s JOIN p3_assignments a ON a.id=s.assignment_id
        WHERE a.room_id=p.classId AND COALESCE((SELECT root_id FROM p3_question_bank WHERE exercise_id=a.exercise_id),a.exercise_id)=p.exerciseId
        AND s.user_id=p.userId AND s.status='DONE' AND upper(s.verdict) IN ('AC','ACCEPTED','PASS')) THEN 1 ELSE 0 END) completed
      FROM pairs p GROUP BY p.id ORDER BY p.weekNo DESC
      """,progressArgs);
    for(var row:progress)row.put("percent",number(row,"total",0)==0?0:Math.round(number(row,"completed",0)*1000.0/number(row,"total",0))/10.0);
    c.commit();return mapOf("days",days,"since",since,"serverTime",System.currentTimeMillis(),"daily",daily,"verdicts",verdicts,"submissions",submissions,"recentSubmissions",recentSubmissions,"total",total,"page",page,"totalPages",pages,"assignments",assignments,"assignmentCount",assignmentCount,"examCount",examCount,"upcoming",upcoming,"finished",finished,"weeks",weeks,"weekProgress",progress);
   }catch(SQLException|RuntimeException e){c.rollback();throw e;}
  }
 }
}
