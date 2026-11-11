package studyfix.assignment.service;

import studyfix.Identity;
import studyfix.StudyFixGateway;
import studyfix.assignment.catalog.SeedExerciseCatalog;
import studyfix.assignment.grading.DockerJavaSandbox;
import studyfix.assignment.grading.GradingSandbox;
import studyfix.assignment.integration.GatewaySharedRoomCodeProvider;
import studyfix.assignment.integration.SharedRoomCodeProvider;
import studyfix.assignment.model.*;
import studyfix.assignment.persistence.AssignmentRepository;
import studyfix.assignment.persistence.PublicTestVisibility;
import studyfix.assignment.persistence.PracticeRepository;
import studyfix.assignment.persistence.QuestionWeekRepository;
import studyfix.assignment.persistence.QuestionBankRepository;
import studyfix.assignment.persistence.ContestDistributionRepository;
import studyfix.assignment.persistence.ExamMonitoringRepository;
import studyfix.assignment.protocol.AssignmentJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Xử lý nghiệp vụ và quyền truy cập của module bài tập, dùng các lớp Server và Store hiện có. */
public final class AssignmentService implements AutoCloseable {
 private final studyfix.assignment.persistence.OjRepository oj;
 private final OjImportService ojImports;
 private final PracticeRepository practice;
 private final QuestionBankRepository bank;
 private final ProblemBuilderService builder;
 private final ExamBankService examBank;
 private final ExamOrganizationService organization;
 public ExamBankService examBank(){return examBank;}
 public Map<String,Object> studentSubmission(Identity who,String id)throws SQLException{return new studyfix.assignment.persistence.StudentPortalRepository(repo).submission(who,id);}
 public Map<String,Object> studentPortal(Identity who,List<String> rooms,List<Map<String,Object>> people,Map<String,Object> request)throws SQLException{return new studyfix.assignment.persistence.StudentPortalRepository(repo).view(who,rooms,people,request);}
 public Map<String,Object> teacherDashboard(Identity who,List<String> rooms,List<String> students,List<Map<String,Object>> members,Map<String,Object> request)throws SQLException{return new studyfix.assignment.persistence.TeacherDashboardRepository(repo).view(who,rooms,students,members,request);}
 private final WeekAssignmentService weekAssignments;
 private final QuestionWeekRepository weeks;
 private final ExamMonitoringRepository exams;
 private final ExamMonitoringRepository branchExams;
 private final AtomicInteger pendingResultWrites = new AtomicInteger();
 public int pendingResultWrites(){return pendingResultWrites.get();}
 private final StudyFixGateway gateway; private final AssignmentRepository repo; private final BlockingQueue<GradingTask> queue; private final GradingSandbox sandbox; private final Thread[] workers; private final AtomicBoolean closed=new AtomicBoolean(); private final SharedRoomCodeProvider sharedCodeProvider; private final AtomicInteger activeTasks=new AtomicInteger(); private final int queueCapacity; private final String dockerImage;
 public AssignmentService(StudyFixGateway gateway,Path database,int queueSize,int workerCount,String dockerImage)throws Exception{
  this.gateway=Objects.requireNonNull(gateway);sharedCodeProvider=new GatewaySharedRoomCodeProvider(gateway);repo=new AssignmentRepository(database);oj=new studyfix.assignment.persistence.OjRepository(repo);ojImports=new OjImportService(oj);exams=new ExamMonitoringRepository(repo);branchExams=new ExamMonitoringRepository(repo,true);if(!oj.active())repo.seed(SeedExerciseCatalog.all());this.queueCapacity=Math.max(1,queueSize);this.dockerImage=Objects.requireNonNull(dockerImage);queue=new ArrayBlockingQueue<>(this.queueCapacity);sandbox=new DockerJavaSandbox(dockerImage,2,45);
  for(String pending:repo.recoverQueuedSubmissions()){if(!queue.offer(new GradingTask(pending,TaskKind.PERSONAL)))repo.saveResult(pending,new GradingResult("INTERNAL_ERROR",0,100,0,0,"Không thể khôi phục: hàng đợi chấm đầy khi server khởi động.",0,List.of()));}
  for(String pending:repo.recoverQueuedSharedChecks()){if(!queue.offer(new GradingTask(pending,TaskKind.SHARED)))repo.saveSharedCheckResult(pending,"INTERNAL_ERROR","INTERNAL_ERROR",0,100,0,0,"Không thể khôi phục: hàng đợi kiểm tra code chung đầy khi server khởi động.",0);}
  for(String pending:repo.recoverQueuedContestSubmissions()){if(!queue.offer(new GradingTask(pending,TaskKind.CONTEST)))repo.saveContestSubmissionResult(pending,new GradingResult("INTERNAL_ERROR",0,repo.getContestSubmission(pending).maxScore(),0,0,"Không thể khôi phục: hàng đợi bài thi đầy khi server khởi động.",0,List.of()));}
  for(String pending:repo.recoverQueuedTeacherTrials()){if(!queue.offer(new GradingTask(pending,TaskKind.TRIAL)))repo.saveTeacherTrialResult(pending,new GradingResult("INTERNAL_ERROR",0,100,0,0,"Không thể khôi phục: hàng đợi lượt thử của giáo viên đầy khi server khởi động.",0,List.of()));}
  practice=new PracticeRepository(database);weeks=new QuestionWeekRepository(repo);bank=new QuestionBankRepository(repo);builder=new ProblemBuilderService(repo,bank,dockerImage,gateway::problemReviewers);examBank=new ExamBankService(repo);organization=new ExamOrganizationService(repo,gateway,examBank,exams,this::rejudgeOrganization);
  weekAssignments=new WeekAssignmentService(repo,gateway,id->queue.offer(new GradingTask(id,TaskKind.WEEK)));
  for(String id:weekAssignments.repository.recover())if(!queue.offer(new GradingTask(id,TaskKind.WEEK)))weekAssignments.repository.finish(id,new GradingResult("INTERNAL_ERROR",0,100,0,0,"Hàng đợi đầy khi khôi phục bài theo Week.",0,List.of()));
  for(String pending:practice.recover())if(!queue.offer(new GradingTask(pending,TaskKind.PRACTICE)))practice.finish(pending,new GradingResult("INTERNAL_ERROR",0,100,0,0,"Hàng đợi đầy khi khôi phục lượt luyện tập.",0,List.of()));
  workers=new Thread[Math.max(1,workerCount)];for(int i=0;i<workers.length;i++){workers[i]=new Thread(this::workLoop,"studyfix-grader-"+(i+1));workers[i].setDaemon(true);workers[i].start();}
 }
 public Map<String,Object> handle(Map<String,Object> req){String requestId=AssignmentJson.text(req,"requestId",UUID.randomUUID().toString());try{
  String type=AssignmentJson.text(req,"type","");String sessionId=AssignmentJson.text(req,"sessionId","");String roomId=AssignmentJson.text(req,"roomId","");if(sessionId.isBlank())throw new AccessFailure("UNAUTHENTICATED","Thiếu session đăng nhập.");Identity who;try{who=gateway.identity(sessionId);}catch(SecurityException e){throw new AccessFailure("UNAUTHENTICATED","Phiên StudyFix đã hết hạn hoặc chưa đăng nhập.");}
  if(type.equals("OJ_PUBLISH_BATCH")){if(who.role()!=Identity.Role.TEACHER)throw new AccessFailure("FORBIDDEN","Teacher only");List<String> ids=AssignmentJson.list(req.get("exerciseIds")).stream().map(String::valueOf).toList();oj.publish(who,ids);return ok(requestId,AssignmentJson.mapOf("published",ids.size()));}
  if(type.equals("OJ_STATUS")||type.equals("OJ_PUBLISH")){if(who.role()!=Identity.Role.TEACHER)throw new AccessFailure("FORBIDDEN","Teacher only");String id=AssignmentJson.text(req,"exerciseId","");bank.info(id,who);if(type.equals("OJ_PUBLISH"))oj.publish(who,id);return ok(requestId,AssignmentJson.mapOf("validation",oj.validation(id)));}
  if(type.startsWith("WA_"))return weekAssignments.handle(req,who);
  if(type.startsWith("EO_"))return organization.handle(req,who);
  if(type.startsWith("EXAMBANK_"))return examBank.handle(req,who);
  if(type.startsWith("BUILDER_"))return builder.handle(req,who);
  if(type.equals("PING"))return ok(requestId,AssignmentJson.mapOf("serverTime",System.currentTimeMillis(),"username",who.username()));
  if(type.startsWith("PRACTICE_"))return handlePractice(requestId,type,req,who);
  if(type.equals("CATALOG_CREATE")||type.equals("BANK_UPDATE"))return createCatalogExercise(requestId,req,who);
  if(type.startsWith("BANK_"))return handleBank(requestId,type,req,who);
  String contextContest=AssignmentJson.text(req,"contestId","");
  if(type.equals("CONTEST_SUBMISSION_RESULT")){var record=repo.getContestSubmission(AssignmentJson.text(req,"submissionId",""));if(record!=null)contextContest=record.contestId();}
  boolean organizationContext=false;Map<String,Object> organizationExam=null;
  if(!contextContest.isBlank()&&(type.startsWith("CONTEST_")||type.startsWith("EXAM_")||type.equals("LEADERBOARD_GET"))){
   if(organization.repository.managed(contextContest)){organizationExam=organization.repository.require(who,contextContest,false);organizationContext=true;}
   else try{organizationExam=organization.repository.require(who,contextContest,false);organizationContext=true;}catch(SecurityException|IllegalArgumentException ignored){}
  }
  boolean teacherOwner=false;
  if(organizationContext){
   roomId=AssignmentJson.text(organizationExam,"roomId","");teacherOwner="OWNER".equals(organizationExam.get("access"));
   if(type.equals("CONTEST_PROBLEMS_GET"))organization.repository.checkRead(who,contextContest);
  }else{
   if(roomId.isBlank()||!gateway.isMember(sessionId,roomId))throw new AccessFailure("NOT_MEMBER","Bạn chưa ở trong phòng này.");
   try{teacherOwner=gateway.isTeacher(sessionId,roomId);}catch(SecurityException ignored){}
  }
  if(type.startsWith("EXAM_")&&!type.equals("EXAM_SNAPSHOT")){String cid=AssignmentJson.text(req,"contestId","");if(who.role()==Identity.Role.STUDENT&&!cid.startsWith("classroom:")&&!ContestDistributionRepository.canAccess(repo,cid,who.userId()))throw new AccessFailure("FORBIDDEN","Bạn chưa được giao kỳ thi này.");}
  if(type.startsWith("EXAM_"))return handleExam(requestId,type,req,who,sessionId,roomId,teacherOwner);
  switch(type){
   case "CATALOG_LIST" -> {requireOwner(teacherOwner);return ok(requestId,AssignmentJson.mapOf("exercises",repo.listExercises(AssignmentJson.text(req,"query",""),AssignmentJson.text(req,"topic",""),AssignmentJson.text(req,"mode",""))));}
   case "CATALOG_GET" -> {requireOwner(teacherOwner);bank.info(AssignmentJson.text(req,"exerciseId",""),who);Exercise e=repo.getExercise(AssignmentJson.text(req,"exerciseId",""));if(e==null)throw new AccessFailure("NOT_FOUND","Không tìm thấy bài tập.");Map<String,Object> d=new LinkedHashMap<>(e.publicView());List<Map<String,Object>> pub=new ArrayList<>();for(int i=0;i<e.tests().size();i++){Exercise.TestCase t=e.tests().get(i);if(!t.hidden())pub.add(AssignmentJson.mapOf("index",i+1,"input",t.input(),"expectedOutput",t.expected(),"weight",t.weight()));}d.put("publicTests",pub);return ok(requestId,AssignmentJson.mapOf("exercise",d));}
   case "ROOM_STUDENTS_LIST" -> {
    requireOwner(teacherOwner);List<Map<String,Object>> students=new ArrayList<>();
    for(Identity member:currentRoomStudents(sessionId,roomId))students.add(AssignmentJson.mapOf("username",member.username(),"displayName",member.displayName()));
    return ok(requestId,AssignmentJson.mapOf("students",students));
   }
   case "CLASS_PROGRESS" -> {requireOwner(teacherOwner);return ok(requestId,AssignmentJson.mapOf("assignments",classProgress(sessionId,roomId,who)));}
   case "ASSIGN_CREATE" -> {
    requireOwner(teacherOwner);
    String exerciseId=AssignmentJson.text(req,"exerciseId","");
    bank.requirePublished(exerciseId);bank.info(exerciseId,who);Exercise selected=repo.getExercise(exerciseId);
    if(selected==null)throw new AccessFailure("NOT_FOUND","Bài trong ngân hàng không tồn tại.");
    String scope=AssignmentJson.text(req,"scope","ROOM").toUpperCase(Locale.ROOT);
    if(!scope.equals("ROOM")&&!scope.equals("INDIVIDUAL"))throw new AccessFailure("INVALID_REQUEST","scope phải là ROOM hoặc INDIVIDUAL.");
    if(selected.mode()==ExerciseMode.DEBUG_COLLAB&&!scope.equals("ROOM"))throw new AccessFailure("INVALID_REQUEST","Bài sửa lỗi cộng tác phải được giao cho cả phòng (ROOM).");
    String targetId=null,targetUsername=null;
    Identity targetIdentity=null;
    if(scope.equals("INDIVIDUAL")){
     targetUsername=AssignmentJson.text(req,"targetUsername","").trim();
     if(targetUsername.isBlank())throw new AccessFailure("INVALID_REQUEST","Nhập username học sinh nhận bài.");
     targetIdentity=findRoomStudent(sessionId,roomId,targetUsername);targetId=targetIdentity.userId();targetUsername=targetIdentity.username();
    }
    long now=System.currentTimeMillis();int deadlineMinutes=AssignmentJson.integer(req,"deadlineMinutes",0);
    if(deadlineMinutes<0||deadlineMinutes>43200)throw new AccessFailure("INVALID_REQUEST","Hạn nộp phải từ 0 đến 43200 phút.");
    long deadline=deadlineMinutes==0?0:now+deadlineMinutes*60_000L;
    boolean starterLoaded=false;String starterLoadMessage="";long starterVersion=-1;
    SharedRoomCodeProvider.Snapshot starterSnapshot=null;
    boolean shouldAutoLoadStarter=false;
    if(selected.mode()==ExerciseMode.DEBUG_COLLAB){
     try {
      starterSnapshot=sharedCodeProvider.snapshot(sessionId,roomId);
      String previousStarter=repo.latestRoomDebugStarterCode(roomId);
      boolean knownStarter=starterSnapshot.version()==0 || (previousStarter!=null && previousStarter.equals(starterSnapshot.source()));
      boolean safeState=!starterSnapshot.draftActive()&&!starterSnapshot.editTurnActive();
      shouldAutoLoadStarter=knownStarter&&safeState;
      if(!shouldAutoLoadStarter){
       starterLoadMessage=starterSnapshot.draftActive()
               ? "Bài đã giao nhưng phòng đang có bản nháp; chưa tự thay code để tránh mất nội dung."
               : starterSnapshot.editTurnActive()
               ? "Bài đã giao nhưng đang có người giữ quyền sửa; chưa tự thay code để tránh xung đột."
               : "Bài đã giao nhưng code phòng đã khác starter gần nhất; cần giáo viên xác nhận trước khi thay.";
      }
     } catch(RuntimeException ex) {
      starterSnapshot=null;
      starterLoadMessage="Bài đã giao, nhưng không thể đọc trạng thái code an toàn. Giáo viên cần kiểm tra trước khi nạp code.";
     }
    }
    AssignmentRecord a=new AssignmentRecord(UUID.randomUUID().toString(),exerciseId,roomId,who.userId(),scope,targetId,targetUsername,now,deadline,"OPEN",selected.mode());
    // Lưu danh sách người nhận ngay lúc giao bài để người vào phòng sau
    // không làm sai báo cáo cũ. Bài giao cũ chưa lưu danh sách vẫn dùng cách tra trước đây.
    List<AssignmentRepository.AssignmentRecipient> recipientSnapshot=new ArrayList<>();
    if(scope.equals("ROOM")) {
     for(Identity recipient:currentRoomStudents(sessionId,roomId)) recipientSnapshot.add(new AssignmentRepository.AssignmentRecipient(recipient.userId(),recipient.username(),recipient.displayName()));
    } else if(targetIdentity!=null) {
     recipientSnapshot.add(new AssignmentRepository.AssignmentRecipient(targetIdentity.userId(),targetIdentity.username(),targetIdentity.displayName()));
    }
    // Lưu bài giao và danh sách người nhận trong cùng giao dịch trước khi đổi code chung. Nếu nạp code mẫu
    // bị xung đột hoặc lỗi thì bài giao vẫn được giữ và trả về cảnh báo để người dùng xử lý.
    repo.createAssignment(a,recipientSnapshot);
    if(shouldAutoLoadStarter&&starterSnapshot!=null){
     try {
      starterVersion=sharedCodeProvider.loadStarterCode(sessionId,roomId,starterSnapshot.version(),selected.starterCode(),selected.title());
      starterLoaded=true;starterLoadMessage="Code lỗi mẫu đã được tự nạp an toàn vào code cộng tác.";
     } catch(RuntimeException ex) {
      starterLoadMessage="Bài đã giao, nhưng trạng thái code đã thay đổi hoặc không thể nạp starter. Code hiện tại được giữ nguyên; giáo viên có thể kiểm tra rồi xác nhận nạp lại.";
     }
    }
    return ok(requestId,AssignmentJson.mapOf("assignment",assignmentMap(a),"starterLoaded",starterLoaded,
            "starterLoadMessage",starterLoadMessage,"roomCodeVersion",starterVersion,"message","Đã giao bài."));
   }
   case "ASSIGN_LIST" -> {List<Map<String,Object>> rows=new ArrayList<>();for(AssignmentRecord a:repo.listAssignments(roomId,who.userId(),teacherOwner)){Map<String,Object> row=new LinkedHashMap<>(assignmentMap(a));Exercise e=repo.getExercise(a.exerciseId());row.put("exerciseTitle",e==null?a.exerciseId():e.title());row.put("acceptedByMe",who.role()==Identity.Role.STUDENT&&repo.hasAcceptedSubmission(a.id(),who.userId()));rows.add(row);}return ok(requestId,AssignmentJson.mapOf("assignments",rows));}
   case "ASSIGN_GET" -> {AssignmentRecord a=requireAssignmentVisible(AssignmentJson.text(req,"assignmentId",""),roomId,who,teacherOwner);Exercise e=repo.getExercise(a.exerciseId());Map<String,Object> d=new LinkedHashMap<>(assignmentMap(a));d.put("exercise",e.publicView());List<Map<String,Object>> pub=new ArrayList<>();int idx=0;for(Exercise.TestCase t:e.tests()){idx++;if(!t.hidden())pub.add(AssignmentJson.mapOf("index",idx,"input",t.input(),"expectedOutput",t.expected(),"weight",t.weight()));}d.put("publicTests",pub);return ok(requestId,AssignmentJson.mapOf("assignment",d));}
   case "DEBUG_STARTER_PREPARE" -> {requireOwner(teacherOwner);AssignmentRecord a=requireAssignmentVisible(AssignmentJson.text(req,"assignmentId",""),roomId,who,teacherOwner);Exercise e=repo.getExercise(a.exerciseId());requireDebug(a,e);SharedRoomCodeProvider.Snapshot snap=sharedCodeProvider.snapshot(sessionId,roomId);return ok(requestId,AssignmentJson.mapOf("assignmentId",a.id(),"exerciseId",e.id(),"title",e.title(),"mode",e.mode().name(),"starterCode",e.starterCode(),"roomCodeVersion",snap.version(),"draftActive",snap.draftActive(),"editTurnActive",snap.editTurnActive(),"codeHash",sha256(snap.source())));}
   case "DEBUG_STARTER_LOAD" -> {requireOwner(teacherOwner);AssignmentRecord a=requireAssignmentVisible(AssignmentJson.text(req,"assignmentId",""),roomId,who,teacherOwner);if(!a.createdByUserId().equals(who.userId()))throw new AccessFailure("FORBIDDEN","Chỉ giáo viên đã giao bài này mới được nạp code mẫu.");Exercise e=repo.getExercise(a.exerciseId());requireDebug(a,e);long expected=AssignmentJson.number(req,"expectedVersion",-1);long loaded;try{loaded=sharedCodeProvider.loadStarterCode(sessionId,roomId,expected,e.starterCode(),e.title());}catch(SecurityException|IllegalStateException|IllegalArgumentException ex){throw new AccessFailure("STARTER_LOAD_REJECTED",ex.getMessage());}return ok(requestId,AssignmentJson.mapOf("assignmentId",a.id(),"roomCodeVersion",loaded,"message","Đã nạp code lỗi mẫu thành công."));}
   case "SHARED_CODE_STATE" -> {AssignmentRecord a=requireAssignmentVisible(AssignmentJson.text(req,"assignmentId",""),roomId,who,teacherOwner);Exercise e=repo.getExercise(a.exerciseId());requireDebug(a,e);SharedRoomCodeProvider.Snapshot snap=sharedCodeProvider.snapshot(sessionId,roomId);return ok(requestId,AssignmentJson.mapOf("assignmentId",a.id(),"roomCodeVersion",snap.version(),"draftActive",snap.draftActive(),"editTurnActive",snap.editTurnActive(),"sourceHash",sha256(snap.source()),"sourceEmpty",snap.source()==null||snap.source().isBlank()));}
   case "SHARED_CHECK_CREATE" -> {return createSharedCheck(requestId,req,sessionId,roomId,who,teacherOwner);}
   case "SHARED_CHECK_LATEST" -> {AssignmentRecord a=requireAssignmentVisible(AssignmentJson.text(req,"assignmentId",""),roomId,who,teacherOwner);Exercise e=repo.getExercise(a.exerciseId());requireDebug(a,e);SharedCheckRun run=repo.latestSharedCheck(a.id(),roomId);return ok(requestId,AssignmentJson.mapOf("checkRun",run==null?Map.of():sharedCheckMap(run)));}
   case "SHARED_CHECK_STATUS", "SHARED_CHECK_RESULT" -> {SharedCheckRun run=repo.getSharedCheck(AssignmentJson.text(req,"checkRunId",""));if(run==null)throw new AccessFailure("NOT_FOUND","Không tìm thấy lượt kiểm tra code chung.");AssignmentRecord a=requireAssignmentVisible(run.assignmentId(),roomId,who,teacherOwner);requireDebug(a,repo.getExercise(a.exerciseId()));if(!roomId.equals(run.roomId()))throw new AccessFailure("NOT_FOUND","Lượt kiểm tra không thuộc phòng này.");return ok(requestId,AssignmentJson.mapOf("checkRun",sharedCheckMap(run)));}
   case "SUBMIT_CREATE" -> {return submit(requestId,req,sessionId,roomId,who,teacherOwner);}
   case "TEACHER_TRIAL_CREATE" -> {return createTeacherTrial(requestId,req,roomId,who,teacherOwner);}
   case "TEACHER_TRIAL_LIST" -> {requireOwner(teacherOwner);List<Map<String,Object>> trials=new ArrayList<>();for(TeacherTrialRecord trial:repo.listTeacherTrials(roomId,AssignmentJson.text(req,"exerciseId",""),who.userId()))trials.add(teacherTrialMap(trial));return ok(requestId,AssignmentJson.mapOf("trials",trials));}
   case "TEACHER_TRIAL_RESULT" -> {requireOwner(teacherOwner);TeacherTrialRecord trial=repo.getTeacherTrial(AssignmentJson.text(req,"trialId",""));if(trial==null||!roomId.equals(trial.roomId())||!who.userId().equals(trial.teacherUserId()))throw new AccessFailure("NOT_FOUND","Không tìm thấy lượt thử của giáo viên.");Map<String,Object> result=new LinkedHashMap<>(teacherTrialMap(trial));result.put("testResults",repo.teacherTrialTestResults(trial.id()));result.put("structureResults",repo.structureResults(trial.id()));return ok(requestId,AssignmentJson.mapOf("trial",result));}
   case "SUBMISSION_LIST" -> {String aid=AssignmentJson.text(req,"assignmentId","");AssignmentRecord a=requireAssignmentVisible(aid,roomId,who,teacherOwner);List<Map<String,Object>> list=new ArrayList<>();for(SubmissionRecord s:repo.listSubmissions(aid,who.userId(),teacherOwner))list.add(submissionMap(s,false));return ok(requestId,AssignmentJson.mapOf("submissions",list));}
   case "SUBMISSION_RESULT" -> {SubmissionRecord s=repo.getSubmission(AssignmentJson.text(req,"submissionId",""));if(s==null)throw new AccessFailure("NOT_FOUND","Không tìm thấy bài nộp.");AssignmentRecord a=requireAssignmentVisible(s.assignmentId(),roomId,who,teacherOwner);if(!teacherOwner&&!s.userId().equals(who.userId()))throw new AccessFailure("FORBIDDEN","Chỉ xem được kết quả của chính mình.");Map<String,Object>d=new LinkedHashMap<>(submissionMap(s,true));d.put("testResults",repo.publicTestResults(s.id()));d.put("structureResults",repo.structureResults(s.id()));return ok(requestId,AssignmentJson.mapOf("submission",d));}
   case "CONTEST_RECIPIENTS" -> {requireOwner(teacherOwner);String cid=AssignmentJson.text(req,"contestId","");Map<String,Object> c=repo.contest(cid);if(c==null||!roomId.equals(c.get("roomId")))throw new AccessFailure("NOT_FOUND","Không tìm thấy kỳ thi trong lớp.");List<Map<String,Object>> students=new ArrayList<>();for(Identity i:gateway.contestStudents(sessionId,roomId))students.add(AssignmentJson.mapOf("userId",i.userId(),"username",i.username(),"displayName",i.displayName()));return ok(requestId,AssignmentJson.mapOf("students",students,"draws",ContestDistributionRepository.draws(repo,cid)));}
   case "CONTEST_DISTRIBUTE" -> {return distributeContest(requestId,req,who,sessionId,roomId,teacherOwner);}
   case "CONTEST_CREATE" -> {return createContest(requestId,req,roomId,who,teacherOwner);}
   case "CONTEST_LIST" -> {return ok(requestId,AssignmentJson.mapOf("contests",visibleContests(roomId,who,teacherOwner)));}
   case "CONTEST_PROBLEMS_GET" -> {String cid=AssignmentJson.text(req,"contestId","");if(!teacherOwner&&!ContestDistributionRepository.canAccess(repo,cid,who.userId()))throw new AccessFailure("FORBIDDEN","Bạn chưa được giao kỳ thi này.");Map<String,Object> c=repo.contest(cid);if(c==null||!roomId.equals(c.get("roomId")))throw new AccessFailure("NOT_FOUND","Không tìm thấy kỳ thi trong phòng.");if(!teacherOwner&&organization.repository.managed(cid)){c=new LinkedHashMap<>(c);c.put("endAt",organization.repository.deadline(who,cid,AssignmentJson.number(c,"endAt",0)));}List<Map<String,Object>> problems=new ArrayList<>();for(String exerciseId:teacherOwner?repo.contestExerciseIds(cid):ContestDistributionRepository.studentProblems(repo,cid,who)){Exercise e=repo.getExercise(exerciseId);if(e==null)continue;Map<String,Object> problem=new LinkedHashMap<>(e.publicView());List<Map<String,Object>> pub=new ArrayList<>();int idx=0;for(Exercise.TestCase t:e.tests()){idx++;if(!t.hidden())pub.add(AssignmentJson.mapOf("index",idx,"input",t.input(),"expectedOutput",t.expected(),"weight",t.weight()));}problem.put("publicTests",pub);problem.put("maxScore",repo.contestMaxScore(cid,exerciseId));if(!teacherOwner&&Boolean.FALSE.equals(examBank.repository.contestPolicy(cid).get("allowTemplate")))problem.put("template","");problem.put("acceptedByMe",who.role()==Identity.Role.STUDENT&&!organization.repository.managed(cid)&&repo.hasAcceptedContestSubmission(cid,exerciseId,who.userId()));problems.add(problem);}return ok(requestId,AssignmentJson.mapOf("contest",c,"problems",problems,"draws",teacherOwner?ContestDistributionRepository.draws(repo,cid):List.of()));}
   case "CONTEST_SUBMIT" -> {return submitContest(requestId,req,roomId,who,teacherOwner);}
   case "CONTEST_SUBMISSION_RESULT" -> {ContestSubmissionRecord s=repo.getContestSubmission(AssignmentJson.text(req,"submissionId",""));if(s==null||!roomId.equals(s.roomId()))throw new AccessFailure("NOT_FOUND","Không tìm thấy bài nộp trong phòng.");Map<String,Object> c=repo.contest(s.contestId());if(c==null||!roomId.equals(c.get("roomId")))throw new AccessFailure("NOT_FOUND","Kỳ thi không thuộc phòng hiện tại.");if(!teacherOwner&&!s.userId().equals(who.userId()))throw new AccessFailure("FORBIDDEN","Chỉ xem được kết quả bài thi của chính mình.");Map<String,Object> d=new LinkedHashMap<>(contestSubmissionView(s,teacherOwner));if(teacherOwner)d.put("testResults",repo.contestSubmissionTestResults(s.id()));return ok(requestId,AssignmentJson.mapOf("submission",d));}
   case "CONTEST_SUBMISSION_LIST" -> {String cid=AssignmentJson.text(req,"contestId","");String eid=AssignmentJson.text(req,"exerciseId","");Map<String,Object> c=repo.contest(cid);if(c==null||!roomId.equals(c.get("roomId"))||!repo.contestHasExercise(cid,eid))throw new AccessFailure("NOT_FOUND","Không tìm thấy bài trong kỳ thi.");List<Map<String,Object>> items=new ArrayList<>();for(ContestSubmissionRecord item:repo.listContestSubmissions(cid,eid,who.userId(),teacherOwner))items.add(contestSubmissionView(item,teacherOwner));return ok(requestId,AssignmentJson.mapOf("submissions",items));}
   case "LEADERBOARD_GET" -> {requireOwner(teacherOwner);String cid=AssignmentJson.text(req,"contestId","");Map<String,Object> c=repo.contest(cid);if(c==null||!roomId.equals(c.get("roomId")))throw new AccessFailure("NOT_FOUND","Không tìm thấy kỳ thi trong phòng.");return ok(requestId,AssignmentJson.mapOf("contest",c,"entries",repo.leaderboard(cid)));}
   default -> throw new AccessFailure("INVALID_REQUEST","Loại yêu cầu không được hỗ trợ: "+type);
  }
 }catch(AccessFailure e){return error(requestId,e.code,e.getMessage());}catch(SecurityException e){return error(requestId,"FORBIDDEN",e.getMessage());}catch(IllegalArgumentException e){return error(requestId,"INVALID_REQUEST",e.getMessage());}catch(SQLException e){return error(requestId,"STORAGE_ERROR","Lỗi lưu trữ bài tập: "+safe(e.getMessage()));}catch(Exception e){return error(requestId,"INTERNAL_ERROR","Yêu cầu chưa xử lý được: "+safe(e.getMessage()));}}
 private Map<String,Object> handleExam(String requestId,String type,Map<String,Object> req,Identity who,String session,String room,boolean owner)throws SQLException{
  try{
   if(type.equals("EXAM_SNAPSHOT")){
    List<Map<String,Object>> modes=new ArrayList<>();Map<String,Object> monitors=new LinkedHashMap<>();
    for(Map<String,Object> c:visibleContests(room,who,owner)){
     String id=AssignmentJson.text(c,"id","");Map<String,Object> mode=new LinkedHashMap<>(exams.mode(id));long now=System.currentTimeMillis();
     mode.putAll(AssignmentJson.mapOf("startAt",c.get("startAt"),"endAt",c.get("endAt"),"live",now>=AssignmentJson.number(c,"startAt",Long.MAX_VALUE)&&now<AssignmentJson.number(c,"endAt",0),"title",c.get("title")));
     modes.add(mode);if(owner&&Boolean.TRUE.equals(mode.get("enabled")))monitors.put(id,exams.monitor(id));
    }
    Map<String,Object> branch=gateway.classroomMonitoringContext(session,room);String bid=AssignmentJson.text(branch,"id","");
    Map<String,Object> bm=new LinkedHashMap<>(branchExams.mode(bid));bm.putAll(AssignmentJson.mapOf("startAt",0L,"endAt",branch.get("endAt"),"live","BRANCHING".equals(branch.get("phase"))));
    modes.add(bm);if(owner&&Boolean.TRUE.equals(bm.get("enabled")))monitors.put(bid,branchExams.monitor(bid));
    return ok(requestId,AssignmentJson.mapOf("modes",modes,"monitors",monitors));
   }
   String id=AssignmentJson.text(req,"contestId","");boolean branch=id.startsWith("classroom:");
   Map<String,Object> c=branch?gateway.classroomMonitoringContext(session,room):repo.contest(id);
   if(branch&&!id.equals(c.get("id")))throw new AccessFailure("NOT_FOUND","Classroom round has changed.");
   ExamMonitoringRepository monitoring=branch?branchExams:exams;
   if(c==null||!room.equals(c.get("roomId")))throw new AccessFailure("NOT_FOUND","Không tìm thấy kỳ thi trong phòng đang tham gia.");
   Map<String,Object> result;
   if(!branch&&organization.repository.managed(id)){c=new LinkedHashMap<>(c);c.put("endAt",organization.repository.deadline(who,id,AssignmentJson.number(c,"endAt",0)));Map<String,Object> e=organization.repository.require(who,id,false);if(Boolean.TRUE.equals(e.get("locked"))||Set.of("POSTPONED","CANCELLED","FINISHED").contains(AssignmentJson.text(e,"state","")))c.put("endAt",0L);}
   switch(type){
    case "EXAM_CONFIG"->{requireOwner(owner);Object enabled=req.get("enabled");if(!(enabled instanceof Boolean))throw new IllegalArgumentException("enabled phải là boolean.");result=monitoring.configure(id,(Boolean)enabled,AssignmentJson.integer(req,"tabLimit",3),AssignmentJson.integer(req,"pasteLimit",3));}
    case "EXAM_STATUS"->result=monitoring.status(c,who,session);
    case "EXAM_EVENT"->result=monitoring.event(c,who,session,req);
    case "EXAM_MONITOR"->{requireOwner(owner);result=monitoring.monitor(id);}
    case "EXAM_HISTORY"->{requireOwner(owner);result=AssignmentJson.mapOf("events",monitoring.history(id,AssignmentJson.text(req,"studentId",""),AssignmentJson.text(req,"eventType",""),AssignmentJson.number(req,"before",Long.MAX_VALUE)));}
    default->throw new AccessFailure("INVALID_REQUEST","Thao tác Exam Mode không hợp lệ.");
   }return ok(requestId,result);
  }catch(ExamMonitoringRepository.Failure e){throw new AccessFailure(e.code,e.getMessage());}
 }
 private Map<String,Object> createCatalogExercise(String requestId,Map<String,Object> req,Identity who)throws Exception {
  if(who.role()!=Identity.Role.TEACHER)throw new AccessFailure("FORBIDDEN","Chỉ giáo viên được thêm bài vào ngân hàng.");
  String id=catalogText(req,"id",64,true).trim();
  if("BANK_UPDATE".equals(req.get("type"))&&oj.config(id)!=null)throw new AccessFailure("IMMUTABLE_OJ","Đề OJ cần nhập phiên bản gói mới để giữ đúng hợp đồng chấm và kiểm định lại.");
  if(!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw new IllegalArgumentException("Mã bài chỉ gồm chữ, số, dấu _ và -, tối đa 64 ký tự.");
  String title=catalogText(req,"title",200,true),topic=catalogText(req,"topic",100,true),difficulty=catalogText(req,"difficulty",30,true);
  if(!Set.of("Dễ","Trung bình","Khó").contains(difficulty))throw new IllegalArgumentException("Độ khó không hợp lệ.");
  ExerciseMode mode=ExerciseMode.parse(catalogText(req,"mode",30,true));
  String statement=catalogText(req,"statement",12000,true),input=catalogText(req,"inputFormat",4000,true),output=catalogText(req,"outputFormat",4000,true);
  String constraints=catalogText(req,"constraints",4000,false),template=catalogText(req,"template",32000,false),starter=catalogText(req,"starterCode",32000,mode==ExerciseMode.DEBUG_COLLAB);
  String hints=catalogText(req,"hints",4000,false);
  List<Object> raw=AssignmentJson.list(req.get("tests"));
  if(raw.isEmpty()||raw.size()>50)throw new IllegalArgumentException("Cần từ 1 đến 50 testcase.");
  List<Exercise.TestCase> tests=new ArrayList<>();boolean publicTest=false;
  for(Object item:raw){
   if(!(item instanceof Map<?,?>))throw new IllegalArgumentException("Testcase không hợp lệ.");
   Map<String,Object> test=AssignmentJson.map(item);
   String testInput=catalogText(test,"input",8000,false),expected=catalogText(test,"expectedOutput",8000,false);
   Object weight=test.get("weight"),hidden=test.get("hidden");
   if(!(weight instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>100)throw new IllegalArgumentException("Trọng số testcase phải là số nguyên từ 1 đến 100.");
   if(!(hidden instanceof Boolean))throw new IllegalArgumentException("Testcase cần xác định công khai hoặc ẩn.");
   boolean secret=Boolean.TRUE.equals(hidden);publicTest|=!secret;
   tests.add(new Exercise.TestCase(testInput,expected,((Number)weight).intValue(),secret));
  }
  if(!publicTest)throw new IllegalArgumentException("Cần ít nhất một testcase công khai để chạy thử.");
  Exercise exercise=new Exercise(id,title,topic,difficulty,statement,input,output,constraints,template,hints,"",mode,starter,tests);
  String saved;try{saved=bank.save(who,exercise,AssignmentJson.text(req,"publication","PUBLISHED"),"BANK_UPDATE".equals(req.get("type")),AssignmentJson.integer(req,"expectedRevision",0),req.containsKey("bankScope")?AssignmentJson.text(req,"bankScope","PRACTICE"):null);}catch(SQLException e){if(e.getErrorCode()==19)throw new AccessFailure("DUPLICATE_EXERCISE","Mã bài đã tồn tại. Hãy chọn mã khác.");throw e;}
  return ok(requestId,AssignmentJson.mapOf("exercise",repo.getExercise(saved).publicView(),"message","Đã thêm bài vào ngân hàng."));
 }
 private Map<String,Object> handleBank(String requestId,String type,Map<String,Object> req,Identity who)throws Exception{
  if(who.role()!=Identity.Role.TEACHER)throw new AccessFailure("FORBIDDEN","Chỉ giáo viên được quản lý ngân hàng đề.");
  String id=AssignmentJson.text(req,"exerciseId","");
  if(type.equals("BANK_DELETE")){bank.delete(who,id,AssignmentJson.integer(req,"expectedRevision",0));return ok(requestId,Map.of());}
  if(type.equals("BANK_GET")){
   Map<String,Object> meta=bank.info(id,who);Exercise e=repo.getExercise(id);Map<String,Object> detail=new LinkedHashMap<>(e.publicView());detail.putAll(meta);boolean isOj=oj.config(id)!=null;detail.put("oj",isOj);if(isOj)detail.put("validation",oj.validation(id));if(Boolean.TRUE.equals(meta.get("canManage")))detail.put("referenceSolution",e.referenceSolution());
   List<Map<String,Object>> publicTests=new ArrayList<>(),tests=new ArrayList<>();for(Exercise.TestCase t:e.tests()){Map<String,Object> row=AssignmentJson.mapOf("input",t.input(),"expectedOutput",t.expected(),"weight",isOj?t.weight()/1000000d:t.weight(),"hidden",t.hidden());if(!t.hidden())publicTests.add(row);if(Boolean.TRUE.equals(meta.get("canManage")))tests.add(row);}
   detail.put("publicTests",publicTests);if(Boolean.TRUE.equals(meta.get("canManage")))detail.put("tests",tests);return ok(requestId,AssignmentJson.mapOf("exercise",detail));
  }
  if(!type.equals("BANK_LIST"))throw new AccessFailure("INVALID_REQUEST","Thao tác ngân hàng không hợp lệ.");
  String query=AssignmentJson.text(req,"query","").strip();if(query.length()>160)throw new IllegalArgumentException("Từ khóa tối đa 160 ký tự.");String needle=practiceSearch(query);
  String bankScope=QuestionBankRepository.scope(AssignmentJson.text(req,"bankScope","PRACTICE"));List<Map<String,Object>> all=bank.list(who).stream().filter(e->bankScope.equals(e.get("bankScope"))).toList();Set<String> ojIds=new HashSet<>(oj.activeIds());for(var item:all)item.put("oj",ojIds.contains(String.valueOf(item.get("id"))));String week=AssignmentJson.text(req,"weekId","");if(!week.isBlank()){Set<String> ids=weeks.exerciseIds(week,who);all=all.stream().filter(e->ids.contains(e.get("id"))).toList();}
  int published=0,drafts=0,used=0;Set<String> topics=new TreeSet<>();List<Map<String,Object>> filtered=new ArrayList<>();
  for(Map<String,Object> row:all){if("PUBLISHED".equals(row.get("publication")))published++;else drafts++;if(AssignmentJson.integer(row,"usageCount",0)>0)used++;topics.add(String.valueOf(row.get("topic")));
   if(!practiceSearch(row.get("displayCode")+" "+row.get("title")).contains(needle))continue;boolean match=true;for(String key:List.of("topic","difficulty","mode")){String value=AssignmentJson.text(req,key,"");if(!value.isBlank()&&!value.equals(row.get(key.equals("mode")&&Set.of("PROGRAM","METHOD","CLASS").contains(value)?"javaStructure":key)))match=false;}if(match)filtered.add(row);
  }
  int total=filtered.size(),pages=Math.max(1,(total+9)/10),page=Math.max(1,Math.min(pages,AssignmentJson.integer(req,"page",1)));
  return ok(requestId,AssignmentJson.mapOf("exercises",filtered.subList((page-1)*10,Math.min(total,page*10)),"topics",new ArrayList<>(topics),"stats",AssignmentJson.mapOf("total",all.size(),"published",published,"drafts",drafts,"used",used),"page",page,"total",total,"totalPages",pages));
 }
 private static String catalogText(Map<String,Object> req,String key,int max,boolean required){
  Object value=req.get(key);if(value!=null&&!(value instanceof String))throw new IllegalArgumentException(key+" phải là chuỗi.");
  String text=value==null?"":(String)value;
  if(text.length()>max||text.getBytes(StandardCharsets.UTF_8).length>max*4)throw new IllegalArgumentException(key+" vượt giới hạn "+max+" ký tự.");
  if(required&&text.isBlank())throw new IllegalArgumentException("Chưa nhập trường "+key+".");return text;
 }
 private Map<String,Object> handlePractice(String requestId,String type,Map<String,Object> req,Identity who)throws Exception {
  if(type.startsWith("PRACTICE_WEEK_")){
   String weekId=AssignmentJson.text(req,"weekId","");
   switch(type){
    case "PRACTICE_WEEK_LIST"->{return ok(requestId,AssignmentJson.mapOf("weeks",weeks.list(who)));}
    case "PRACTICE_WEEK_SAVE"->{String saved=weeks.save(who,weekId,AssignmentJson.integer(req,"weekNo",0),catalogText(req,"title",200,true).strip(),catalogText(req,"description",4000,false));return ok(requestId,AssignmentJson.mapOf("weekId",saved,"weeks",weeks.list(who)));}
    case "PRACTICE_WEEK_DELETE"->weeks.delete(who,weekId);
    case "PRACTICE_WEEK_ADD","PRACTICE_WEEK_REMOVE"->{List<String> ids=new ArrayList<>();for(Object item:AssignmentJson.list(req.get("exerciseIds")))ids.add(String.valueOf(item));weeks.changeItems(who,weekId,ids,type.equals("PRACTICE_WEEK_ADD"));}
    default->throw new AccessFailure("INVALID_REQUEST","Thao tác Week không hợp lệ.");
   }return ok(requestId,AssignmentJson.mapOf("weeks",weeks.list(who)));
  }
  if(type.equals("PRACTICE_RESULT")) {
   Map<String,Object> row=practice.get(AssignmentJson.text(req,"submissionId",""));
   if(row==null||!who.userId().equals(row.get("userId")))throw new AccessFailure("NOT_FOUND","Không tìm thấy bài nộp của bạn.");
   return ok(requestId,AssignmentJson.mapOf("submission",practiceView(row,true)));
  }
  if(type.equals("PRACTICE_LIST")) {
   List<Map<String,Object>> all=repo.listExercises("","","");String weekId=AssignmentJson.text(req,"weekId","");if(!weekId.isBlank()){Set<String> ids=weeks.exerciseIds(weekId,who);all=all.stream().filter(e->ids.contains(String.valueOf(e.get("id")))).toList();}Map<String,Map<String,Object>> progress=practice.progress(who.userId());
   List<Map<String,Object>> filtered=new ArrayList<>();Set<String> topics=new TreeSet<>();int ac=0,failed=0,fresh=0;
   String query=AssignmentJson.text(req,"query","").strip();if(query.length()>160)throw new AccessFailure("INVALID_REQUEST","Từ khóa tối đa 160 ký tự.");
   String needle=practiceSearch(query);
   for(Map<String,Object> item:all) {
    Map<String,Object> row=new LinkedHashMap<>(item);row.putAll(progress.getOrDefault(String.valueOf(item.get("id")),AssignmentJson.mapOf("practiceStatus","NEW","acceptedByMe",false,"attempts",0)));
    String state=String.valueOf(row.get("practiceStatus"));if(state.equals("AC"))ac++;if(state.equals("FAILED"))failed++;if(state.equals("NEW"))fresh++;
    topics.add(String.valueOf(item.get("topic")));
    if(!practiceSearch(item.get("id")+" "+item.get("title")).contains(needle))continue;
    boolean matches=true;for(String key:List.of("topic","difficulty","mode")){String value=AssignmentJson.text(req,key,"");if(!value.isBlank()&&!value.equals(row.get(key.equals("mode")&&Set.of("PROGRAM","METHOD","CLASS").contains(value)?"javaStructure":key)))matches=false;}
    String status=AssignmentJson.text(req,"filterStatus","");if(!status.isBlank()&&(status.equals("ATTEMPTED")?state.equals("NEW"):!status.equals(state)))matches=false;
    if(matches)filtered.add(row);
   }
   int total=filtered.size(),pages=Math.max(1,(total+9)/10),page=Math.max(1,Math.min(pages,AssignmentJson.integer(req,"page",1)));
   return ok(requestId,AssignmentJson.mapOf("exercises",filtered.subList((page-1)*10,Math.min(total,page*10)),"total",total,"page",page,"totalPages",pages,"topics",new ArrayList<>(topics),"stats",AssignmentJson.mapOf("total",all.size(),"accepted",ac,"failed",failed,"fresh",fresh)));
  }
  String id=AssignmentJson.text(req,"exerciseId","");bank.requirePracticePublished(id);Exercise exercise=repo.getExercise(id);
  if(exercise==null)throw new AccessFailure("NOT_FOUND","Bài trong ngân hàng không tồn tại.");
  switch(type) {
   case "PRACTICE_GET" -> {
    Map<String,Object> detail=new LinkedHashMap<>(exercise.publicView());List<Map<String,Object>> tests=new ArrayList<>();
    for(Exercise.TestCase test:exercise.tests())if(!test.hidden())tests.add(AssignmentJson.mapOf("input",test.input(),"expectedOutput",test.expected()));
    detail.put("publicTests",tests);detail.putAll(practice.progress(who.userId()).getOrDefault(id,AssignmentJson.mapOf("practiceStatus","NEW","acceptedByMe",false)));
    return ok(requestId,AssignmentJson.mapOf("exercise",detail));
   }
   case "PRACTICE_SUBMISSIONS" -> {return ok(requestId,practice.history(who.userId(),id,AssignmentJson.integer(req,"page",1)));}
   case "PRACTICE_SUBMIT","PRACTICE_RUN" -> {return createPractice(requestId,req,who,exercise,type.equals("PRACTICE_RUN"));}
   default -> throw new AccessFailure("INVALID_REQUEST","Thao tác luyện tập không hợp lệ.");
  }
 }
 private static String practiceSearch(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).toLowerCase(Locale.ROOT).replaceAll("\\p{M}+","").replace('đ','d');}
 private static Map<String,Object> practiceView(Map<String,Object> row,boolean source){Map<String,Object> out=new LinkedHashMap<>(row);out.remove("userId");if(!source)out.remove("sourceCode");return out;}
 private synchronized Map<String,Object> createPractice(String requestId,Map<String,Object> req,Identity who,Exercise exercise,boolean run)throws Exception {
  String key=AssignmentJson.text(req,"idempotencyKey","");if(key.isBlank()||key.length()>128)throw new AccessFailure("INVALID_REQUEST","Thiếu hoặc sai mã yêu cầu nộp.");
  String source=AssignmentJson.text(req,"sourceCode","");if(source.isBlank()||source.getBytes(StandardCharsets.UTF_8).length>32000||source.contains("\u0000"))throw new AccessFailure("INVALID_REQUEST","Code phải có nội dung, tối đa 32.000 byte.");
  Map<String,Object> previous=practice.byKey(who.userId(),key);
  if(previous!=null) {
   if(!exercise.id().equals(previous.get("exerciseId"))||run!=Boolean.TRUE.equals(previous.get("isRun"))||!source.equals(previous.get("sourceCode")))throw new AccessFailure("DUPLICATE_REQUEST","Mã yêu cầu đã dùng cho code/bài khác.");
   return ok(requestId,AssignmentJson.mapOf("submission",practiceView(previous,false)));
  }
  int count=(int)exercise.tests().stream().filter(t->!run||!t.hidden()).count();
  if(count==0)throw new AccessFailure("INVALID_REQUEST","Bài chưa có test phù hợp để chạy.");
  if(queue.remainingCapacity()==0)throw new AccessFailure("QUEUE_FULL","Hàng đợi chấm đang đầy. Hãy thử lại sau.");
  Map<String,Object> row=practice.create(who,exercise.id(),source,run,key,count);
  String id=String.valueOf(row.get("id"));
  if(!queue.offer(new GradingTask(id,TaskKind.PRACTICE)))practice.finish(id,new GradingResult("INTERNAL_ERROR",0,100,0,count,"Hàng đợi không nhận được lượt chấm.",0,List.of()));
  return ok(requestId,AssignmentJson.mapOf("submission",practiceView(practice.get(id),false)));
 }
 private synchronized Map<String,Object> createTeacherTrial(String requestId,Map<String,Object> req,String roomId,Identity who,boolean teacherOwner)throws Exception {
  requireOwner(teacherOwner);
  if(who.role()!=Identity.Role.TEACHER)throw new AccessFailure("FORBIDDEN","Chỉ giáo viên chủ phòng được thử bài trong ngân hàng.");
  String exerciseId=AssignmentJson.text(req,"exerciseId","").trim();
  Exercise exercise=repo.getExercise(exerciseId);
  if(exercise==null)throw new AccessFailure("NOT_FOUND","Bài trong ngân hàng không tồn tại.");
  if(exercise.mode()!=ExerciseMode.BUILD_FROM_SCRATCH)throw new AccessFailure("WRONG_EXERCISE_MODE","Chỉ thử chấm bài Lập trình từ đầu tại đây. Bài sửa lỗi dùng Kiểm tra code chung trong Phòng học.");
  String key=AssignmentJson.text(req,"idempotencyKey","").trim();
  if(key.isBlank()||key.length()>100)throw new AccessFailure("INVALID_REQUEST","idempotencyKey không hợp lệ.");
  TeacherTrialRecord previous=repo.teacherTrialByIdempotency(key);
  if(previous!=null){if(!previous.teacherUserId().equals(who.userId())||!previous.roomId().equals(roomId)||!previous.exerciseId().equals(exerciseId))throw new AccessFailure("DUPLICATE_REQUEST","idempotencyKey đã dùng cho lượt thử khác.");return ok(requestId,AssignmentJson.mapOf("trial",teacherTrialMap(previous),"message","Đây là kết quả của lần thử đã nhận trước đó."));}
  String source=AssignmentJson.text(req,"sourceCode","");
  if(source.isBlank())throw new AccessFailure("INVALID_REQUEST","Code không được để trống.");
  if(source.getBytes(StandardCharsets.UTF_8).length>32768)throw new AccessFailure("INVALID_REQUEST","Source code vượt 32 KiB.");
  if(queue.remainingCapacity()==0)throw new AccessFailure("QUEUE_FULL","Hàng đợi chấm bài đang đầy; lượt thử chưa được nhận.");
  TeacherTrialRecord trial=new TeacherTrialRecord(UUID.randomUUID().toString(),exerciseId,roomId,who.userId(),who.username(),source,System.currentTimeMillis(),"QUEUED",0,100,"QUEUED",0,exercise.tests().size(),"Đã nhận lượt thử, đang chờ chấm.",0,key);
  repo.createTeacherTrial(trial);
  if(!queue.offer(new GradingTask(trial.id(),TaskKind.TRIAL))){repo.saveTeacherTrialResult(trial.id(),new GradingResult("INTERNAL_ERROR",0,100,0,exercise.tests().size(),"Hàng đợi không nhận được lượt thử.",0,List.of()));return ok(requestId,AssignmentJson.mapOf("trial",teacherTrialMap(repo.getTeacherTrial(trial.id())),"message","Đã lưu lượt thử nhưng worker chưa nhận được tác vụ."));}
  return ok(requestId,AssignmentJson.mapOf("trial",teacherTrialMap(trial),"message","Đã nhận lượt thử của giáo viên. Kết quả không tính vào thành tích học sinh."));
 }
 private static Map<String,Object> teacherTrialMap(TeacherTrialRecord t){return AssignmentJson.mapOf("id",t.id(),"exerciseId",t.exerciseId(),"roomId",t.roomId(),"teacherUsername",t.teacherUsername(),"submittedAt",t.submittedAt(),"status",t.status(),"score",t.score(),"maxScore",t.maxScore(),"verdict",t.verdict(),"passedTests",t.passedTests(),"totalTests",t.totalTests(),"feedback",t.feedback(),"durationMs",t.durationMillis());}

 private List<Map<String,Object>> visibleContests(String room,Identity who,boolean owner)throws SQLException{
  List<Map<String,Object>> list=new ArrayList<>();for(Map<String,Object> c:repo.listContests(room))if(owner||ContestDistributionRepository.canAccess(repo,String.valueOf(c.get("id")),who.userId())){if(!owner){int count=ContestDistributionRepository.allocatedCount(repo,String.valueOf(c.get("id")),who.userId());if(count>0)c.put("assignedProblemCount",count);String cid=String.valueOf(c.get("id"));if(organization.repository.managed(cid)){Map<String,Object> exam=organization.repository.require(who,cid,false);c.put("windowEndAt",exam.get("windowEndAt"));c.put("durationMinutes",exam.get("durationMinutes"));c.put("endAt",organization.repository.deadline(who,cid,AssignmentJson.number(c,"endAt",0)));c.put("finished",AssignmentJson.number(exam,"attemptFinishedAt",0)>0);}}list.add(c);}return list;
 }
 private Map<String,Object> distributeContest(String requestId,Map<String,Object> req,Identity who,String session,String room,boolean owner)throws SQLException{
  requireOwner(owner);String cid=AssignmentJson.text(req,"contestId","");Map<String,Object> c=repo.contest(cid);
  if(c==null||!room.equals(c.get("roomId")))throw new AccessFailure("NOT_FOUND","Không tìm thấy kỳ thi trong lớp.");
  if(System.currentTimeMillis()>=AssignmentJson.number(c,"endAt",0))throw new AccessFailure("CONTEST_CLOSED","Kỳ thi đã kết thúc.");
  Set<String> ids=new LinkedHashSet<>();for(Object x:AssignmentJson.list(req.get("exerciseIds")))ids.add(String.valueOf(x));
  if(ids.isEmpty()||!repo.contestExerciseIds(cid).containsAll(ids))throw new AccessFailure("INVALID_REQUEST","Chọn bài thuộc danh sách của kỳ thi này.");
  Set<String> users=new LinkedHashSet<>();for(Object x:AssignmentJson.list(req.get("studentIds")))users.add(String.valueOf(x));
  List<Identity> targets=new ArrayList<>();for(Identity i:gateway.contestStudents(session,room))if(users.remove(i.userId()))targets.add(i);
  if(!users.isEmpty()||targets.isEmpty())throw new AccessFailure("INVALID_REQUEST","Chọn sinh viên hợp lệ trong lớp của bạn.");
  ContestDistributionRepository.assign(repo,cid,targets,new ArrayList<>(ids));
  return ok(requestId,AssignmentJson.mapOf("draws",ContestDistributionRepository.draws(repo,cid)));
 }
 private Map<String,Object> createContest(String requestId,Map<String,Object> req,String roomId,Identity who,boolean teacherOwner)throws Exception {
  requireOwner(teacherOwner);
  String title=AssignmentJson.text(req,"title","").trim();if(title.isBlank()||title.length()>120)throw new AccessFailure("INVALID_REQUEST","Tên kỳ thi phải có 1-120 ký tự.");
  long now=System.currentTimeMillis();int startInMinutes=AssignmentJson.integer(req,"startInMinutes",0);int durationMinutes=AssignmentJson.integer(req,"durationMinutes",0);
  if(startInMinutes<0||startInMinutes>43200||durationMinutes<1||durationMinutes>43200)throw new AccessFailure("INVALID_REQUEST","Thời gian thi không hợp lệ.");
  long start=now+startInMinutes*60_000L;long end=start+durationMinutes*60_000L;if(end-now>30L*24*60*60*1000)throw new AccessFailure("INVALID_REQUEST","Thời gian thi tối đa 30 ngày.");
  String contestType=AssignmentJson.text(req,"contestType","INDIVIDUAL").trim().toUpperCase(Locale.ROOT);
  if(!contestType.equals("INDIVIDUAL")&&!contestType.equals("TEAM_COLLAB"))throw new AccessFailure("INVALID_REQUEST","contestType phải là INDIVIDUAL hoặc TEAM_COLLAB.");
  if(contestType.equals("TEAM_COLLAB"))throw new AccessFailure("CONTEST_TYPE_NOT_READY","Kỳ thi nhóm TEAM_COLLAB đang thuộc giai đoạn tiếp theo. Hiện chỉ hỗ trợ thi cá nhân; bài sửa lỗi cộng tác không bị chuyển thành bài cá nhân.");
  String bankVersion=AssignmentJson.text(req,"examVersionId","");
  if(!bankVersion.isBlank()){
   Map<String,Object> definition=examBank.repository.approvedForContest(who,bankVersion);
   String distribution=AssignmentJson.text(req,"distributionMode","ALL");int count=AssignmentJson.integer(req,"problemsPerStudent",1),size=AssignmentJson.list(definition.get("items")).size();
   if(!Set.of("ALL","RANDOM").contains(distribution)||("RANDOM".equals(distribution)&&(count<1||count>size)))throw new IllegalArgumentException("Invalid distribution policy");
   String id=UUID.randomUUID().toString();repo.createContestFromExam(id,roomId,title,who.userId(),now,start,end,definition,distribution,count);
   return ok(requestId,AssignmentJson.mapOf("contestId",id,"examVersionId",bankVersion,"message","Created contest from frozen exam version"));
  }
  List<String> ids=new ArrayList<>();
  for(Object o:AssignmentJson.list(req.get("exerciseIds"))) {String id=String.valueOf(o).trim();if(!id.isBlank()&&!ids.contains(id))ids.add(id);}
  // Hỗ trợ client cũ bằng cách đổi ID bài đã giao sang ID bài trong ngân hàng đề.
  if(ids.isEmpty())for(Object o:AssignmentJson.list(req.get("assignmentIds"))){String aid=String.valueOf(o).trim();if(aid.isBlank())continue;AssignmentRecord a=requireAssignmentVisible(aid,roomId,who,true);if(!a.createdByUserId().equals(who.userId()))throw new AccessFailure("FORBIDDEN","Chỉ dùng bài do giáo viên chủ phòng giao.");if(!ids.contains(a.exerciseId()))ids.add(a.exerciseId());}
  if(ids.isEmpty()||ids.size()>20)throw new AccessFailure("INVALID_REQUEST","Chọn từ 1 đến 20 bài trong ngân hàng.");
  for(String id:ids){bank.requirePublished(id);bank.info(id,who);Exercise e=repo.getExercise(id);if(e==null)throw new AccessFailure("NOT_FOUND","Bài trong ngân hàng không tồn tại: "+id+".");if(e.mode()!=ExerciseMode.BUILD_FROM_SCRATCH)throw new AccessFailure("INVALID_REQUEST","Bài "+id+" là DEBUG_COLLAB. Chọn contest nhóm khi tính năng TEAM_COLLAB được triển khai; hiện không thể đưa bài sửa lỗi vào kỳ thi cá nhân.");}
  String distribution=AssignmentJson.text(req,"distributionMode","ALL").toUpperCase(Locale.ROOT);int count=AssignmentJson.integer(req,"problemsPerStudent",1);
  if(!Set.of("ALL","RANDOM").contains(distribution)||("RANDOM".equals(distribution)&&(count<1||count>ids.size())))throw new AccessFailure("INVALID_REQUEST","Invalid number of random problems per student.");
  String id=UUID.randomUUID().toString();repo.createContestFromExercises(id,roomId,title,who.userId(),now,start,end,contestType,ids,distribution,"RANDOM".equals(distribution)?count:0);
  return ok(requestId,AssignmentJson.mapOf("contestId",id,"contestType",contestType,"exerciseIds",ids,"message","Đã tạo kỳ thi trực tiếp từ ngân hàng bài."));
 }
 private synchronized Map<String,Object> submitContest(String requestId,Map<String,Object> req,String roomId,Identity who,boolean teacherOwner)throws Exception {
  if(who.role()!=Identity.Role.STUDENT)throw new AccessFailure("FORBIDDEN","Chỉ học sinh được nộp bài thi chính thức. Giáo viên dùng chế độ thử riêng.");
  String contestId=AssignmentJson.text(req,"contestId","").trim();String exerciseId=AssignmentJson.text(req,"exerciseId","").trim();String key=AssignmentJson.text(req,"idempotencyKey","").trim();
  if(key.isBlank()||key.length()>100)throw new AccessFailure("INVALID_REQUEST","idempotencyKey không hợp lệ.");
  if(contestId.isBlank()||exerciseId.isBlank())throw new AccessFailure("INVALID_REQUEST","Thiếu contestId hoặc exerciseId.");
  ContestSubmissionRecord previous=repo.contestSubmissionByIdempotency(key);
  if(previous!=null){if(!previous.userId().equals(who.userId())||!previous.contestId().equals(contestId)||!previous.exerciseId().equals(exerciseId))throw new AccessFailure("DUPLICATE_REQUEST","idempotencyKey đã được dùng cho yêu cầu khác.");return ok(requestId,AssignmentJson.mapOf("submission",contestSubmissionView(previous,false),"message","Đây là receipt của lần nộp thi đã nhận trước đó."));}
  Map<String,Object> contest=repo.contest(contestId);if(contest==null||!roomId.equals(contest.get("roomId")))throw new AccessFailure("NOT_FOUND","Không tìm thấy kỳ thi trong phòng.");
  if(!"INDIVIDUAL".equalsIgnoreCase(String.valueOf(contest.get("contestType"))))throw new AccessFailure("CONTEST_TYPE_NOT_READY","Kỳ thi nhóm chưa được bật; không nhận bài cá nhân cho contest này.");
  long now=System.currentTimeMillis(),start=((Number)contest.get("startAt")).longValue(),end=organization.repository.deadline(who,contestId,((Number)contest.get("endAt")).longValue());
  if(now<start)throw new AccessFailure("CONTEST_NOT_STARTED","Kỳ thi chưa bắt đầu.");if(now>=end)throw new AccessFailure("CONTEST_CLOSED","Kỳ thi đã kết thúc.");
  if(!ContestDistributionRepository.studentProblems(repo,contestId,who).contains(exerciseId))throw new AccessFailure("FORBIDDEN","Problem was not assigned to this student.");
  String problemId=repo.contestProblemId(contestId,exerciseId);if(problemId==null)throw new AccessFailure("NOT_FOUND","Bài này không thuộc kỳ thi.");
  Exercise exercise=repo.getExercise(exerciseId);if(exercise==null)throw new AccessFailure("NOT_FOUND","Bài trong ngân hàng không còn tồn tại.");
  if(exercise.mode()!=ExerciseMode.BUILD_FROM_SCRATCH)throw new AccessFailure("INVALID_REQUEST","Bài DEBUG_COLLAB không thể nộp như bài cá nhân.");
  String source=AssignmentJson.text(req,"sourceCode","");if(source.isBlank())throw new AccessFailure("INVALID_REQUEST","Code bài thi không được để trống.");if(source.getBytes(StandardCharsets.UTF_8).length>32768)throw new AccessFailure("INVALID_REQUEST","Source code vượt 32 KiB.");
  if(!organization.repository.managed(contestId)&&Boolean.FALSE.equals(examBank.repository.contestPolicy(contestId).get("allowResubmit"))&&!repo.listContestSubmissions(contestId,exerciseId,who.userId(),false).isEmpty())throw new IllegalArgumentException("Exam policy allows only one submission per question.");
  if(queue.remainingCapacity()==0)throw new AccessFailure("QUEUE_FULL","Hàng đợi chấm bài đang đầy; chưa nhận bài, hãy thử lại.");
  String id=UUID.randomUUID().toString();ContestSubmissionRecord submission=new ContestSubmissionRecord(id,contestId,exerciseId,roomId,who.userId(),who.username(),source,now,"QUEUED",0,repo.contestMaxScore(contestId,exerciseId),"QUEUED",0,exercise.tests().size(),"Đã nhận bài, đang chờ chấm.",0,key);
  repo.createContestSubmission(submission,problemId);
  if(!queue.offer(new GradingTask(id,TaskKind.CONTEST))){repo.saveContestSubmissionResult(id,new GradingResult("INTERNAL_ERROR",0,submission.maxScore(),0,exercise.tests().size(),"Hàng đợi không nhận được bài thi sau khi lưu.",0,List.of()));return ok(requestId,AssignmentJson.mapOf("submission",contestSubmissionView(repo.getContestSubmission(id),false),"message","Bài đã lưu nhưng worker chưa nhận được tác vụ."));}
  return ok(requestId,AssignmentJson.mapOf("submission",contestSubmissionView(submission,false),"message","Đã nhận bài thi; trạng thái QUEUED."));
 }
 private Map<String,Object> contestSubmissionView(ContestSubmissionRecord s,boolean teacher)throws SQLException{
  Map<String,Object> row=new LinkedHashMap<>(contestSubmissionMap(s));row.put("structureResults",repo.structureResults(s.id()));Map<String,Object> policy=examBank.repository.contestPolicy(s.contestId());Map<String,Object> contest=repo.contest(s.contestId());
  if(organization.repository.managed(s.contestId())&&!teacher)return organization.repository.redact(new Identity(s.userId(),s.username(),s.username(),Identity.Role.STUDENT),s.contestId(),row);
  if(!teacher&&Boolean.FALSE.equals(policy.get("showScore"))&&contest!=null&&System.currentTimeMillis()<AssignmentJson.number(contest,"endAt",0)){row.put("score",null);row.put("scoreHidden",true);}
  if(!teacher)PublicTestVisibility.apply(row,policy,repo.contestSubmissionTestResults(s.id()),!Boolean.TRUE.equals(row.get("scoreHidden")));
  return row;
 }
 private static Map<String,Object> contestSubmissionMap(ContestSubmissionRecord s){return AssignmentJson.mapOf("id",s.id(),"contestId",s.contestId(),"exerciseId",s.exerciseId(),"username",s.username(),"submittedAt",s.submittedAt(),"status",s.status(),"score",s.score(),"maxScore",s.maxScore(),"verdict",s.verdict(),"passedTests",s.passedTests(),"totalTests",s.totalTests(),"feedback",s.feedback(),"durationMs",s.durationMillis());}
 private synchronized Map<String,Object> submit(String requestId,Map<String,Object> req,String sessionId,String roomId,Identity who,boolean teacherOwner)throws Exception {
  if (who.role()!=Identity.Role.STUDENT) throw new AccessFailure("FORBIDDEN","Chỉ học sinh được nộp bài.");
  String aid=AssignmentJson.text(req,"assignmentId","");
  AssignmentRecord a=requireAssignmentVisible(aid,roomId,who,teacherOwner);
  if (!a.status().equals("OPEN")) throw new AccessFailure("ASSIGNMENT_CLOSED","Bài đã đóng.");

  String submissionMode=AssignmentJson.text(req,"submissionMode","PERSONAL").toUpperCase(Locale.ROOT);
  if (!submissionMode.equals("PERSONAL"))
   throw new AccessFailure("INVALID_REQUEST","Bài cá nhân chỉ nhận submissionMode=PERSONAL; code chung phải dùng SHARED_CHECK_CREATE.");
  if (a.mode()!=ExerciseMode.BUILD_FROM_SCRATCH)
   throw new AccessFailure("INVALID_REQUEST","Bài DEBUG_COLLAB phải dùng chức năng Kiểm tra code chung, không tạo bài nộp cá nhân.");
  String key=AssignmentJson.text(req,"idempotencyKey","");
  if (key.isBlank() || key.length()>100) throw new AccessFailure("INVALID_REQUEST","idempotencyKey không hợp lệ.");

  // Gửi lại cùng yêu cầu sẽ trả biên nhận ban đầu, kể cả khi lúc gửi lại đã hết hạn.
  SubmissionRecord previous=repo.byIdempotency(key);
  if (previous!=null) {
   if (!previous.userId().equals(who.userId()) || !previous.assignmentId().equals(aid) || !previous.submissionMode().equals(submissionMode))
    throw new AccessFailure("DUPLICATE_REQUEST","idempotencyKey đã được dùng cho yêu cầu khác.");
   return ok(requestId,AssignmentJson.mapOf("submission",submissionMap(previous,false),"message","Đây là receipt của lần nộp đã nhận trước đó."));
  }

  long now=System.currentTimeMillis();
  String contestId=AssignmentJson.text(req,"contestId","");
  if (contestId.isBlank()) {
   if (a.deadlineAt()>0 && now>=a.deadlineAt()) throw new AccessFailure("DEADLINE_PASSED","Đã quá hạn nộp bài.");
  } else {
   Map<String,Object> c=repo.contest(contestId);
   if (c==null || !roomId.equals(c.get("roomId")) || !repo.contestAssignments(contestId).contains(aid))
    throw new AccessFailure("NOT_FOUND","Bài không thuộc kỳ thi này.");
   long start=((Number)c.get("startAt")).longValue(), end=((Number)c.get("endAt")).longValue();
   if (now<start) throw new AccessFailure("CONTEST_NOT_STARTED","Kỳ thi chưa bắt đầu.");
   if (now>=end) throw new AccessFailure("CONTEST_CLOSED","Kỳ thi đã kết thúc.");
  }

  String source=AssignmentJson.text(req,"sourceCode","");
  if (source.isBlank()) throw new AccessFailure("INVALID_REQUEST","Code cá nhân không được để trống.");
  if (source.getBytes(StandardCharsets.UTF_8).length>32768) throw new AccessFailure("INVALID_REQUEST","Source code vượt 32 KiB.");
  if (queue.remainingCapacity()==0) throw new AccessFailure("QUEUE_FULL","Hàng đợi chấm bài đang đầy; chưa nhận bài, hãy thử lại.");
  Exercise ex=repo.getExercise(a.exerciseId());
  if (ex==null || ex.tests().isEmpty()) throw new AccessFailure("INTERNAL_ERROR","Bài tập chưa có bộ test hợp lệ.");
  String id=UUID.randomUUID().toString();
  SubmissionRecord sub=new SubmissionRecord(id,aid,roomId,who.userId(),who.username(),source,now,"QUEUED",0,100,"QUEUED",0,ex.tests().size(),"Đã nhận bài, đang chờ chấm.",0,submissionMode,key);
  repo.createSubmission(sub,contestId.isBlank()?null:contestId);
  if (!queue.offer(new GradingTask(id,TaskKind.PERSONAL))) {
   // Chỉ báo đang chờ chấm khi bài nộp thực sự đã được đưa vào hàng đợi.
   repo.saveResult(id,new GradingResult("INTERNAL_ERROR",0,100,0,ex.tests().size(),"Hàng đợi không nhận được bài sau khi lưu. Hãy liên hệ giảng viên.",0,List.of()));
   SubmissionRecord failed=repo.getSubmission(id);
   return ok(requestId,AssignmentJson.mapOf("submission",submissionMap(failed,false),"message","Bài đã lưu nhưng worker chưa nhận được tác vụ."));
  }
  return ok(requestId,AssignmentJson.mapOf("submission",submissionMap(sub,false),"message","Đã nhận bài; trạng thái QUEUED."));
 }
 private synchronized Map<String,Object> createSharedCheck(String requestId,Map<String,Object> req,String sessionId,String roomId,Identity who,boolean teacherOwner)throws Exception {
  String aid=AssignmentJson.text(req,"assignmentId","");AssignmentRecord a=requireAssignmentVisible(aid,roomId,who,teacherOwner);Exercise e=repo.getExercise(a.exerciseId());requireDebug(a,e);
  String key=AssignmentJson.text(req,"checkRequestId","").trim();if(key.isBlank()||key.length()>100)throw new AccessFailure("INVALID_REQUEST","checkRequestId không hợp lệ.");
  long expected=AssignmentJson.number(req,"roomCodeVersion",-1);String requestedHash=AssignmentJson.text(req,"sourceHash","");
  SharedCheckRun duplicate=repo.sharedCheckByRequestKey(key);if(duplicate!=null){if(!duplicate.assignmentId().equals(aid)||!duplicate.requestedByUserId().equals(who.userId())||duplicate.checkedVersion()!=expected||!duplicate.sourceHash().equals(requestedHash))throw new AccessFailure("DUPLICATE_REQUEST","checkRequestId đã dùng cho lượt kiểm tra khác.");return ok(requestId,AssignmentJson.mapOf("checkRun",sharedCheckMap(duplicate),"message","Đây là receipt của lượt kiểm tra đã nhận trước đó."));}
  if(!"OPEN".equalsIgnoreCase(a.status()))throw new AccessFailure("ASSIGNMENT_CLOSED","Bài đã đóng.");
  long now=System.currentTimeMillis();if(a.deadlineAt()>0&&now>=a.deadlineAt())throw new AccessFailure("DEADLINE_PASSED","Đã quá hạn kiểm tra bài sửa lỗi.");
  SharedRoomCodeProvider.Snapshot snap;try{snap=sharedCodeProvider.snapshot(sessionId,roomId);}catch(SecurityException ex){throw new AccessFailure("NOT_MEMBER","Không lấy được code chung của phòng.");}
  if(snap.draftActive())throw new AccessFailure("DRAFT_NOT_COMMITTED","Phòng còn bản nháp chưa commit. Hãy hoàn tất hoặc hủy lượt sửa trước khi kiểm tra.");
  if(expected<0||snap.version()!=expected)throw new AccessFailure("STALE_VERSION","Version code chung đã thay đổi. Version gửi="+expected+", version hiện tại="+snap.version()+". Hãy tải lại trạng thái rồi kiểm tra lại.");
  String actualHash=sha256(snap.source());if(requestedHash.isBlank()||!actualHash.equalsIgnoreCase(requestedHash))throw new AccessFailure("STALE_SOURCE","Snapshot code không khớp với trạng thái server. Hãy làm mới trạng thái.");
  if(snap.source()==null||snap.source().isBlank())throw new AccessFailure("INVALID_REQUEST","Code chung đang trống.");
  if(snap.source().getBytes(StandardCharsets.UTF_8).length>32768)throw new AccessFailure("INVALID_REQUEST","Code chung vượt 32 KiB.");
  if(queue.remainingCapacity()==0)throw new AccessFailure("QUEUE_FULL","Hàng đợi chấm đang đầy; lượt kiểm tra chưa được nhận.");
  if(e.tests().isEmpty())throw new AccessFailure("INTERNAL_ERROR","Bài sửa lỗi chưa có bộ test.");
  SharedCheckRun run=new SharedCheckRun(UUID.randomUUID().toString(),key,aid,roomId,snap.version(),actualHash,snap.source(),who.userId(),who.username(),System.currentTimeMillis(),"QUEUED","QUEUED",0,100,0,e.tests().size(),"Đã nhận lượt kiểm tra, đang chờ sandbox.",0);
  repo.createSharedCheck(run);
  if(!queue.offer(new GradingTask(run.id(),TaskKind.SHARED))){repo.saveSharedCheckResult(run.id(),"INTERNAL_ERROR","INTERNAL_ERROR",0,100,0,e.tests().size(),"Hàng đợi không nhận được lượt kiểm tra; hãy thử lại.",0);throw new AccessFailure("QUEUE_FULL","Hàng đợi không nhận được lượt kiểm tra; chưa bắt đầu chấm.");}
  return ok(requestId,AssignmentJson.mapOf("checkRun",sharedCheckMap(run),"message","Đã nhận yêu cầu kiểm tra code chung."));
 }
 public record Metrics(int queuedTasks, int activeTasks, int workerCount, int queueCapacity, String dockerImage, boolean running) { }
 public Metrics metrics(){return new Metrics(queue.size(),activeTasks.get(),workers.length,queueCapacity,dockerImage,!closed.get());}
 private void workLoop(){
  while(!closed.get()){
   GradingTask task=null;
   try{
    task=queue.take();
    activeTasks.incrementAndGet();
    GradingResult result;
    try {
     result=gradeTask(task);
    } catch(Exception failure) {
     if(closed.get() || failure instanceof InterruptedException){Thread.currentThread().interrupt();break;}
     gateway.logService("Lỗi chấm kind="+task.kind()+" submission="+task.id()+" cause="+failure.getClass().getSimpleName());
     double maximum=100;try{if(task.kind()==TaskKind.CONTEST)maximum=repo.getContestSubmission(task.id()).maxScore();}catch(Exception ignored){}
     result=new GradingResult("INTERNAL_ERROR",0,maximum,0,0,"Dịch vụ chấm gặp lỗi. Mã bài nộp: "+task.id(),0,List.of());
    }
    if(result!=null)persistUntilSaved(task,result);
   }catch(InterruptedException e){if(closed.get())break;}
   finally{if(task!=null)activeTasks.decrementAndGet();}
  }
 }
 private GradingResult gradeExercise(String id,String source,Exercise e,List<Exercise.TestCase> tests,double score)throws Exception{var config=oj.config(e.id());return config==null?sandbox.grade(id,source,tests,score):new studyfix.assignment.grading.OjJavaSandbox(dockerImage).grade(e,source,tests,score,config);}
 private GradingResult gradeContest(ContestSubmissionRecord submission,Exercise e)throws Exception{
  Map<String,Object> policy=examBank.repository.contestPolicy(submission.contestId());
  if(!Boolean.FALSE.equals(policy.get("inherit")))return gradeExercise("contest-"+submission.id(),submission.source(),e,e.tests(),submission.maxScore());
  Map<String,Object> config=new LinkedHashMap<>();Map<String,Object> own=oj.config(e.id());if(own!=null)config.putAll(own);
  for(String[] pair:List.of(new String[]{"timeLimitMs","time_limit_ms"},new String[]{"memoryLimitMb","memory_limit_mb"},new String[]{"outputLimitKb","output_limit_kb"},new String[]{"checker","checker"},new String[]{"partialScoring","partial_scoring"}))config.put(pair[1],policy.get(pair[0]));
  return new studyfix.assignment.grading.OjJavaSandbox(dockerImage).grade(e,submission.source(),e.tests(),submission.maxScore(),config);
 }
 private GradingResult gradeTask(GradingTask task)throws Exception {
    if(task.kind()==TaskKind.WEEK){weekAssignments.repository.running(task.id());var row=weekAssignments.repository.rawSubmission(task.id());Exercise e=repo.getExercise(String.valueOf(row.get("exerciseId")));boolean run=AssignmentJson.number(row,"is_run",0)!=0;return gradeExercise("week-"+task.id(),String.valueOf(row.get("source_code")),e,e.tests().stream().filter(t->!run||!t.hidden()).toList(),100);
    }else if(task.kind()==TaskKind.PRACTICE){
      practice.running(task.id());Map<String,Object> row=practice.get(task.id());if(row==null)return null;
      Exercise exercise=repo.getExercise(String.valueOf(row.get("exerciseId")));if(exercise==null)throw new IllegalStateException("Bài không tồn tại.");
      boolean run=Boolean.TRUE.equals(row.get("isRun"));List<Exercise.TestCase> tests=exercise.tests().stream().filter(t->!run||!t.hidden()).toList();
      return gradeExercise("practice-"+task.id(),String.valueOf(row.get("sourceCode")),exercise,tests,100);
    }else if(task.kind()==TaskKind.SHARED){
      repo.markSharedCheckRunning(task.id());
      SharedCheckRun run=repo.getSharedCheck(task.id());
      if(run==null)return null;
      AssignmentRecord a=repo.getAssignment(run.assignmentId());
      Exercise e=a==null?null:repo.getExercise(a.exerciseId());
      if(e==null)throw new IllegalStateException("Không tìm thấy bài tập để chấm.");
      return gradeExercise("check-"+run.id(),run.source(),e,e.tests(),100);
    }else if(task.kind()==TaskKind.CONTEST){
     String id=task.id();
      repo.markContestSubmissionRunning(id);ContestSubmissionRecord submission=repo.getContestSubmission(id);if(submission==null)return null;
      Exercise e=repo.getExercise(submission.exerciseId());if(e==null)throw new IllegalStateException("Không tìm thấy bài trong ngân hàng.");
      return gradeContest(submission,e);
    }else if(task.kind()==TaskKind.TRIAL){
     String id=task.id();
      repo.markTeacherTrialRunning(id);TeacherTrialRecord trial=repo.getTeacherTrial(id);if(trial==null)return null;
      Exercise e=repo.getExercise(trial.exerciseId());if(e==null)throw new IllegalStateException("Không tìm thấy bài trong ngân hàng.");
      return gradeExercise("teacher-trial-"+trial.id(),trial.source(),e,e.tests(),100);
    }else{
     String id=task.id();
      repo.markRunning(id);SubmissionRecord submission=repo.getSubmission(id);if(submission==null)return null;
      AssignmentRecord a=repo.getAssignment(submission.assignmentId());Exercise e=a==null?null:repo.getExercise(a.exerciseId());if(e==null)throw new IllegalStateException("Không tìm thấy bài tập.");
      return gradeExercise(submission.id(),submission.source(),e,e.tests(),100);
    }
 }
 private void persistUntilSaved(GradingTask task,GradingResult result)throws InterruptedException {
  int failures=0;
  try {
   while(!closed.get()) {
    try {
     switch(task.kind()) {
      case WEEK -> weekAssignments.repository.finish(task.id(),result);
      case PRACTICE -> practice.finish(task.id(),result);
      case PERSONAL -> repo.saveResult(task.id(),result);
      case CONTEST -> repo.saveContestSubmissionResult(task.id(),result);
      case TRIAL -> repo.saveTeacherTrialResult(task.id(),result);
      case SHARED -> repo.saveSharedCheckResult(task.id(),checkState(result.verdict()),result.verdict(),result.score(),result.maxScore(),result.passed(),result.total(),safe(result.feedback()),result.durationMillis());
     }
     if(failures>0)gateway.logService("Đã lưu lại kết quả kind="+task.kind()+" submission="+task.id()+" verdict="+result.verdict());
     return;
    } catch(Exception failure) {
     if(failures++==0)pendingResultWrites.incrementAndGet();
     if(failures==1||failures%30==0)gateway.logService("Chờ lưu kết quả kind="+task.kind()+" submission="+task.id()+" verdict="+result.verdict()+" attempt="+failures+" cause="+failure.getClass().getSimpleName());
     // Retain the original verdict on this bounded worker; retry writes, not grading.
     Thread.sleep(1000);
    }
   }
  } finally {
   if(failures>0)pendingResultWrites.decrementAndGet();
   if(closed.get())gateway.logService("Dừng khi chờ lưu submission="+task.id()+"; công việc chưa hoàn tất sẽ được phục hồi khi khởi động lại.");
  }
 }
 private List<Identity> currentRoomStudents(String actor,String room){List<Identity> students=new ArrayList<>();for(String item:gateway.codeSnapshot(actor,room).members()){int split=item.indexOf(" | ");if(split<1)continue;try{Identity member=gateway.identity(item.substring(0,split));if(member.role()==Identity.Role.STUDENT)students.add(member);}catch(SecurityException ignored){}}students.sort(Comparator.comparing(Identity::username,String.CASE_INSENSITIVE_ORDER));return students;}
 private List<Map<String,Object>> classProgress(String actor,String room,Identity teacher)throws SQLException{
  List<Identity> currentStudents=currentRoomStudents(actor,room);
  Map<String,Identity> currentById=new HashMap<>();for(Identity student:currentStudents)currentById.put(student.userId(),student);
  List<AssignmentRecord> assignments=repo.listAssignments(room,teacher.userId(),true);List<Map<String,Object>> reports=new ArrayList<>();
  for(AssignmentRecord assignment:assignments){
   Exercise exercise=repo.getExercise(assignment.exerciseId());
   List<SubmissionRecord> submissions=repo.listSubmissions(assignment.id(),teacher.userId(),true);
   Map<String,SubmissionRecord> latest=new LinkedHashMap<>();Map<String,SubmissionRecord> accepted=new HashMap<>();
   for(SubmissionRecord sub:submissions){latest.putIfAbsent(sub.userId(),sub);if(isAccepted(sub.verdict()))accepted.putIfAbsent(sub.userId(),sub);}
   List<AssignmentRepository.AssignmentRecipient> recipients=new ArrayList<>();
   if(repo.hasAssignmentRecipientSnapshot(assignment.id())) recipients.addAll(repo.listAssignmentRecipients(assignment.id()));
   else if("ROOM".equals(assignment.scope())) for(Identity student:currentStudents)recipients.add(new AssignmentRepository.AssignmentRecipient(student.userId(),student.username(),student.displayName()));
   else if(assignment.targetUserId()!=null&&!assignment.targetUserId().isBlank()) recipients.add(new AssignmentRepository.AssignmentRecipient(assignment.targetUserId(),assignment.targetUsername()==null?"":assignment.targetUsername(),assignment.targetUsername()==null?"":assignment.targetUsername()));
   List<Map<String,Object>> entries=new ArrayList<>();int submittedCount=0,acCount=0;
   for(AssignmentRepository.AssignmentRecipient recipient:recipients){
    SubmissionRecord last=latest.get(recipient.userId());SubmissionRecord ac=accepted.get(recipient.userId());boolean submitted=last!=null,acceptedByStudent=ac!=null;Identity current=currentById.get(recipient.userId());
    if(submitted)submittedCount++;if(acceptedByStudent)acCount++;
    entries.add(AssignmentJson.mapOf("userId",recipient.userId(),"username",recipient.username(),"displayName",recipient.displayName(),"online",current!=null,"submitted",submitted,"accepted",acceptedByStudent,"latestVerdict",last==null?"NOT_SUBMITTED":last.verdict(),"latestSubmissionId",last==null?"":last.id(),"latestSubmittedAt",last==null?0:last.submittedAt(),"acceptedSubmissionId",ac==null?"":ac.id()));
   }
   reports.add(AssignmentJson.mapOf("assignmentId",assignment.id(),"exerciseId",assignment.exerciseId(),"title",exercise==null?assignment.exerciseId():exercise.title(),"mode",assignment.mode().name(),"scope",assignment.scope(),"targetUsername",assignment.targetUsername()==null?"":assignment.targetUsername(),"status",assignment.status(),"deadlineAt",assignment.deadlineAt(),"receivedCount",recipients.size(),"submittedCount",submittedCount,"acceptedCount",acCount,"entries",entries));
  }
  return reports;
 }
 private static boolean isAccepted(String verdict){return "ACCEPTED".equalsIgnoreCase(verdict)||"AC".equalsIgnoreCase(verdict)||"PASS".equalsIgnoreCase(verdict);}
 private Identity findRoomStudent(String actor,String room,String username){for(Identity found:currentRoomStudents(actor,room))if(found.username().equalsIgnoreCase(username))return found;throw new AccessFailure("NOT_MEMBER","Không tìm thấy học sinh này trong phòng hiện tại.");}
 private AssignmentRecord requireAssignmentVisible(String id,String room,Identity who,boolean owner)throws SQLException{AssignmentRecord a=repo.getAssignment(id);if(a==null)throw new AccessFailure("NOT_FOUND","Không tìm thấy bài được giao.");if(!a.roomId().equals(room))throw new AccessFailure("NOT_FOUND","Bài không thuộc phòng hiện tại.");if(!owner&&!(a.scope().equals("ROOM")||who.userId().equals(a.targetUserId())))throw new AccessFailure("FORBIDDEN","Bạn không được giao bài này.");return a;}
 private static void requireOwner(boolean ok){if(!ok)throw new AccessFailure("FORBIDDEN","Chỉ giáo viên chủ phòng mới được thao tác này.");}
 private static Map<String,Object> assignmentMap(AssignmentRecord a){return AssignmentJson.mapOf("id",a.id(),"exerciseId",a.exerciseId(),"roomId",a.roomId(),"createdByUserId",a.createdByUserId(),"scope",a.scope(),"targetUsername",a.targetUsername()==null?"":a.targetUsername(),"createdAt",a.createdAt(),"deadlineAt",a.deadlineAt(),"status",a.status(),"mode",a.mode().name());}
 private static void requireDebug(AssignmentRecord a,Exercise e){if(e==null||a.mode()!=ExerciseMode.DEBUG_COLLAB||e.mode()!=ExerciseMode.DEBUG_COLLAB)throw new AccessFailure("WRONG_EXERCISE_MODE","Chức năng này chỉ áp dụng cho bài DEBUG_COLLAB.");}
 private static String sha256(String code){try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((code==null?"":code).getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(digest);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String checkState(String verdict){if("ACCEPTED".equals(verdict))return "PASS";if("SANDBOX_UNAVAILABLE".equals(verdict))return "SANDBOX_UNAVAILABLE";if("INTERNAL_ERROR".equals(verdict))return "INTERNAL_ERROR";return "FAILED";}
 private static Map<String,Object> sharedCheckMap(SharedCheckRun r){return AssignmentJson.mapOf("id",r.id(),"assignmentId",r.assignmentId(),"roomId",r.roomId(),"requestedBy",r.requestedByUsername(),"createdAt",r.createdAt(),"checkedVersion",r.checkedVersion(),"status",r.status(),"verdict",r.verdict(),"score",r.score(),"maxScore",r.maxScore(),"passedTests",r.passedTests(),"totalTests",r.totalTests(),"feedback",r.feedback(),"durationMs",r.durationMillis());}
 private static Map<String,Object> submissionMap(SubmissionRecord s,boolean includeResult){Map<String,Object> m=AssignmentJson.mapOf("id",s.id(),"assignmentId",s.assignmentId(),"username",s.username(),"submittedAt",s.submittedAt(),"status",s.status(),"score",s.score(),"maxScore",s.maxScore(),"verdict",s.verdict(),"passedTests",s.passedTests(),"totalTests",s.totalTests(),"feedback",s.feedback(),"submissionMode",s.submissionMode());if(includeResult)m.put("durationMs",s.durationMillis());return m;}
 private static Map<String,Object> ok(String id,Map<String,Object> data){return AssignmentJson.mapOf("requestId",id,"status","OK","data",data);}
 private static Map<String,Object> error(String id,String code,String message){return AssignmentJson.mapOf("requestId",id,"status","ERROR","errorCode",code,"message",message==null?"Yêu cầu thất bại.":message);}
 private static String safe(String m){if(m==null)return "unknown";return m.length()>250?m.substring(0,250):m;}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;builder.close();for(Thread t:workers)t.interrupt();}
 /** Dùng nội bộ cho phòng học, không mở thành API để trình duyệt gọi trực tiếp. */
 public OjImportService ojImports(){return ojImports;}
 public ProblemBuilderService problemBuilder(){return builder;}
 public Exercise classroomExercise(String id) throws SQLException { return repo.getExercise(id); }
 public List<Map<String,Object>> classroomDebugSources(Identity who) throws SQLException {
  List<Map<String,Object>> out=new ArrayList<>();
  for(Map<String,Object> row:bank.list(who)) {
   if(!"PRACTICE".equals(row.get("bankScope")) || !"PUBLISHED".equals(row.get("publication")) || !Boolean.TRUE.equals(row.get("canManage")))continue;
   Exercise e=repo.getExercise(AssignmentJson.text(row,"id",""));
   String issue=e==null?"Không tìm thấy nội dung bài":e.debugPreparationIssue();boolean ready=issue.isBlank();
   row.put("debugEligible",ready); row.put("debugReason",issue); out.add(row);
  }
  return out;
 }
 public Exercise classroomDebugSource(Identity who,String id) throws SQLException {
  bank.requirePracticePublished(id); Map<String,Object> meta=bank.info(id,who);
  if(!Boolean.TRUE.equals(meta.get("canManage")))throw new SecurityException("Chỉ sử dụng lời giải của bài bạn được quản lý.");
  return repo.getExercise(id);
 }
 public void requirePublishedQuestion(String id)throws SQLException{bank.requirePublished(id);}
 private synchronized void rejudgeOrganization(Identity who,String contest,List<String> ids,String reason)throws Exception{
  if(queue.remainingCapacity()<ids.size())throw new AccessFailure("QUEUE_FULL","Hàng đợi không đủ chỗ; chưa nhận lệnh chấm lại.");
  for(String id:organization.repository.requeue(who,contest,ids,reason))if(!queue.offer(new GradingTask(id,TaskKind.CONTEST)))throw new AccessFailure("QUEUE_FULL","Bài đã lưu trạng thái chờ; hàng đợi cần khôi phục.");
 }
 private enum TaskKind { PERSONAL, SHARED, CONTEST, TRIAL, PRACTICE, WEEK }
 private record GradingTask(String id,TaskKind kind){}
 private static final class AccessFailure extends RuntimeException{final String code;AccessFailure(String code,String message){super(message);this.code=code;}}
}
