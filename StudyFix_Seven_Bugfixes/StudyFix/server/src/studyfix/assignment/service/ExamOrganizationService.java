package studyfix.assignment.service;

import studyfix.Identity;
import studyfix.StudyFixGateway;
import studyfix.assignment.persistence.*;
import java.sql.SQLException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Authentication is supplied by the existing gateway; all organization ACLs live on the server. */
public final class ExamOrganizationService {
    @FunctionalInterface public interface Rejudge {void run(Identity who,String contest,List<String> ids,String reason)throws Exception;}
    public final ExamOrganizationRepository repository;
    private final StudyFixGateway gateway;
    private final ExamBankService bank;
    private final ExamMonitoringRepository monitoring;
    private final Rejudge rejudge;
    public static final Set<String> TYPES=Set.of("EO_SOURCES","EO_LIST","EO_GET","EO_PULSE","EO_DRAFT_GET","EO_DRAFT_SAVE","EO_DRAFT_DELETE","EO_SCHEDULE","EO_CLONE","EO_ROSTER_ADD","EO_CONTROL","EO_CONFIRM","EO_BEGIN","EO_HEARTBEAT","EO_FINISH","EO_NOTIFY","EO_READ","EO_SUBMISSION","EO_ADJUST","EO_REJUDGE","EO_PUBLISH","EO_EXPORT","EO_CODE_GET","EO_CODE_SAVE","EO_HISTORY");
    public ExamOrganizationService(AssignmentRepository repo,StudyFixGateway gateway,ExamBankService bank,ExamMonitoringRepository monitoring,Rejudge rejudge)throws Exception {
        repository=new ExamOrganizationRepository(repo);this.gateway=gateway;this.bank=bank;this.monitoring=monitoring;this.rejudge=rejudge;
        if(!Boolean.getBoolean("studyfix.legacy.sqlite"))for(String owner:repository.legacyOwners()){Identity identity=new Identity(owner,owner,owner,Identity.Role.TEACHER);repository.importLegacyRoster(identity,gateway.examDirectory(identity));}
    }
    private static List<String> ids(Object value){List<String> out=list(value).stream().map(String::valueOf).distinct().toList();if(out.size()>2500)throw new IllegalArgumentException("Danh sách quá lớn.");return out;}
    private static void teacher(Identity who){if(who.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ giảng viên thực hiện thao tác này.");}
    public Map<String,Object> handle(Map<String,Object> req,Identity who)throws Exception {
        String type=text(req,"type",""),id=text(req,"contestId","");Object result;
        switch(type){
            case "EO_SOURCES"->{teacher(who);Map<String,Object> dir=gateway.examDirectory(who);repository.importLegacyRoster(who,dir);Map<String,Object> response=new LinkedHashMap<>(dir);List<Map<String,Object>> definitions=new ArrayList<>();for(int page=1;;page++){Map<String,Object> data=bank.repository.directory(who,mapOf("filterStatus","APPROVED","allVersions",true,"page",page));for(Object v:list(data.get("exams")))definitions.add(map(v));if(page>=integer(data,"totalPages",1))break;}response.put("exams",definitions);result=response;}
            case "EO_LIST"->{if(who.role()==Identity.Role.TEACHER)repository.importLegacyRoster(who,gateway.examDirectory(who));result=repository.directory(who,req);}
            case "EO_HISTORY"->result=history(who,id,req);
            case "EO_PULSE"->result=repository.pulse(who);
            case "EO_GET"->result=detail(who,id,req);
            case "EO_DRAFT_GET"->{teacher(who);result=repository.draft(who,text(req,"draftId",""));}
            case "EO_DRAFT_SAVE"->{teacher(who);result=repository.saveDraft(who,text(req,"draftId",""),integer(req,"expectedRevision",0),normalize(map(req.get("document"))));}
            case "EO_DRAFT_DELETE"->{teacher(who);repository.deleteDraft(who,text(req,"draftId",""),integer(req,"expectedRevision",0));result=mapOf("deleted",true);}
            case "EO_SCHEDULE"->{teacher(who);String draft=text(req,"draftId","");Map<String,Object> existing=repository.scheduledDraft(who,draft);if(!existing.isEmpty())return mapOf("status","OK","data",existing);Map<String,Object> saved=repository.draft(who,draft),doc=map(saved.get("document"));Map<String,Object> definition=bank.repository.approvedForContest(who,text(doc,"examVersionId",""));Map<String,Object> directory=gateway.examDirectory(who);List<Map<String,Object>> classes=selectClasses(directory,ids(doc.get("classIds")));List<Map<String,Object>> students=selectStudents(directory,ids(doc.get("studentIds")),classes.stream().map(c->text(c,"id","")).toList());validate(doc,definition,students);result=repository.schedule(who,draft,integer(req,"expectedRevision",0),definition,students,classes);}
            case "EO_CLONE"->{teacher(who);Map<String,Object> e=repository.require(who,id,true);Map<String,Object> doc=normalize(mapOf("title",text(e,"title","")+" (bản sao)","code","KT_"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),"subject",text(e,"subject",""),"description",text(e,"description",""),"timezone",text(e,"timezone","Asia/Bangkok"),"startAt",System.currentTimeMillis()+3600000,"durationMinutes",Math.max(1,(number(e,"endAt",0)-number(e,"startAt",0))/60000),"examVersionId",text(e,"examVersionId",""),"classIds",list(e.get("classes")).stream().map(v->map(v).get("id")).toList(),"studentIds",repository.candidates(id).stream().map(v->v.get("userId")).toList(),"policy",e.get("policy")));result=repository.saveDraft(who,"",0,doc);}
            case "EO_ROSTER_ADD"->{teacher(who);Map<String,Object> e=repository.require(who,id,true);List<String> classes=list(e.get("classes")).stream().map(v->text(map(v),"id","")).toList();repository.appendCandidates(who,id,selectStudents(gateway.examDirectory(who),ids(req.get("studentIds")),classes));result=mapOf("saved",true);}
            case "EO_CONTROL"->{teacher(who);repository.control(who,id,integer(req,"expectedRevision",0),text(req,"action",""),req);result=mapOf("saved",true);}
            case "EO_CONFIRM","EO_BEGIN","EO_HEARTBEAT","EO_FINISH"->{repository.candidateAction(who,id,type.substring(3));result=mapOf("saved",true);}
            case "EO_NOTIFY"->{repository.notify(who,id,text(req,"message",""),ids(req.get("userIds")));result=mapOf("sent",true);}
            case "EO_READ"->{repository.markRead(who,id);result=mapOf("read",true);}
            case "EO_SUBMISSION"->result=repository.submission(who,id,text(req,"submissionId",""));
            case "EO_ADJUST"->{Object score=req.get("score");if(!(score instanceof Number n))throw new IllegalArgumentException("Điểm không hợp lệ.");repository.adjust(who,id,text(req,"submissionId",""),n.doubleValue(),text(req,"reason",""));result=mapOf("saved",true);}
            case "EO_REJUDGE"->{repository.require(who,id,true);rejudge.run(who,id,ids(req.get("submissionIds")),text(req,"reason",""));result=mapOf("queued",true);}
            case "EO_PUBLISH"->result=repository.publish(who,id,ids(req.get("submissionIds")));
            case "EO_EXPORT"->result=export(who,id,req);
            case "EO_CODE_GET","EO_CODE_SAVE"->result=repository.savedCode(who,id,text(req,"exerciseId",""),text(req,"source",""),type.equals("EO_CODE_SAVE"));
            default->throw new IllegalArgumentException("Thao tác tổ chức thi không hợp lệ.");
        }
        return mapOf("status","OK","requestId",text(req,"requestId",""),"data",result);
    }
    private Map<String,Object> history(Identity who,String id,Map<String,Object> req)throws SQLException {
        Map<String,Object> detail=repository.detail(who,id);if(text(map(detail.get("exam")),"access","").equals("STUDENT"))throw new SecurityException("Chỉ giáo viên tạo kỳ thi được xem sự kiện.");
        Set<Object> visible=new HashSet<>();for(Object v:list(detail.get("candidates")))visible.add(map(v).get("userId"));
        List<Map<String,Object>> all=monitoring.history(id,text(req,"studentId",""),text(req,"eventType",""),number(req,"before",Long.MAX_VALUE));
        return mapOf("events",all.stream().filter(v->visible.contains(v.get("userId"))).toList(),"nextBefore",all.size()==100?all.getLast().get("id"):0);
    }
    private static Map<String,Object> normalize(Map<String,Object> raw){
        Map<String,Object> doc=new LinkedHashMap<>();for(String key:List.of("title","code","subject","description","timezone","examVersionId","distributionMode"))doc.put(key,text(raw,key,switch(key){case "timezone"->"Asia/Bangkok";case "distributionMode"->"ALL";default->"";}).strip());
        if(text(doc,"title","").length()>120||text(doc,"description","").length()>6000||text(doc,"subject","").length()>120||text(doc,"code","").length()>64)throw new IllegalArgumentException("Tên/mã/mô tả vượt giới hạn.");
        doc.put("startAt",number(raw,"startAt",0));doc.put("durationMinutes",integer(raw,"durationMinutes",90));doc.put("problemsPerStudent",integer(raw,"problemsPerStudent",1));doc.put("step",Math.max(1,Math.min(5,integer(raw,"step",1))));doc.put("classIds",ids(raw.get("classIds")));doc.put("studentIds",ids(raw.get("studentIds")));
        Map<String,Object> p=map(raw.get("policy"));doc.put("policy",mapOf("allowResubmit",!Boolean.FALSE.equals(p.get("allowResubmit")),"maxAttempts",integer(p,"maxAttempts",2),"resultRelease",text(p,"resultRelease","PUBLISH"),"showPublicTests",Boolean.TRUE.equals(p.get("showPublicTests")),"lockOutsideTime",!Boolean.FALSE.equals(p.get("lockOutsideTime")),"monitoring",!Boolean.FALSE.equals(p.get("monitoring")),"tabLimit",integer(p,"tabLimit",3),"pasteLimit",integer(p,"pasteLimit",3)));return doc;
    }
    private static List<Map<String,Object>> selectClasses(Map<String,Object> directory,List<String> selected){List<Map<String,Object>> classes=list(directory.get("classes")).stream().map(v->map(v)).filter(c->selected.contains(text(c,"id",""))&&!Boolean.TRUE.equals(c.get("closed"))).toList();if(classes.isEmpty()||classes.size()!=selected.size())throw new SecurityException("Chọn lớp đang mở do bạn quản lý.");return classes;}
    private static List<Map<String,Object>> selectStudents(Map<String,Object> directory,List<String> selected,List<String> classes){
        Map<String,Map<String,Object>> available=new LinkedHashMap<>();for(Object value:list(directory.get("students"))){Map<String,Object> s=map(value);if(classes.contains(text(s,"classId",""))&&selected.contains(text(s,"id","")))available.putIfAbsent(text(s,"id",""),s);}
        if(available.isEmpty()||available.size()!=selected.size())throw new IllegalArgumentException("Danh sách có sinh viên không thuộc lớp được chọn hoặc không hoạt động.");return new ArrayList<>(available.values());
    }
    private static void validate(Map<String,Object> doc,Map<String,Object> definition,List<Map<String,Object>> students){
        if(text(doc,"title","").isBlank()||text(doc,"subject","").isBlank()||!text(doc,"code","").matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw new IllegalArgumentException("Nhập tên, học phần và mã kỳ thi hợp lệ.");
        ZoneId.of(text(doc,"timezone","Asia/Bangkok"));long start=number(doc,"startAt",0),now=System.currentTimeMillis();int duration=integer(doc,"durationMinutes",0);if(start<now-60000||start>now+365L*24*60*60000||duration<1||duration>43200)throw new IllegalArgumentException("Lịch thi phải từ hiện tại đến 1 năm tới; thời lượng 1–43200 phút.");
        Map<String,Object> p=map(doc.get("policy"));if(integer(p,"maxAttempts",0)<1||integer(p,"maxAttempts",0)>100||integer(p,"tabLimit",0)<1||integer(p,"tabLimit",0)>100||integer(p,"pasteLimit",0)<1||integer(p,"pasteLimit",0)>100||!Set.of("IMMEDIATE","AFTER_END","PUBLISH").contains(text(p,"resultRelease","")))throw new IllegalArgumentException("Quy chế hoặc ngưỡng giám sát không hợp lệ.");
        List<Object> items=list(definition.get("items"));String mode=text(doc,"distributionMode","ALL");if(!Set.of("ALL","RANDOM").contains(mode))throw new IllegalArgumentException("Cách phát đề không hợp lệ.");if(mode.equals("RANDOM")){int count=integer(doc,"problemsPerStudent",0);if(count<1||count>items.size())throw new IllegalArgumentException("Số câu ngẫu nhiên không hợp lệ.");if(items.stream().map(v->String.valueOf(map(v).get("points"))).distinct().count()>1)throw new IllegalArgumentException("Phát ngẫu nhiên yêu cầu các câu cùng trọng số để bảo đảm thang điểm công bằng.");}if(students.size()>2500)throw new IllegalArgumentException("Mỗi kỳ thi tối đa 2500 thí sinh.");
    }
    private Map<String,Object> detail(Identity who,String id,Map<String,Object> req)throws SQLException {
        Map<String,Object> data=repository.detail(who,id);Map<String,Object> exam=map(data.get("exam"));String access=text(exam,"access","");
        if(!access.equals("STUDENT")){
            Map<String,Object> monitor=monitoring.monitor(id);Set<Object> visible=new HashSet<>();for(Object v:list(data.get("candidates")))visible.add(map(v).get("userId"));
            monitor.put("students",list(monitor.get("students")).stream().filter(s->visible.contains(map(s).get("userId"))).toList());monitor.put("recentEvents",list(monitor.get("recentEvents")).stream().filter(s->visible.contains(map(s).get("userId"))).toList());data.put("monitor",monitor);
        }
        List<Object> all=list(data.get("submissions"));List<Object> filtered=filteredSubmissions(all,req);
        data.put("resultExercises",all.stream().map(v->text(map(v),"exerciseId","")).distinct().toList());
        int size=Math.max(10,Math.min(50,integer(req,"pageSize",10))),pages=Math.max(1,(filtered.size()+size-1)/size),page=Math.max(1,Math.min(pages,integer(req,"submissionPage",1)));
        data.put("submissionTotal",filtered.size());data.put("submissionPage",page);data.put("submissionPages",pages);data.put("submissions",filtered.subList((page-1)*size,Math.min(page*size,filtered.size())));
        data.put("gradeStats",mapOf("total",all.size(),"graded",all.stream().filter(v->text(map(v),"status","").equals("DONE")).count(),"rejudging",all.stream().filter(v->number(map(v),"rejudges",0)>0&&!text(map(v),"status","").equals("DONE")).count(),"published",publishedCount(who,id,all)));
        if(access.equals("OWNER"))data.put("analytics",analytics(all,list(data.get("candidates"))));return data;
    }
    private static List<Object> filteredSubmissions(List<Object> all,Map<String,Object> req){
        String q=text(req,"submissionQuery","").toLowerCase(Locale.ROOT),verdict=text(req,"verdict",""),status=text(req,"submissionStatus",""),exercise=text(req,"exerciseId","");double min=req.get("minScore") instanceof Number n?n.doubleValue():0,max=req.get("maxScore") instanceof Number n?n.doubleValue():Double.MAX_VALUE;
        return all.stream().filter(v->{var s=map(v);return (q.isBlank()||(text(s,"id","")+" "+text(s,"studentCode","")+" "+text(s,"displayName","")).toLowerCase(Locale.ROOT).contains(q))&&(verdict.isBlank()||verdict.equals(s.get("verdict")))&&(status.isBlank()||status.equals(s.get("status")))&&(exercise.isBlank()||exercise.equals(s.get("exerciseId")))&&(!(s.get("score") instanceof Number n)||n.doubleValue()>=min&&n.doubleValue()<=max);}).toList();
    }
    private long publishedCount(Identity who,String id,List<Object> all)throws SQLException {return who.role()==Identity.Role.TEACHER?countPublished(id,all):all.stream().filter(v->map(v).containsKey("publicationId")).count();}
    private long countPublished(String id,List<Object> all)throws SQLException {Set<Object> result=new HashSet<>();repository.write(c->{for(var p:ExamOrganizationRepository.rows(c,"SELECT rows_json FROM p3_eo_publications WHERE contest_id=?",id))for(Object v:list(parse(text(p,"rows_json","[]"))))result.add(map(v).get("id"));return null;});return result.size();}
    public static Map<String,Object> analytics(List<Object> submissions,List<Object> candidates){
        Map<String,Double> best=new HashMap<>(),totals=new HashMap<>();Map<String,Set<String>> attempted=new LinkedHashMap<>(),accepted=new LinkedHashMap<>();
        for(Object v:submissions){Map<String,Object> s=map(v);if(!text(s,"status","").equals("DONE")||!(s.get("score") instanceof Number n))continue;String uid=text(s,"userId",""),eid=text(s,"exerciseId",""),key=uid+":"+eid;best.merge(key,n.doubleValue(),Math::max);attempted.computeIfAbsent(eid,k->new HashSet<>()).add(uid);if(Set.of("AC","ACCEPTED").contains(text(s,"verdict","")))accepted.computeIfAbsent(eid,k->new HashSet<>()).add(uid);}
        for(var entry:best.entrySet()){String user=entry.getKey().substring(0,entry.getKey().indexOf(':'));totals.merge(user,entry.getValue(),Double::sum);}List<Double> values=new ArrayList<>(totals.values());Collections.sort(values);double mean=values.stream().mapToDouble(Double::doubleValue).average().orElse(0),median=values.isEmpty()?0:values.size()%2==1?values.get(values.size()/2):(values.get(values.size()/2-1)+values.get(values.size()/2))/2;
        List<Map<String,Object>> questions=new ArrayList<>();for(String eid:attempted.keySet())questions.add(mapOf("id",eid,"attempted",attempted.get(eid).size(),"accepted",accepted.getOrDefault(eid,Set.of()).size()));
        return mapOf("scores",values,"studentTotals",totals,"mean",mean,"median",median,"deviation",Math.sqrt(values.stream().mapToDouble(v->(v-mean)*(v-mean)).average().orElse(0)),"questions",questions);
    }
    private static String cell(Object value){String s=String.valueOf(value==null?"":value);if(s.matches("^[=+@-].*"))s="'"+s;return '"'+s.replace("\"","\"\"")+'"';}
    private Map<String,Object> export(Identity who,String id,Map<String,Object> req)throws SQLException {
        String kind=text(req,"kind","roster");List<Map<String,Object>> rows=new ArrayList<>();String[] keys,headers;
        if(kind.equals("exams")){teacher(who);List<String> selected=ids(req.get("ids"));Map<String,Object> query=new LinkedHashMap<>(req);query.put("pageSize",50);for(int page=1;;page++){query.put("page",page);Map<String,Object> data=repository.directory(who,query);for(Object v:list(data.get("exams"))){var e=map(v);if(selected.isEmpty()||selected.contains(text(e,"id","")))rows.add(e);}if(page>=integer(data,"totalPages",1))break;}keys=new String[]{"code","title","subject","status","startAt","endAt","candidateCount"};headers=new String[]{"Mã kỳ thi","Tên kỳ thi","Học phần","Trạng thái","Bắt đầu (UTC ms)","Kết thúc (UTC ms)","Thí sinh"};}
        else{Map<String,Object> detail=repository.detail(who,id);if(text(map(detail.get("exam")),"access","").equals("STUDENT"))throw new SecurityException("Không được xuất danh sách thí sinh.");if(kind.equals("results")){repository.require(who,id,true);List<String> selected=ids(req.get("ids"));rows=filteredSubmissions(new ArrayList<Object>(repository.submissions(id)),req).stream().map(v->map(v)).filter(s->selected.isEmpty()||selected.contains(text(s,"id",""))).toList();keys=new String[]{"id","studentCode","displayName","exerciseId","score","maxScore","verdict","status","submittedAt"};headers=new String[]{"Mã nộp","Mã SV","Họ tên","Câu","Điểm","Điểm tối đa","Kết quả","Trạng thái","Thời điểm nộp (UTC ms)"};}else if(kind.equals("log")){repository.require(who,id,true);rows=list(detail.get("audit")).stream().map(v->map(v)).toList();List<Map<String,Object>> logs=new ArrayList<>(rows);for(long before=Long.MAX_VALUE;;){List<Map<String,Object>> events=monitoring.history(id,"","",before);for(var ev:events)logs.add(mapOf("id","event:"+ev.get("id"),"actor",ev.get("displayName"),"action",ev.get("type"),"detail",text(ev,"source","")+" · "+number(ev,"durationMs",0)+" ms","createdAt",ev.get("receivedAt")));if(events.size()<100)break;before=number(events.getLast(),"id",0);}logs.sort(Comparator.comparingLong(v->-number(v,"createdAt",0)));rows=logs;keys=new String[]{"id","actor","action","detail","createdAt"};headers=new String[]{"Mã","Người thực hiện","Thao tác","Chi tiết","Thời điểm (UTC ms)"};}else{rows=list(detail.get("candidates")).stream().map(v->map(v)).toList();keys=new String[]{"studentCode","displayName","className","confirmedAt"};headers=new String[]{"Mã SV","Họ tên","Lớp","Xác nhận (UTC ms)"};}}
        StringBuilder csv=new StringBuilder("\ufeff");csv.append(Arrays.stream(headers).map(ExamOrganizationService::cell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");for(var row:rows){for(int i=0;i<keys.length;i++){if(i>0)csv.append(',');csv.append(cell(row.get(keys[i])));}csv.append("\r\n");}return mapOf("filename","studyfix-"+kind+".csv","base64",Base64.getEncoder().encodeToString(csv.toString().getBytes(StandardCharsets.UTF_8)),"contentType","text/csv;charset=utf-8");
    }
}
