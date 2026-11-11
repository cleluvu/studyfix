package studyfix.assignment.persistence;

import studyfix.Identity;
import studyfix.assignment.model.ContestSubmissionRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import java.security.SecureRandom;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Durable exam organization. All mutations commit in the learning SQLite file. */
public final class ExamOrganizationRepository {
    private final AssignmentRepository repo;
    @FunctionalInterface public interface Work<T>{T run(Connection c)throws SQLException;}
    public ExamOrganizationRepository(AssignmentRepository repo)throws Exception {
        this.repo=repo;
        String schema;
        try(var in=getClass().getResourceAsStream("/studyfix/db/sqlite-exam-organization.sql")){
            schema=in==null?Files.readString(Path.of("src/studyfix/db/sqlite-exam-organization.sql")):new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
        final String sql=schema.replaceAll("(?m)^--.*$","");
        write(c->{try(var s=c.createStatement()){for(String part:sql.split(";"))if(!part.isBlank())s.execute(part);}migrateOnlineOnly(c);migratePersonalDuration(c);return null;});
        write(c->{update(c,"INSERT OR IGNORE INTO p3_eo_exams(contest_id,code,state,updated_at) SELECT id,'KT_'||REPLACE(id,'-',''),'LEGACY',created_at FROM p3_contests");return null;});
    }
    /** Remove retired venue configuration without changing exams, participants or grades. */
    private static void migrateOnlineOnly(Connection c)throws SQLException {
        if(!rows(c,"SELECT 1 FROM p3_eo_migrations WHERE version=2").isEmpty())return;
        update(c,"DROP INDEX IF EXISTS p3_eo_seat_unique");
        for(String table:List.of("p3_eo_exams","p3_eo_candidates")){
            Set<String> retired=table.equals("p3_eo_exams")?Set.of("format"):Set.of("room_id","seat","shift");
            for(var column:rows(c,"PRAGMA table_info("+table+")"))if(retired.contains(text(column,"name","")))
                update(c,"ALTER TABLE "+table+" DROP COLUMN "+text(column,"name",""));
        }
        update(c,"DROP TABLE IF EXISTS p3_eo_proctors");
        update(c,"DROP TABLE IF EXISTS p3_eo_rooms");
        update(c,"UPDATE p3_eo_drafts SET document_json=json_remove(document_json,'$.format','$.roomIds','$.proctors')");
        for(var publication:rows(c,"SELECT id,rows_json FROM p3_eo_publications")){
            List<Object> cleaned=new ArrayList<>();
            for(Object value:list(parse(text(publication,"rows_json","[]")))){
                Map<String,Object> row=new LinkedHashMap<>(map(value));
                for(String key:List.of("roomName","roomId","seat","shift"))row.remove(key);
                cleaned.add(row);
            }
            update(c,"UPDATE p3_eo_publications SET rows_json=? WHERE id=?",stringify(cleaned),publication.get("id"));
        }
        update(c,"INSERT INTO p3_eo_migrations VALUES(2,?)",System.currentTimeMillis());
    }
    private static void migratePersonalDuration(Connection c)throws SQLException {
        boolean found=false;for(var column:rows(c,"PRAGMA table_info(p3_eo_exams)"))if("duration_minutes".equals(column.get("name")))found=true;
        if(!found)update(c,"ALTER TABLE p3_eo_exams ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 0");
        update(c,"INSERT OR IGNORE INTO p3_eo_migrations VALUES(3,?)",System.currentTimeMillis());
    }
    public static long attemptDeadline(long windowEnd,long startedAt,long durationMinutes,long extraMinutes) {
        return (startedAt>0&&durationMinutes>0?Math.min(windowEnd,startedAt+durationMinutes*60000):windowEnd)+extraMinutes*60000;
    }
    private static void expireAttempts(Connection c,Map<String,Object> exam)throws SQLException {
        if(Set.of("LEGACY","CANCELLED","POSTPONED").contains(text(exam,"state","")))return;
        long duration=number(exam,"duration_minutes",0),end=number(exam,"endAt",0),now=System.currentTimeMillis();
        update(c,"UPDATE p3_eo_candidates SET finished_at=(CASE WHEN ?>0 THEN MIN(?,started_at+?*60000) ELSE ? END)+extra_minutes*60000 WHERE contest_id=? AND started_at>0 AND finished_at=0 AND ?>=(CASE WHEN ?>0 THEN MIN(?,started_at+?*60000) ELSE ? END)+extra_minutes*60000",duration,end,duration,end,exam.get("id"),now,duration,end,duration,end);
    }
    private static void candidateTime(Connection c,Map<String,Object> exam,Identity who)throws SQLException {
        Map<String,Object> u=first(rows(c,"SELECT * FROM p3_eo_candidates WHERE contest_id=? AND user_id=?",exam.get("id"),who.userId()));
        long windowEnd=number(exam,"endAt",0),started=number(u,"started_at",0),end=attemptDeadline(windowEnd,started,number(exam,"duration_minutes",0),number(u,"extra_minutes",0));
        exam.put("windowEndAt",windowEnd);exam.put("attemptStartedAt",started);exam.put("attemptEndAt",started>0?end:0L);exam.put("attemptFinishedAt",number(u,"finished_at",0));exam.put("attemptExpired",started>0&&System.currentTimeMillis()>=end);
        exam.put("endAt",end);exam.put("latestEndAt",end);exam.put("status",number(u,"finished_at",0)>0?"FINISHED":phase(exam));
    }
    public <T>T write(Work<T> action)throws SQLException {
        try(Connection c=repo.open();Statement tx=c.createStatement()){
            tx.execute("BEGIN IMMEDIATE");
            try{T result=action.run(c);tx.execute("COMMIT");return result;}
            catch(SQLException|RuntimeException e){try{tx.execute("ROLLBACK");}catch(SQLException ignored){}throw e;}
        }
    }
    public static List<Map<String,Object>> rows(Connection c,String sql,Object... params)throws SQLException {
        try(var p=c.prepareStatement(sql)){
            for(int i=0;i<params.length;i++)p.setObject(i+1,params[i]);
            try(var r=p.executeQuery()){List<Map<String,Object>> result=new ArrayList<>();var md=r.getMetaData();while(r.next()){
                Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i),r.getObject(i));result.add(row);
            }return result;}
        }
    }
    public static int update(Connection c,String sql,Object... params)throws SQLException {
        try(var p=c.prepareStatement(sql)){for(int i=0;i<params.length;i++)p.setObject(i+1,params[i]);return p.executeUpdate();}
    }
    private static Map<String,Object> first(List<Map<String,Object>> rows){return rows.isEmpty()?new LinkedHashMap<>():rows.getFirst();}
    private static String clean(Object value){return Normalizer.normalize(String.valueOf(value),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('đ','d');}
    public static String phase(Map<String,Object> e){
        String state=text(e,"state","");if(Set.of("POSTPONED","CANCELLED","FINISHED","DRAFT").contains(state))return state;
        long now=System.currentTimeMillis();return now<number(e,"startAt",Long.MAX_VALUE)?"SCHEDULED":now>=number(e,"latestEndAt",number(e,"endAt",0))?"FINISHED":"RUNNING";
    }
    private static final String EXAMS="SELECT x.*,c.title,c.room_id AS roomId,c.created_by_user_id AS ownerId,c.start_at AS startAt,c.end_at AS endAt,c.id AS id,ec.version_id AS examVersionId,v.version_no AS examVersion,COALESCE(json_extract(ec.config_json,'$.examCode'),b.code) AS examCode,COALESCE(json_extract(ec.config_json,'$.examTitle'),json_extract(v.document_json,'$.title')) AS examTitle,COALESCE(json_extract(ec.config_json,'$.scoringMode'),json_extract(v.document_json,'$.scoringMode')) AS scoringMode FROM p3_eo_exams x JOIN p3_contests c ON c.id=x.contest_id LEFT JOIN p3_exam_contests ec ON ec.contest_id=c.id LEFT JOIN p3_exam_versions v ON v.id=ec.version_id LEFT JOIN p3_exam_bank b ON b.id=v.bank_id";
    private Map<String,Object> exam(Connection c,String id)throws SQLException {
        Map<String,Object> e=first(rows(c,EXAMS+" WHERE c.id=?",id));if(e.isEmpty())throw new IllegalArgumentException("Không tìm thấy kỳ thi.");expireAttempts(c,e);e.put("durationMinutes",number(e,"duration_minutes",0));
        var composition=first(rows(c,"SELECT sources_json FROM p3_eo_compositions WHERE version_id=?",e.get("examVersionId")));if(!composition.isEmpty())e.put("compositionSources",list(parse(text(composition,"sources_json","[]"))));e.put("policy",object(text(e,"policy_json","{}")));e.remove("policy_json");e.put("classes",list(parse(text(e,"classes_json","[]"))));e.remove("classes_json");
        if(text(e,"state","").equals("LEGACY")){Map<String,Object> old=object(text(first(rows(c,"SELECT config_json FROM p3_exam_contests WHERE contest_id=?",id)),"config_json","{}"));if(!old.isEmpty()){old.put("resultRelease",Boolean.FALSE.equals(old.get("showScore"))?"AFTER_END":"IMMEDIATE");e.put("policy",old);}}
        e.put("latestEndAt",number(e,"endAt",0)+number(first(rows(c,"SELECT COALESCE(MAX(extra_minutes),0) AS n FROM p3_eo_candidates WHERE contest_id=? AND finished_at=0",id)),"n",0)*60000);
        e.put("status",phase(e));e.put("locked",number(e,"locked",0)!=0);e.put("legacy",text(e,"state","").equals("LEGACY"));
        e.put("candidateCount",number(first(rows(c,"SELECT COUNT(*) AS n FROM p3_eo_candidates WHERE contest_id=?",id)),"n",0));
        e.put("problemCount",number(first(rows(c,"SELECT COUNT(*) AS n FROM p3_contest_problems WHERE contest_id=?",id)),"n",0));
        e.put("totalPoints",first(rows(c,"SELECT COALESCE(SUM(max_score),0) AS n FROM p3_contest_problems WHERE contest_id=?",id)).get("n"));Map<String,Object> distribution=first(rows(c,"SELECT mode,problem_count FROM p3_contest_distribution WHERE contest_id=?",id));e.put("distributionMode",text(distribution,"mode","ALL"));e.put("problemsPerStudent",distribution.get("problem_count"));if(text(distribution,"mode","ALL").equals("RANDOM")&&number(e,"problemCount",0)>0)e.put("totalPoints",((Number)e.get("totalPoints")).doubleValue()*number(distribution,"problem_count",0)/number(e,"problemCount",1));if(ExamScoring.equalTen(e))e.put("totalPoints",10d);return e;
    }
    private String access(Connection c,Identity who,Map<String,Object> e)throws SQLException {
        if(who.role()==Identity.Role.TEACHER&&who.userId().equals(e.get("ownerId")))return "OWNER";
        String id=text(e,"id","");
        if(who.role()==Identity.Role.STUDENT&&!rows(c,"SELECT 1 FROM p3_eo_candidates WHERE contest_id=? AND user_id=?",id,who.userId()).isEmpty())return "STUDENT";
        throw new SecurityException("Bạn không được truy cập kỳ thi này.");
    }
    public Map<String,Object> require(Identity who,String id,boolean owner)throws SQLException {
        try(Connection c=repo.open()){Map<String,Object> e=exam(c,id);String role=access(c,who,e);if(owner&&!role.equals("OWNER"))throw new SecurityException("Chỉ chủ kỳ thi được thực hiện thao tác này.");e.put("access",role);if(role.equals("STUDENT"))candidateTime(c,e,who);return e;}
    }
    public boolean managed(String id)throws SQLException {
        if(id.isBlank())return false;try(Connection c=repo.open()){return !rows(c,"SELECT 1 FROM p3_eo_exams WHERE contest_id=? AND state<>'LEGACY'",id).isEmpty();}
    }
    public void audit(Connection c,String id,Identity who,String action,String detail)throws SQLException {
        update(c,"INSERT INTO p3_eo_audit(contest_id,actor_id,actor_name,action,detail,created_at) VALUES(?,?,?,?,?,?)",id,who.userId(),who.displayName(),action,detail,System.currentTimeMillis());
    }
    public Map<String,Object> draft(Identity who,String id)throws SQLException {
        try(Connection c=repo.open()){Map<String,Object> d=first(rows(c,"SELECT id,owner_id,revision,document_json,updated_at AS updatedAt FROM p3_eo_drafts WHERE id=? AND owner_id=?",id,who.userId()));if(d.isEmpty())throw new SecurityException("Không tìm thấy bản nháp của bạn.");d.put("document",object(text(d,"document_json","{}")));d.remove("document_json");d.remove("owner_id");return d;}
    }
    public Map<String,Object> saveDraft(Identity who,String id,int revision,Map<String,Object> document)throws SQLException {
        if(who.role()!=Identity.Role.TEACHER)throw new SecurityException("Teacher only");
        String json=stringify(document);if(json.getBytes(StandardCharsets.UTF_8).length>96000)throw new IllegalArgumentException("Bản nháp quá lớn.");
        final String key=id.isBlank()?UUID.randomUUID().toString():id;
        write(c->{if(id.isBlank())update(c,"INSERT INTO p3_eo_drafts VALUES(?,?,1,?,?)",key,who.userId(),json,System.currentTimeMillis());
            else if(update(c,"UPDATE p3_eo_drafts SET revision=revision+1,document_json=?,updated_at=? WHERE id=? AND owner_id=? AND revision=? AND NOT EXISTS(SELECT 1 FROM p3_eo_exams WHERE draft_id=?)",json,System.currentTimeMillis(),key,who.userId(),revision,key)!=1)throw new IllegalStateException("Bản nháp đã thay đổi hoặc đã lên lịch. Hãy tải lại.");return null;});return draft(who,key);
    }
    public void deleteDraft(Identity who,String id,int revision)throws SQLException {
        write(c->{if(update(c,"DELETE FROM p3_eo_drafts WHERE id=? AND owner_id=? AND revision=? AND NOT EXISTS(SELECT 1 FROM p3_eo_exams WHERE draft_id=?)",id,who.userId(),revision,id)!=1)throw new IllegalStateException("Không thể xóa bản nháp đã đổi hoặc đã lên lịch.");return null;});
    }
    public List<String> legacyOwners()throws SQLException {try(Connection c=repo.open()){return rows(c,"SELECT DISTINCT c.created_by_user_id AS owner FROM p3_contests c JOIN p3_eo_exams x ON x.contest_id=c.id WHERE x.state='LEGACY'").stream().map(v->text(v,"owner","")).toList();}}
    public Map<String,Object> scheduledDraft(Identity who,String draft)throws SQLException {try(Connection c=repo.open()){var existing=first(rows(c,"SELECT contest_id FROM p3_eo_exams WHERE draft_id=?",draft));return existing.isEmpty()?Map.of():require(who,text(existing,"contest_id",""),true);}}
    public Map<String,Object> schedule(Identity who,String draftId,int revision,Map<String,Object> definition,List<Map<String,Object>> candidates,List<Map<String,Object>> classes)throws SQLException {
        return write(c->{
            Map<String,Object> prior=first(rows(c,"SELECT contest_id FROM p3_eo_exams WHERE draft_id=?",draftId));if(!prior.isEmpty()){Map<String,Object> existing=exam(c,text(prior,"contest_id",""));access(c,who,existing);return existing;}
            Map<String,Object> d=first(rows(c,"SELECT * FROM p3_eo_drafts WHERE id=? AND owner_id=? AND revision=?",draftId,who.userId(),revision));if(d.isEmpty())throw new IllegalStateException("Bản nháp đã thay đổi. Tải lại trước khi lên lịch.");
            Map<String,Object> doc=object(text(d,"document_json","{}"));String id=UUID.randomUUID().toString();long now=System.currentTimeMillis(),start=number(doc,"startAt",0),end=number(doc,"endAt",start+number(doc,"durationMinutes",0)*60000);
            String mode=text(doc,"distributionMode","ALL");List<Object> items=list(definition.get("items"));int count=mode.equals("RANDOM")?integer(doc,"problemsPerStudent",1):items.size();
            Map<String,Object> policy=new LinkedHashMap<>(map(doc.get("policy")));policy.put("showScore",text(policy,"resultRelease","PUBLISH").equals("IMMEDIATE"));policy.put("showPublicTests",!PublicTestVisibility.mode(policy).equals("NONE"));
            update(c,"INSERT INTO p3_contests VALUES(?,?,?,?,?,?,?,'SCHEDULED','AC_COUNT_SCORE_TIME_V2','INDIVIDUAL')",id,text(classes.getFirst(),"id",""),text(doc,"title",""),who.userId(),now,start,end);
            if(ExamScoring.equalTen(definition))update(c,"UPDATE p3_contests SET scoring_rule_version=? WHERE id=?",ExamScoring.RULE,id);
            for(Object value:items){Map<String,Object> item=map(value);String eid=text(item,"exerciseId","");Map<String,Object> question=first(rows(c,"SELECT title,statement,mode,catalog_version FROM p3_exercises WHERE id=?",eid));if(question.isEmpty())throw new IllegalArgumentException("Câu hỏi không tồn tại.");
                update(c,"INSERT INTO p3_contest_problems(contest_id,problem_id,exercise_id,mode,max_score,title_snapshot,statement_snapshot,catalog_version) VALUES(?,?,?,?,?,?,?,?)",id,eid,eid,question.get("mode"),ExamScoring.questionMax(definition,item,count),question.get("title"),question.get("statement"),question.get("catalog_version"));}
            update(c,"INSERT INTO p3_contest_distribution VALUES(?,?,?)",id,mode,count);update(c,"INSERT INTO p3_contest_audience VALUES(?,1)",id);
            Map<String,Object> grading=new LinkedHashMap<>(map(definition.get("grading")));grading.putAll(policy);update(c,"INSERT INTO p3_exam_contests VALUES(?,?,?)",id,definition.get("id"),stringify(grading));
            update(c,"INSERT INTO p3_eo_exams(contest_id,code,subject,description,timezone,policy_json,classes_json,draft_id,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",id,text(doc,"code","").toUpperCase(Locale.ROOT),text(doc,"subject",""),text(doc,"description",""),text(doc,"timezone","Asia/Bangkok"),stringify(policy),stringify(classes),draftId,now);
            update(c,"UPDATE p3_eo_exams SET duration_minutes=? WHERE contest_id=?",number(doc,"durationMinutes",0),id);
            SecureRandom random=new SecureRandom();
            for(Map<String,Object> student:candidates){String uid=text(student,"id","");update(c,"INSERT INTO p3_eo_candidates(contest_id,user_id,username,student_code,display_name,class_id,class_name) VALUES(?,?,?,?,?,?,?)",id,uid,student.get("username"),student.get("studentCode"),student.get("name"),student.get("classId"),student.get("classCode"));
                update(c,"INSERT INTO p3_contest_draws VALUES(?,?,?,?,?)",id,uid,now,student.get("username"),student.get("name"));List<Object> drawn=new ArrayList<>(items);if(mode.equals("RANDOM"))Collections.shuffle(drawn,random);
                for(int n=0;n<count;n++)update(c,"INSERT INTO p3_contest_student_problems VALUES(?,?,?,?)",id,uid,map(drawn.get(n)).get("exerciseId"),n+1);
            }
            Map<String,Object> candidateSchedule=exam(c,id);for(var student:candidates)available(c,candidateSchedule,text(student,"id",""));
            boolean enabled=Boolean.TRUE.equals(policy.get("monitoring"));update(c,"INSERT INTO p3_exam_modes VALUES(?,?,?,?,?,?)",id,enabled?1:0,UUID.randomUUID().toString(),integer(policy,"tabLimit",3),integer(policy,"pasteLimit",3),now);
            audit(c,id,who,"SCHEDULE","Đã chốt "+candidates.size()+" thí sinh và phiên bản đề "+definition.get("code"));return exam(c,id);
        });
    }
    /** Preserve legacy IDs; capture enrolled members only for unrestricted old contests. */
    public void importLegacyRoster(Identity who,Map<String,Object> directory)throws SQLException {
        write(c->{update(c,"INSERT OR IGNORE INTO p3_eo_exams(contest_id,code,state,updated_at) SELECT id,'KT_'||REPLACE(id,'-',''),'LEGACY',created_at FROM p3_contests");for(Map<String,Object> e:rows(c,"SELECT c.id,c.room_id FROM p3_contests c JOIN p3_eo_exams x ON x.contest_id=c.id WHERE c.created_by_user_id=? AND x.state='LEGACY'",who.userId())){
            String id=text(e,"id","");boolean restricted=!rows(c,"SELECT 1 FROM p3_contest_audience WHERE contest_id=? AND restricted=1",id).isEmpty();
            for(Object value:list(directory.get("students"))){Map<String,Object> s=map(value);if(!Objects.equals(s.get("classId"),e.get("room_id")))continue;
                if(restricted&&rows(c,"SELECT 1 FROM p3_contest_draws WHERE contest_id=? AND user_id=?",id,s.get("id")).isEmpty())continue;
                update(c,"INSERT OR IGNORE INTO p3_eo_candidates(contest_id,user_id,username,student_code,display_name,class_id,class_name) VALUES(?,?,?,?,?,?,?)",id,s.get("id"),s.get("username"),s.get("studentCode"),s.get("name"),s.get("classId"),s.get("classCode"));}
            List<Object> cls=list(directory.get("classes")).stream().filter(v->Objects.equals(map(v).get("id"),e.get("room_id"))).toList();update(c,"UPDATE p3_eo_exams SET classes_json=? WHERE contest_id=?",stringify(cls),id);
        }return null;});
    }
    public Map<String,Object> directory(Identity who,Map<String,Object> req)throws SQLException {
        try(Connection c=repo.open()){
            List<Map<String,Object>> all=new ArrayList<>();for(Map<String,Object> key:rows(c,"SELECT c.id FROM p3_contests c JOIN p3_eo_exams x ON x.contest_id=c.id WHERE c.created_by_user_id=? OR EXISTS(SELECT 1 FROM p3_eo_candidates u WHERE u.contest_id=c.id AND u.user_id=?) ORDER BY c.created_at DESC",who.userId(),who.userId())){Map<String,Object> e=exam(c,text(key,"id",""));e.put("access",access(c,who,e));if(who.role()==Identity.Role.STUDENT)candidateTime(c,e,who);all.add(e);}
            if(who.role()==Identity.Role.TEACHER)for(Map<String,Object> d:rows(c,"SELECT id,revision,document_json,updated_at FROM p3_eo_drafts WHERE owner_id=? AND NOT EXISTS(SELECT 1 FROM p3_eo_exams WHERE draft_id=p3_eo_drafts.id)",who.userId())){Map<String,Object> e=new LinkedHashMap<>(object(text(d,"document_json","{}")));e.putAll(mapOf("id",d.get("id"),"revision",d.get("revision"),"updatedAt",d.get("updated_at"),"status","DRAFT","access","OWNER","candidateCount",list(e.get("studentIds")).size()));all.add(e);}
            String q=clean(text(req,"query","")),status=text(req,"filterStatus",""),subject=text(req,"subject","");
            List<Map<String,Object>> filtered=all.stream().filter(e->clean(e.get("code")+" "+e.get("title")+" "+e.get("subject")+" "+stringify(e.get("classes"))).contains(q)&&(status.isBlank()||status.equals(e.get("status")))&&(subject.isBlank()||subject.equals(e.get("subject")))).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            String sort=text(req,"sort","time");filtered.sort(sort.equals("code")?Comparator.comparing(e->text(e,"code","")):sort.equals("title")?Comparator.comparing(e->text(e,"title","")):Comparator.<Map<String,Object>>comparingLong(e->number(e,"startAt",number(e,"updatedAt",0))).reversed());
            int size=Math.max(10,Math.min(50,integer(req,"pageSize",10))),pages=Math.max(1,(filtered.size()+size-1)/size),page=Math.max(1,Math.min(pages,integer(req,"page",1)));
            return mapOf("exams",filtered.subList((page-1)*size,Math.min(page*size,filtered.size())),"total",filtered.size(),"page",page,"totalPages",pages,"stats",mapOf("total",filtered.size(),"upcoming",filtered.stream().filter(e->e.get("status").equals("SCHEDULED")).count(),"running",filtered.stream().filter(e->e.get("status").equals("RUNNING")).count(),"finished",filtered.stream().filter(e->e.get("status").equals("FINISHED")).count()),"subjects",all.stream().map(e->text(e,"subject","")).filter(s->!s.isBlank()).distinct().sorted().toList(),"serverTime",System.currentTimeMillis());
        }
    }
    public List<Map<String,Object>> candidates(String id)throws SQLException {try(Connection c=repo.open()){Map<String,Object> exam=exam(c,id);List<Map<String,Object>> candidates=rows(c,"SELECT u.user_id AS userId,u.username,u.student_code AS studentCode,u.display_name AS displayName,u.class_id AS classId,u.class_name AS className,u.confirmed_at AS confirmedAt,u.started_at AS startedAt,u.last_activity AS lastActivity,u.finished_at AS finishedAt,u.extra_minutes AS extraMinutes,u.note,(SELECT COUNT(DISTINCT s.exercise_id) FROM p3_contest_problem_submissions s WHERE s.contest_id=u.contest_id AND s.user_id=u.user_id) AS submittedQuestions,(SELECT COUNT(*) FROM p3_contest_problem_submissions s WHERE s.contest_id=u.contest_id AND s.user_id=u.user_id) AS attempts,(SELECT COUNT(*) FROM p3_contest_student_problems p WHERE p.contest_id=u.contest_id AND p.user_id=u.user_id) AS assignedQuestions FROM p3_eo_candidates u WHERE u.contest_id=? ORDER BY u.student_code",id);for(var u:candidates){long started=number(u,"startedAt",0),end=attemptDeadline(number(exam,"endAt",0),started,number(exam,"duration_minutes",0),number(u,"extraMinutes",0));u.put("deadlineAt",started>0?end:0L);u.put("remainingMillis",started>0&&number(u,"finishedAt",0)==0?Math.max(0,end-System.currentTimeMillis()):0L);}return candidates;}}
    public List<Map<String,Object>> submissions(String id)throws SQLException {
        try(Connection c=repo.open()){return rows(c,"SELECT s.id,s.contest_id AS contestId,s.exercise_id AS exerciseId,q.title_snapshot AS exerciseTitle,s.user_id AS userId,s.username,u.student_code AS studentCode,u.display_name AS displayName,u.class_name AS className,s.submitted_at AS submittedAt,s.status,s.score AS machineScore,COALESCE(a.score,s.score) AS score,s.max_score AS maxScore,s.verdict,s.passed_tests AS passedTests,s.total_tests AS totalTests,s.feedback,s.duration_ms AS durationMs,a.reason AS adjustmentReason,(SELECT COUNT(*) FROM p3_eo_grade_history h WHERE h.submission_id=s.id) AS rejudges FROM p3_contest_problem_submissions s JOIN p3_contest_problems q ON q.contest_id=s.contest_id AND q.exercise_id=s.exercise_id LEFT JOIN p3_eo_candidates u ON u.contest_id=s.contest_id AND u.user_id=s.user_id LEFT JOIN p3_eo_adjustments a ON a.submission_id=s.id WHERE s.contest_id=? ORDER BY s.submitted_at DESC",id);}
    }
    public Map<String,Object> detail(Identity who,String id)throws SQLException {
        Map<String,Object> e=require(who,id,false);String access=text(e,"access","");
        try(Connection c=repo.open()){
            List<Map<String,Object>> roster=candidates(id).stream().filter(s->access.equals("OWNER")||access.equals("STUDENT")&&who.userId().equals(s.get("userId"))).toList();
            Set<Object> visible=new HashSet<>();roster.forEach(s->visible.add(s.get("userId")));
            List<Map<String,Object>> subs=submissions(id).stream().filter(s->access.equals("OWNER")||visible.contains(s.get("userId"))).map(s->{if(access.equals("STUDENT"))try{return redact(who,id,s);}catch(SQLException ex){throw new IllegalStateException(ex);}return s;}).toList();
            List<Map<String,Object>> messages=rows(c,"SELECT id,sender,message,created_at AS createdAt,read_at AS readAt FROM p3_eo_messages WHERE contest_id=? AND user_id=? ORDER BY id DESC LIMIT 100",id,who.userId());
            List<Map<String,Object>> audits=access.equals("OWNER")?rows(c,"SELECT id,actor_name AS actor,action,detail,created_at AS createdAt FROM p3_eo_audit WHERE contest_id=? ORDER BY id DESC LIMIT 200",id):List.of();
            List<Map<String,Object>> versions=access.equals("OWNER")?rows(c,"SELECT id,created_at AS createdAt FROM p3_eo_publications WHERE contest_id=? ORDER BY id DESC",id):List.of();
            if(access.equals("STUDENT")){e.remove("classes");e.remove("candidateCount");}
            return mapOf("exam",e,"candidates",roster,"submissions",subs,"audit",audits,"messages",messages,"publications",versions,"serverTime",System.currentTimeMillis());
        }
    }
    private void available(Connection c,Map<String,Object> exam,String user)throws SQLException {
        if(!rows(c,"SELECT 1 FROM p3_eo_candidates u JOIN p3_contests t ON t.id=u.contest_id JOIN p3_eo_exams x ON x.contest_id=t.id WHERE u.user_id=? AND u.contest_id<>? AND x.state NOT IN ('CANCELLED','POSTPONED','FINISHED') AND t.start_at<? AND (t.end_at+u.extra_minutes*60000)>? LIMIT 1",user,exam.get("id"),exam.get("endAt"),exam.get("startAt")).isEmpty())throw new IllegalStateException("Thí sinh đã có lịch thi chồng lấn.");
    }
    public void control(Identity who,String id,int revision,String action,Map<String,Object> req)throws SQLException {
        write(c->{Map<String,Object> e=exam(c,id);if(!access(c,who,e).equals("OWNER"))throw new SecurityException("Owner only");if(integer(e,"revision",0)!=revision)throw new IllegalStateException("Kỳ thi đã thay đổi. Hãy tải lại.");String reason=text(req,"reason","").strip();if(reason.isBlank()||reason.length()>1000)throw new IllegalArgumentException("Nhập lý do (tối đa 1000 ký tự).");
            String phase=phase(e);long now=System.currentTimeMillis();
            switch(action){
                case "EXTEND"->{int minutes=integer(req,"minutes",0);if(minutes<1||minutes>1440||!Set.of("SCHEDULED","RUNNING").contains(phase))throw new IllegalArgumentException("Chỉ gia hạn 1–1440 phút cho kỳ thi chưa kết thúc.");List<Object> users=list(req.get("userIds"));
                    for(var u:rows(c,"SELECT * FROM p3_eo_candidates WHERE contest_id=?",id)){String uid=text(u,"user_id","");if(!users.isEmpty()&&!users.contains(uid))continue;if(number(u,"finished_at",0)>0&&!users.isEmpty())throw new IllegalArgumentException("Thí sinh đã kết thúc lượt.");Map<String,Object> period=new LinkedHashMap<>(e);period.put("endAt",number(e,"endAt",0)+(number(u,"extra_minutes",0)+minutes)*60000);available(c,period,uid);}
                    long next=number(e,"endAt",0)+minutes*60000L;
                    if(users.isEmpty())update(c,"UPDATE p3_contests SET end_at=? WHERE id=?",next,id);else for(Object uid:new LinkedHashSet<>(users))if(update(c,"UPDATE p3_eo_candidates SET extra_minutes=extra_minutes+? WHERE contest_id=? AND user_id=? AND finished_at=0",minutes,id,uid)!=1)throw new IllegalArgumentException("Thí sinh không hợp lệ hoặc đã kết thúc lượt.");}
                case "LOCK"->{if(phase.equals("FINISHED")||phase.equals("CANCELLED"))throw new IllegalStateException("Kỳ thi đã kết thúc.");update(c,"UPDATE p3_eo_exams SET locked=? WHERE contest_id=?",Boolean.TRUE.equals(req.get("locked"))?1:0,id);}
                case "END"->{if(!phase.equals("RUNNING"))throw new IllegalStateException("Kỳ thi không đang diễn ra.");update(c,"UPDATE p3_contests SET end_at=MIN(end_at,?) WHERE id=?",now,id);update(c,"UPDATE p3_eo_exams SET state='FINISHED' WHERE contest_id=?",id);update(c,"UPDATE p3_eo_candidates SET finished_at=CASE WHEN finished_at=0 AND started_at>0 THEN ? ELSE finished_at END WHERE contest_id=?",now,id);}
                case "POSTPONE"->{if(!phase.equals("SCHEDULED"))throw new IllegalStateException("Chỉ hoãn kỳ thi chưa bắt đầu.");update(c,"UPDATE p3_eo_exams SET state='POSTPONED' WHERE contest_id=?",id);}
                case "RESCHEDULE"->{if(!phase.equals("POSTPONED"))throw new IllegalStateException("Kỳ thi chưa tạm hoãn.");long start=number(req,"startAt",0),end=start+(number(e,"endAt",0)-number(e,"startAt",0));if(start<=now)throw new IllegalArgumentException("Chọn thời điểm mới trong tương lai.");Map<String,Object> period=new LinkedHashMap<>(e);period.put("startAt",start);period.put("endAt",end);for(var u:rows(c,"SELECT user_id FROM p3_eo_candidates WHERE contest_id=?",id))available(c,period,text(u,"user_id",""));update(c,"UPDATE p3_contests SET start_at=?,end_at=? WHERE id=?",start,end,id);update(c,"UPDATE p3_eo_exams SET state='PUBLISHED' WHERE contest_id=?",id);update(c,"UPDATE p3_eo_candidates SET confirmed_at=0 WHERE contest_id=?",id);}
                case "CANCEL"->{if(!Set.of("SCHEDULED","POSTPONED").contains(phase))throw new IllegalStateException("Chỉ hủy trước khi bắt đầu; kỳ thi đang chạy dùng Kết thúc.");update(c,"UPDATE p3_eo_exams SET state='CANCELLED',locked=1 WHERE contest_id=?",id);}
                case "UPDATE"->{if(!Set.of("SCHEDULED","POSTPONED").contains(phase))throw new IllegalStateException("Không sửa thông tin kỳ thi đang chạy/đã kết thúc.");String title=text(req,"title","").strip(),description=text(req,"description","");if(title.isBlank()||title.length()>120||description.length()>6000)throw new IllegalArgumentException("Tên 1–120 ký tự, mô tả tối đa 6000.");update(c,"UPDATE p3_contests SET title=? WHERE id=?",title,id);update(c,"UPDATE p3_eo_exams SET description=? WHERE contest_id=?",description,id);}
                default->throw new IllegalArgumentException("Thao tác điều hành không hợp lệ.");
            }update(c,"UPDATE p3_eo_exams SET revision=revision+1,updated_at=? WHERE contest_id=?",now,id);audit(c,id,who,action,reason);return null;});
    }
    public void candidateAction(Identity who,String id,String action)throws SQLException {
        // Expiration is persisted before the command transaction, including rejected resumes.
        require(who,id,false);
        write(c->{Map<String,Object> e=exam(c,id);if(!access(c,who,e).equals("STUDENT"))throw new SecurityException("Chỉ thí sinh thực hiện thao tác này.");long now=System.currentTimeMillis();
            if(action.equals("CONFIRM")){if(Set.of("CANCELLED","FINISHED").contains(phase(e)))throw new IllegalStateException("Kỳ thi không còn nhận xác nhận.");update(c,"UPDATE p3_eo_candidates SET confirmed_at=? WHERE contest_id=? AND user_id=?",now,id,who.userId());}
            else {
                Map<String,Object> u=first(rows(c,"SELECT * FROM p3_eo_candidates WHERE contest_id=? AND user_id=?",id,who.userId()));
                long started=number(u,"started_at",0),duration=number(e,"duration_minutes",0),end=attemptDeadline(number(e,"endAt",0),started,duration,number(u,"extra_minutes",0));
                if(Set.of("CANCELLED","POSTPONED","FINISHED").contains(text(e,"state",""))||now<number(e,"startAt",Long.MAX_VALUE)||now>=end||Boolean.TRUE.equals(e.get("locked"))||number(u,"finished_at",0)>0)throw new IllegalStateException("Kỳ thi chưa mở, đang khóa hoặc đã hết thời gian làm bài của bạn.");
                if(action.equals("FINISH")){if(started==0)throw new IllegalStateException("Bạn chưa bắt đầu thi.");update(c,"UPDATE p3_eo_candidates SET finished_at=? WHERE contest_id=? AND user_id=?",now,id,who.userId());audit(c,id,who,"STUDENT_FINISH","Kết thúc lượt thi");}
                else {if(duration>0&&started==0&&!action.equals("BEGIN"))throw new IllegalStateException("Bấm Bắt đầu thi để mở lượt làm bài.");update(c,"UPDATE p3_eo_candidates SET started_at=CASE WHEN started_at=0 THEN ? ELSE started_at END,last_activity=? WHERE contest_id=? AND user_id=? AND finished_at=0",now,now,id,who.userId());}
            }return null;
        });
    }    public void notify(Identity who,String id,String message,List<String> users)throws SQLException {
        write(c->{Map<String,Object> e=exam(c,id);String role=access(c,who,e);String body=message.strip();if(body.isBlank()||body.length()>2000)throw new IllegalArgumentException("Thông báo cần 1–2000 ký tự.");
            List<String> recipients=new ArrayList<>();if(role.equals("STUDENT"))recipients.add(text(e,"ownerId",""));else for(var u:rows(c,"SELECT user_id FROM p3_eo_candidates WHERE contest_id=?",id))if(users.isEmpty()||users.contains(text(u,"user_id","")))recipients.add(text(u,"user_id",""));
            if(recipients.isEmpty())throw new IllegalArgumentException("Không có người nhận hợp lệ.");for(String uid:recipients)update(c,"INSERT INTO p3_eo_messages(contest_id,user_id,sender,message,created_at) VALUES(?,?,?,?,?)",id,uid,who.displayName(),body,System.currentTimeMillis());audit(c,id,who,"NOTIFY","Gửi thông báo cho "+recipients.size()+" người");return null;});
    }
    public void markRead(Identity who,String id)throws SQLException {require(who,id,false);write(c->{update(c,"UPDATE p3_eo_messages SET read_at=? WHERE contest_id=? AND user_id=? AND read_at=0",System.currentTimeMillis(),id,who.userId());return null;});}
    public Map<String,Object> submission(Identity who,String id,String sid)throws SQLException {
        require(who,id,true);var sub=repo.getContestSubmission(sid);if(sub==null||!sub.contestId().equals(id))throw new IllegalArgumentException("Bài nộp không thuộc kỳ thi.");
        try(Connection c=repo.open()){return mapOf("submission",submissions(id).stream().filter(s->sid.equals(s.get("id"))).findFirst().orElseThrow(),"source",sub.source(),"tests",repo.contestSubmissionTestResults(sid),"structureResults",repo.structureResults(sid),"history",rows(c,"SELECT id,reason,result_json AS result,created_at AS createdAt FROM p3_eo_grade_history WHERE submission_id=? ORDER BY id DESC",sid));}
    }
    public void adjust(Identity who,String id,String sid,double score,String reason)throws SQLException {
        require(who,id,true);write(c->{Map<String,Object> s=first(rows(c,"SELECT score,max_score,status FROM p3_contest_problem_submissions WHERE id=? AND contest_id=?",sid,id));if(s.isEmpty()||!text(s,"status","").equals("DONE")||!Double.isFinite(score)||score<0||score>((Number)s.get("max_score")).doubleValue()||reason.isBlank()||reason.length()>1000)throw new IllegalArgumentException("Điểm/lý do không hợp lệ hoặc bài chưa chấm xong.");update(c,"INSERT INTO p3_eo_adjustments VALUES(?,?,?,?,?) ON CONFLICT(submission_id) DO UPDATE SET score=excluded.score,reason=excluded.reason,actor_id=excluded.actor_id,updated_at=excluded.updated_at",sid,score,reason,who.userId(),System.currentTimeMillis());audit(c,id,who,"ADJUST",stringify(mapOf("submissionId",sid,"machineScore",s.get("score"),"score",score,"reason",reason)));return null;});
    }
    public List<String> requeue(Identity who,String id,List<String> ids,String reason)throws SQLException {
        require(who,id,true);if(ids.isEmpty()||ids.size()>100||reason.isBlank()||reason.length()>1000)throw new IllegalArgumentException("Chọn 1–100 bài và nhập lý do chấm lại.");
        return write(c->{for(String sid:new LinkedHashSet<>(ids)){Map<String,Object> s=first(rows(c,"SELECT * FROM p3_contest_problem_submissions WHERE id=? AND contest_id=? AND status='DONE'",sid,id));if(s.isEmpty())throw new IllegalStateException("Bài không hợp lệ hoặc đang chấm; tải lại danh sách.");s.remove("source_code");update(c,"INSERT INTO p3_eo_grade_history(submission_id,actor_id,reason,result_json,created_at) VALUES(?,?,?,?,?)",sid,who.userId(),reason,stringify(s),System.currentTimeMillis());update(c,"UPDATE p3_contest_problem_submissions SET status='QUEUED',verdict='QUEUED',feedback='Đang chờ chấm lại.' WHERE id=?",sid);}audit(c,id,who,"REJUDGE",reason+" · "+ids.size()+" bài");return List.copyOf(new LinkedHashSet<>(ids));});
    }
    private static boolean allEnded(Connection c,Map<String,Object> exam)throws SQLException {
        if(text(exam,"state","").equals("FINISHED"))return true;
        if(!phase(exam).equals("FINISHED"))return false;
        return rows(c,"SELECT 1 FROM p3_eo_candidates WHERE contest_id=? AND finished_at=0 AND ?<( ?+extra_minutes*60000) LIMIT 1",exam.get("id"),System.currentTimeMillis(),exam.get("endAt")).isEmpty();
    }
    public Map<String,Object> publish(Identity who,String id,List<String> ids)throws SQLException {
        require(who,id,true);return write(c->{Map<String,Object> e=exam(c,id);if(!allEnded(c,e))throw new IllegalStateException("Chỉ công bố khi đã hết thời gian của tất cả thí sinh, kể cả phần gia hạn.");List<Map<String,Object>> current=submissions(id).stream().filter(s->ids.isEmpty()||ids.contains(text(s,"id",""))).toList();if(current.isEmpty()||(!ids.isEmpty()&&current.size()!=new HashSet<>(ids).size()))throw new IllegalArgumentException("Chọn bài nộp hợp lệ để công bố.");
            if(current.stream().anyMatch(s->!text(s,"status","").equals("DONE")||Set.of("INTERNAL_ERROR","SANDBOX_UNAVAILABLE").contains(text(s,"verdict",""))))throw new IllegalStateException("Còn bài chưa chấm xong hoặc lỗi hạ tầng. Xử lý trước khi công bố.");
            for(var row:current){row.put("testResults",repo.contestSubmissionTestResults(text(row,"id","")));row.put("structureResults",repo.structureResults(text(row,"id","")));}
            update(c,"INSERT INTO p3_eo_publications(contest_id,actor_id,created_at,rows_json) VALUES(?,?,?,?)",id,who.userId(),System.currentTimeMillis(),stringify(current));audit(c,id,who,"PUBLISH","Công bố "+current.size()+" bài nộp");return mapOf("published",current.size());});
    }
    public Map<String,Object> redact(Identity who,String id,Map<String,Object> submission)throws SQLException {
        Map<String,Object> e;boolean ended;try(Connection c=repo.open()){e=exam(c,id);ended=allEnded(c,e);}Map<String,Object> policy=map(e.get("policy"));String release=text(policy,"resultRelease","IMMEDIATE");boolean visible=release.equals("IMMEDIATE")||release.equals("AFTER_END")&&ended;Map<String,Object> out=new LinkedHashMap<>(submission);
        if(release.equals("PUBLISH"))try(Connection c=repo.open()){
            for(var version:rows(c,"SELECT id,rows_json FROM p3_eo_publications WHERE contest_id=? ORDER BY id DESC",id)){
                for(Object value:list(parse(text(version,"rows_json","[]")))){Map<String,Object> row=map(value);if(Objects.equals(row.get("id"),submission.get("id"))&&who.userId().equals(row.get("userId"))){out.putAll(row);out.put("publicationId",version.get("id"));visible=true;break;}}
                if(visible)break;
            }
        }
        if(!visible){out.put("score",null);out.put("machineScore",null);out.put("scoreHidden",true);out.put("verdict","HIDDEN");out.put("feedback","Kết quả chưa được công bố.");out.remove("adjustmentReason");}
        if(visible)out.remove("scoreHidden");
        List<Map<String,Object>> tests=out.containsKey("testResults")?list(out.get("testResults")).stream().map(v->map(v)).toList():visible&&!out.containsKey("publicationId")?repo.contestSubmissionTestResults(text(submission,"id","")):List.of();
        if(visible&&!out.containsKey("publicationId"))out.put("structureResults",repo.structureResults(text(submission,"id","")));
        PublicTestVisibility.apply(out,policy,tests,visible);
        return out;
    }
    /** Called inside the same IMMEDIATE transaction that inserts a contest submission. */
    public static void beforeSubmission(Connection c,ContestSubmissionRecord sub)throws SQLException {
        if(rows(c,"SELECT 1 FROM sqlite_master WHERE type='table' AND name='p3_eo_exams'").isEmpty())return;
        Map<String,Object> e=first(rows(c,"SELECT x.*,t.start_at AS startAt,t.end_at AS endAt FROM p3_eo_exams x JOIN p3_contests t ON t.id=x.contest_id WHERE x.contest_id=? AND x.state<>'LEGACY'",sub.contestId()));if(e.isEmpty())return;
        Map<String,Object> u=first(rows(c,"SELECT * FROM p3_eo_candidates WHERE contest_id=? AND user_id=?",sub.contestId(),sub.userId()));long now=System.currentTimeMillis();
        if(u.isEmpty()||number(u,"finished_at",0)>0||number(e,"locked",0)!=0||Set.of("CANCELLED","POSTPONED","FINISHED").contains(text(e,"state",""))||now<number(e,"startAt",Long.MAX_VALUE)||(number(e,"duration_minutes",0)>0&&number(u,"started_at",0)==0)||now>=attemptDeadline(number(e,"endAt",0),number(u,"started_at",0),number(e,"duration_minutes",0),number(u,"extra_minutes",0)))throw new SecurityException("Kỳ thi chưa mở, đang khóa hoặc lượt thi đã kết thúc.");
        Map<String,Object> policy=object(text(e,"policy_json","{}"));int limit=Boolean.FALSE.equals(policy.get("allowResubmit"))?1:integer(policy,"maxAttempts",0);
        if(limit>0&&number(first(rows(c,"SELECT COUNT(*) AS n FROM p3_contest_problem_submissions WHERE contest_id=? AND user_id=? AND exercise_id=? AND verdict NOT IN ('INTERNAL_ERROR','SANDBOX_UNAVAILABLE')",sub.contestId(),sub.userId(),sub.exerciseId())),"n",0)>=limit)throw new IllegalArgumentException("Đã hết số lượt nộp cho câu này.");
        update(c,"UPDATE p3_eo_candidates SET started_at=CASE WHEN started_at=0 THEN ? ELSE started_at END,last_activity=? WHERE contest_id=? AND user_id=?",now,now,sub.contestId(),sub.userId());
    }
    public long deadline(Identity who,String id,long base)throws SQLException {
        try(Connection c=repo.open()){
            Map<String,Object> row=first(rows(c,"SELECT u.started_at,u.finished_at,u.extra_minutes,x.duration_minutes FROM p3_eo_candidates u JOIN p3_eo_exams x ON x.contest_id=u.contest_id WHERE u.contest_id=? AND u.user_id=?",id,who.userId()));
            if(row.isEmpty())return base;
            if(number(row,"finished_at",0)>0||number(row,"duration_minutes",0)>0&&number(row,"started_at",0)==0)return 0;
            return attemptDeadline(base,number(row,"started_at",0),number(row,"duration_minutes",0),number(row,"extra_minutes",0));
        }
    }    public void checkRead(Identity who,String id)throws SQLException {Map<String,Object> e=require(who,id,false);long now=System.currentTimeMillis();if(who.role()==Identity.Role.STUDENT&&number(e,"durationMinutes",0)>0&&number(e,"attemptStartedAt",0)==0)throw new SecurityException("Bấm Bắt đầu thi trước khi mở đề.");if(who.role()==Identity.Role.STUDENT&&(Set.of("CANCELLED","POSTPONED").contains(phase(e))||Boolean.TRUE.equals(map(e.get("policy")).get("lockOutsideTime"))&&(phase(e).equals("FINISHED")||now<number(e,"startAt",0)||now>=number(e,"endAt",0))))throw new SecurityException("Nội dung đề chỉ mở trong thời gian kỳ thi cho phép.");}
    public Map<String,Object> savedCode(Identity who,String id,String exercise,String source,boolean save)throws SQLException {
        require(who,id,false);if(who.role()!=Identity.Role.STUDENT||!ContestDistributionRepository.studentProblems(repo,id,who).contains(exercise))throw new SecurityException("Không có quyền với câu hỏi này.");checkRead(who,id);
        if(save){if(source.getBytes(StandardCharsets.UTF_8).length>32768)throw new IllegalArgumentException("Code tối đa 32 KiB.");write(c->{Map<String,Object> e=exam(c,id);Map<String,Object> u=first(rows(c,"SELECT * FROM p3_eo_candidates WHERE contest_id=? AND user_id=?",id,who.userId()));long now=System.currentTimeMillis(),end=attemptDeadline(number(e,"endAt",0),number(u,"started_at",0),number(e,"duration_minutes",0),number(u,"extra_minutes",0));if(now<number(e,"startAt",Long.MAX_VALUE)||now>=end||Set.of("CANCELLED","POSTPONED","FINISHED").contains(text(e,"state",""))||Boolean.TRUE.equals(e.get("locked"))||number(u,"finished_at",0)>0||number(e,"duration_minutes",0)>0&&number(u,"started_at",0)==0)throw new IllegalStateException("Kỳ thi đã kết thúc, đang khóa hoặc chưa mở.");update(c,"INSERT INTO p3_eo_code_drafts VALUES(?,?,?,?,?) ON CONFLICT(contest_id,user_id,exercise_id) DO UPDATE SET source_code=excluded.source_code,updated_at=excluded.updated_at",id,who.userId(),exercise,source,System.currentTimeMillis());return null;});}
        try(Connection c=repo.open()){return first(rows(c,"SELECT source_code AS source,updated_at AS updatedAt FROM p3_eo_code_drafts WHERE contest_id=? AND user_id=? AND exercise_id=?",id,who.userId(),exercise));}
    }
    public void appendCandidates(Identity who,String id,List<Map<String,Object>> students)throws SQLException {
        require(who,id,true);write(c->{Map<String,Object> e=exam(c,id);if(!Set.of("SCHEDULED","POSTPONED").contains(phase(e)))throw new IllegalStateException("Chỉ thêm thí sinh trước khi bắt đầu.");
            List<Map<String,Object>> pool=rows(c,"SELECT exercise_id FROM p3_contest_problems WHERE contest_id=? ORDER BY rowid",id);Map<String,Object> distribution=first(rows(c,"SELECT mode,problem_count FROM p3_contest_distribution WHERE contest_id=?",id));int count=text(distribution,"mode","ALL").equals("RANDOM")?integer(distribution,"problem_count",pool.size()):pool.size();
            for(var s:students){String uid=text(s,"id","");if(!rows(c,"SELECT 1 FROM p3_eo_candidates WHERE contest_id=? AND user_id=?",id,uid).isEmpty())continue;available(c,e,uid);
                update(c,"INSERT INTO p3_eo_candidates(contest_id,user_id,username,student_code,display_name,class_id,class_name) VALUES(?,?,?,?,?,?,?)",id,uid,s.get("username"),s.get("studentCode"),s.get("name"),s.get("classId"),s.get("classCode"));
                update(c,"INSERT OR IGNORE INTO p3_contest_draws VALUES(?,?,?,?,?)",id,uid,System.currentTimeMillis(),s.get("username"),s.get("name"));List<Map<String,Object>> drawn=new ArrayList<>(pool);if(text(distribution,"mode","ALL").equals("RANDOM"))Collections.shuffle(drawn,new SecureRandom());
                for(int n=0;n<count;n++)update(c,"INSERT OR IGNORE INTO p3_contest_student_problems VALUES(?,?,?,?)",id,uid,drawn.get(n).get("exercise_id"),n+1);
            }update(c,"UPDATE p3_eo_exams SET revision=revision+1,updated_at=? WHERE contest_id=?",System.currentTimeMillis(),id);audit(c,id,who,"APPEND_ROSTER","Kiểm tra và thêm "+students.size()+" thí sinh");return null;});
    }
    public Map<String,Object> pulse(Identity who)throws SQLException {
        try(Connection c=repo.open()){
            return first(rows(c,"SELECT COALESCE(MAX(x.updated_at),0) AS changed,COUNT(*) AS count FROM p3_eo_exams x JOIN p3_contests t ON t.id=x.contest_id WHERE t.created_by_user_id=? OR EXISTS(SELECT 1 FROM p3_eo_candidates u WHERE u.contest_id=t.id AND u.user_id=?)",who.userId(),who.userId()));
        }
    }
}
