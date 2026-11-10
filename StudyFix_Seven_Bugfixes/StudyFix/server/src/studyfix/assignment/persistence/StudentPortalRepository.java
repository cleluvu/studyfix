package studyfix.assignment.persistence;

import studyfix.Identity;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Read-only personal history and public practice standings; exam results stay behind publication ACLs. */
public final class StudentPortalRepository {
 private final AssignmentRepository repo;
 public StudentPortalRepository(AssignmentRepository repo){this.repo=repo;}
 public Map<String,Object> submission(Identity who,String id)throws SQLException {
  if(who.role()!=Identity.Role.STUDENT)throw new SecurityException("Chỉ học sinh được xem lịch sử cá nhân.");
  try(var c=repo.open()){
   var found=rows(c,"SELECT s.id,e.title,s.source_code sourceCode,s.submitted_at submittedAt,s.status,s.verdict,s.score FROM p3_practice_submissions s JOIN p3_exercises e ON e.id=s.exercise_id WHERE s.id=? AND s.user_id=? AND s.is_run=0 UNION ALL SELECT s.id,e.title,s.source_code,s.submitted_at,s.status,s.verdict,s.score FROM p3_submissions s JOIN p3_assignments a ON a.id=s.assignment_id JOIN p3_exercises e ON e.id=a.exercise_id WHERE s.id=? AND s.user_id=? AND NOT EXISTS(SELECT 1 FROM p3_contest_submissions cs WHERE cs.submission_id=s.id) AND NOT EXISTS(SELECT 1 FROM p3_contest_problems cp WHERE cp.assignment_id=a.id)",id,who.userId(),id,who.userId());
   if(found.isEmpty())throw new SecurityException("Không tìm thấy bài nộp của bạn.");
   return found.getFirst();
  }
 }
 private static List<Map<String,Object>> rows(Connection c,String sql,Object... args)throws SQLException {
  try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){var out=new ArrayList<Map<String,Object>>();var m=r.getMetaData();while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}
 }
 public Map<String,Object> view(Identity who,List<String> rooms,List<Map<String,Object>> people,Map<String,Object> request)throws SQLException {
  if(who.role()!=Identity.Role.STUDENT)throw new SecurityException("Chỉ học sinh được xem trang này.");
  int days=integer(request,"days",0);if(!Set.of(0,7,30,90).contains(days))throw new IllegalArgumentException("Khoảng thời gian không hợp lệ.");
  long since=days==0?0:System.currentTimeMillis()-days*86400000L;
  String selected=text(request,"classId","");
  try(var c=repo.open()){
   var history=rows(c,"SELECT s.id AS id,s.exercise_id exerciseId,e.title,s.submitted_at submittedAt,s.verdict,s.status,s.score,'practice' source,'' classId FROM p3_practice_submissions s JOIN p3_exercises e ON e.id=s.exercise_id WHERE s.user_id=? AND s.is_run=0 AND s.submitted_at>=? AND ?='' UNION ALL SELECT s.id,a.exercise_id,e.title,s.submitted_at,s.verdict,s.status,s.score,'assignment',a.room_id FROM p3_submissions s JOIN p3_assignments a ON a.id=s.assignment_id JOIN p3_exercises e ON e.id=a.exercise_id WHERE s.user_id=? AND s.submitted_at>=? AND (?='' OR a.room_id=?) AND NOT EXISTS(SELECT 1 FROM p3_contest_submissions cs WHERE cs.submission_id=s.id) ORDER BY submittedAt DESC,id",who.userId(),since,selected,who.userId(),since,selected,selected);
   var tasks=rows(c,"SELECT a.id,a.exercise_id exerciseId,COALESCE((SELECT root_id FROM p3_question_bank WHERE exercise_id=a.exercise_id),a.exercise_id) rootId,a.created_by_user_id ownerId,e.title,a.room_id classId,a.deadline_at deadlineAt,a.status,EXISTS(SELECT 1 FROM p3_submissions s WHERE s.assignment_id=a.id AND s.user_id=? AND s.status='DONE' AND upper(s.verdict) IN ('AC','ACCEPTED','PASS')) accepted FROM p3_assignments a JOIN p3_assignment_recipients r ON r.assignment_id=a.id JOIN p3_exercises e ON e.id=a.exercise_id WHERE r.user_id=? AND (?='' OR a.room_id=?) AND NOT EXISTS(SELECT 1 FROM p3_contest_problems cp WHERE cp.assignment_id=a.id) ORDER BY a.created_at DESC",who.userId(),who.userId(),selected,selected);
   tasks.removeIf(a->!rooms.contains(text(a,"classId","")));
   var weeks=rows(c,"SELECT w.id,w.week_no weekNo,w.title,COALESCE((SELECT root_id FROM p3_question_bank WHERE exercise_id=wi.exercise_id),wi.exercise_id) rootId,w.owner_id ownerId FROM p3_question_weeks w JOIN p3_question_week_items wi ON wi.week_id=w.id ORDER BY w.week_no,w.title");
   Map<String,Map<String,Object>> grouped=new LinkedHashMap<>();Set<String> weekRoots=new HashSet<>();
   for(var w:weeks){if(!weekRoots.add(w.get("id")+":"+w.get("rootId")))continue;var matching=tasks.stream().filter(a->Objects.equals(a.get("rootId"),w.get("rootId"))&&Objects.equals(a.get("ownerId"),w.get("ownerId"))).toList();if(matching.isEmpty())continue;var g=grouped.computeIfAbsent(text(w,"id",""),id->mapOf("id",id,"weekNo",w.get("weekNo"),"title",w.get("title"),"total",0,"completed",0,"assignments",new ArrayList<>()));g.put("total",number(g,"total",0)+1);if(matching.stream().anyMatch(a->number(a,"accepted",0)>0))g.put("completed",number(g,"completed",0)+1);list(g.get("assignments")).addAll(matching);}
   // Deduplicate revisions using the bank root, and count the best score once per problem.
   var results=rows(c,"SELECT s.user_id userId,COALESCE(b.root_id,s.exercise_id) root,MAX(CASE WHEN s.status='DONE' AND upper(s.verdict) IN ('AC','ACCEPTED','PASS') THEN 1 ELSE 0 END) ac,MAX(CASE WHEN s.status='DONE' THEN s.score ELSE 0 END) points FROM p3_practice_submissions s LEFT JOIN p3_question_bank b ON b.exercise_id=s.exercise_id WHERE s.is_run=0 AND s.submitted_at>=? GROUP BY s.user_id,COALESCE(b.root_id,s.exercise_id)",since);
   var board=new ArrayList<Map<String,Object>>();for(var person:people){var scores=results.stream().filter(s->Objects.equals(s.get("userId"),person.get("userId"))).toList();board.add(mapOf("userId",person.get("userId"),"displayName",person.get("displayName"),"accepted",scores.stream().mapToLong(s->number(s,"ac",0)).sum(),"points",scores.stream().mapToDouble(s->((Number)s.get("points")).doubleValue()).sum()));}
   board.sort(Comparator.<Map<String,Object>>comparingLong(s->-number(s,"accepted",0)).thenComparingDouble(s->-((Number)s.get("points")).doubleValue()).thenComparing(s->text(s,"displayName","")));
   int rank=0;Map<String,Object> previous=null;for(int i=0;i<board.size();i++){var row=board.get(i);if(previous==null||!Objects.equals(previous.get("accepted"),row.get("accepted"))||!Objects.equals(previous.get("points"),row.get("points")))rank=i+1;row.put("rank",rank);row.put("isMe",who.userId().equals(row.get("userId")));previous=row;}
   int pages=Math.max(1,(history.size()+19)/20),page=Math.max(1,Math.min(pages,integer(request,"page",1))),offset=(page-1)*20;
   var roots=rows(c,"SELECT DISTINCT COALESCE(b.root_id,s.exercise_id) root FROM p3_practice_submissions s LEFT JOIN p3_question_bank b ON b.exercise_id=s.exercise_id WHERE s.user_id=? AND s.is_run=0 AND s.status='DONE' AND upper(s.verdict) IN ('AC','ACCEPTED','PASS')",who.userId());
   return mapOf("accepted",roots.size(),"attempts",history.size(),"acceptedAttempts",history.stream().filter(s->"DONE".equals(s.get("status"))&&Set.of("AC","ACCEPTED","PASS").contains(text(s,"verdict",""))).count(),"assignments",tasks,"weeks",new ArrayList<>(grouped.values()),"history",history.subList(offset,Math.min(history.size(),offset+20)),"page",page,"pages",pages,"ranking",board.stream().limit(100).toList(),"myRank",board.stream().filter(s->Boolean.TRUE.equals(s.get("isMe"))).findFirst().orElse(Map.of()),"rankTotal",board.size());
  }
 }
}
