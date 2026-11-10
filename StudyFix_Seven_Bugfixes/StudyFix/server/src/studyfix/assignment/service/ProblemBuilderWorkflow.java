package studyfix.assignment.service;

import studyfix.Identity;
import studyfix.assignment.persistence.*;
import studyfix.assignment.grading.*;
import studyfix.assignment.model.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.*;
import java.util.zip.*;
import java.io.*;
import static studyfix.assignment.protocol.AssignmentJson.*;
import studyfix.assignment.persistence.ProblemBuilderRepository.Failure;

/** Steps 4–7. Bounded background runs, no student code is executed in the host JVM. */
public final class ProblemBuilderWorkflow implements AutoCloseable {
 public interface Directory {List<Map<String,Object>> teachers(Identity who)throws SQLException;}
 private final ProblemBuilderRepository drafts;private final ProblemBuilderAssets assets;private final BuilderWorkflowRepository workflow;private final String image;private final Directory directory;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"builder-validation");t.setDaemon(true);return t;});
 public ProblemBuilderWorkflow(AssignmentRepository repo,ProblemBuilderRepository drafts,ProblemBuilderAssets assets,String image,Directory directory)throws SQLException{this.drafts=drafts;this.assets=assets;this.image=image;this.directory=directory;workflow=new BuilderWorkflowRepository(repo,drafts);}
 public Map<String,Object> status(Identity who,String id)throws SQLException{return workflow.status(who,id);}
 public Map<String,Object> snapshot(String exercise)throws SQLException{return workflow.snapshot(exercise);}
 private Map<String,Object> record(Identity who,Map<String,Object> req)throws SQLException{var d=drafts.get(who,text(req,"draftId",""));if(integer(req,"expectedRevision",-1)!=integer(d,"revision",0))throw new Failure("CONFLICT","Nháp đã đổi; lưu/mở lại trước khi thực hiện.");return d;}
 public Map<String,Object> handle(String type,Map<String,Object> req,Identity who)throws SQLException{
  String id=text(req,"draftId","");
  switch(type){
   case "BUILDER_STATUS":return status(who,id);
   case "BUILDER_REVIEWERS":return mapOf("teachers",directory.teachers(who),"selfApproval",true);
   case "BUILDER_REVIEW_LIST":return mapOf("reviews",workflow.reviews(who));
   case "BUILDER_REVIEW_GET":return mapOf("draft",workflow.reviewGet(who,id));
   case "BUILDER_REVIEW_DECIDE":{workflow.decide(who,id,text(req,"contentHash",""),Boolean.TRUE.equals(req.get("approve")));return Map.of();}
   case "BUILDER_EXPORT":{var d=drafts.get(who,id);return mapOf("format","studyfix-builder-v2","payload",d.get("payload"),"contentHash",d.get("contentHash"),"warning","Teacher export chứa test ẩn và lời giải chuẩn; không gửi sinh viên.");}
   case "BUILDER_REVIEW":{var d=record(who,req);String reviewer=text(req,"reviewerId","");boolean self=Boolean.TRUE.equals(req.get("approveSelf"));if(!self&&directory.teachers(who).stream().noneMatch(t->reviewer.equals(t.get("id"))))throw new Failure("INVALID_REQUEST","Chọn tài khoản giảng viên kiểm duyệt đang hoạt động.");workflow.review(who,id,integer(d,"revision",0),reviewer,self);return status(who,id);}
   case "BUILDER_PUBLISH":{record(who,req);return workflow.publish(who,id,integer(req,"expectedRevision",-1));}
   case "BUILDER_ARCHIVE":{record(who,req);workflow.archive(who,id,integer(req,"expectedRevision",-1));return status(who,id);}
   case "BUILDER_TEST_IMPORT":{record(who,req);var a=assets.get(who,text(req,"assetId",""));if(!a.draftId().equals(id)||!a.media().equals("application/zip"))throw new Failure("INVALID_REQUEST","Chọn ZIP của bản nháp này.");return mapOf("tests",importTests(a.bytes()),"preview",true);}
   case "BUILDER_TEST_GENERATE":{record(who,req);int count=integer(req,"count",5),min=integer(req,"min",-10),max=integer(req,"max",10),size=integer(req,"size",5);if(count<1||count>50||min>max||(long)max-min>10000000||size<1||size>100)throw new Failure("INVALID_REQUEST","Số test 1–50, kích thước mảng 1–100, khoảng giá trị tối đa 10 triệu.");String format=text(req,"format","INTEGER");if(!Set.of("INTEGER","ARRAY").contains(format))throw new Failure("INVALID_REQUEST","Generator chỉ hỗ trợ số nguyên hoặc mảng số nguyên.");var random=new Random((long)BuilderProblem.number(req,"seed",1));List<Map<String,Object>> tests=new ArrayList<>();Set<String> inputs=new HashSet<>();for(int attempt=0;tests.size()<count&&attempt<count*100;attempt++){String input;if(format.equals("INTEGER"))input=(min+random.nextInt(max-min+1))+"\n";else{StringJoiner values=new StringJoiner(" ");for(int j=0;j<size;j++)values.add(String.valueOf(min+random.nextInt(max-min+1)));input=size+"\n"+values+"\n";}if(inputs.add(input))tests.add(mapOf("id","GEN"+(tests.size()+1),"input",input,"expectedOutput","","weight",1,"hidden",true,"group","Basic"));}if(tests.size()!=count)throw new Failure("INVALID_REQUEST","Khoảng quá nhỏ để tạo đủ test khác nhau.");return mapOf("tests",tests,"preview",true,"seed",BuilderProblem.number(req,"seed",1));}
   case "BUILDER_VALIDATE":case "BUILDER_EXPECTED":{
    var d=record(who,req);var p=map(d.get("payload"));BuilderProblem.require(p,type.equals("BUILDER_VALIDATE")?5:4);if(text(p,"reference","").isBlank())throw new Failure("STEP_INVALID","Nhập lời giải chuẩn trước khi chạy.");
    synchronized(worker){if(worker.getQueue().remainingCapacity()==0)throw new Failure("BUSY","Hàng đợi kiểm định đầy; thử lại sau.");String hash=text(d,"contentHash",""),job=workflow.createJob(who,id,hash);try{worker.execute(()->run(who,id,hash,job,p,type.equals("BUILDER_EXPECTED")));}catch(RejectedExecutionException e){workflow.job(job,"FAILED","Không xếp được lượt chạy.",Map.of());throw new Failure("BUSY","Hàng đợi đã đóng hoặc đầy.");}return mapOf("jobId",job,"status","QUEUED");}
   }
   default:return null;
  }
 }
 private static Map<String,Object> result(GradingResult r){return mapOf("verdict",r.verdict(),"score",r.score(),"passed",r.passed(),"total",r.total(),"feedback",r.feedback(),"durationMs",r.durationMillis(),"tests",r.publicTests().stream().limit(10).map(row->mapOf("hidden",row.get("hidden"),"passed",row.get("passed"),"durationMs",row.get("durationMs"))).toList());}
 private void run(Identity who,String id,String hash,String job,Map<String,Object> p,boolean expected){
  try{
   var checks=new ArrayList<Map<String,Object>>();workflow.job(job,"RUNNING","Đang biên dịch template trong Docker.",Map.of());var sandbox=new OjJavaSandbox(image);var exercise=BuilderProblem.exercise(p,"builder-dry-run");var config=BuilderProblem.config(p);
   if(expected){config.put("capture_output",true);config.put("show_compile",true);var r=sandbox.grade(exercise,text(p,"reference",""),exercise.tests(),100,config);List<Map<String,Object>> tests=new ArrayList<>();if(r.publicTests().size()!=exercise.tests().size())throw new Failure("REFERENCE_FAILED","Không chạy đủ test: "+r.feedback());for(int i=0;i<r.publicTests().size();i++){var row=r.publicTests().get(i);if(!Boolean.TRUE.equals(row.get("executionOk")))throw new Failure("REFERENCE_FAILED","Lời giải chuẩn lỗi khi chạy test "+(i+1)+".");var t=new LinkedHashMap<>(map(list(p.get("tests")).get(i)));String output=text(row,"observedOutput","");if(output.length()>16000)throw new Failure("INVALID_REQUEST","Expected tạo ra vượt 16000 ký tự.");t.put("expectedOutput",output);tests.add(t);}if(stringify(tests).getBytes(StandardCharsets.UTF_8).length>24576)throw new Failure("INVALID_REQUEST","Generated expected exceeds 24 KiB; reduce the test suite.");workflow.job(job,"DONE","Đã tạo expected. Xem trước và xác nhận áp dụng; chưa sửa testcase.",mapOf("kind","EXPECTED","tests",tests));return;}
   var compile=new DockerJavaSandbox(image,2,45).compileTemplate(BuilderProblem.files(p));boolean compiled="COMPILED".equals(compile.get("verdict"));checks.add(mapOf("name","Template biên dịch Java 17","status",compiled?"PASS":"FAIL","detail",compile));
   workflow.job(job,"RUNNING","Đang chấm lời giải chuẩn với toàn bộ test.",mapOf("checks",checks));config.put("show_compile",true);var reference=sandbox.grade(exercise,text(p,"reference",""),exercise.tests(),100,config);boolean refOk=reference.verdict().equals("ACCEPTED")&&reference.passed()==exercise.tests().size();checks.add(mapOf("name","Lời giải chuẩn vượt toàn bộ test","status",refOk?"PASS":"FAIL","detail",result(reference)));
   boolean detected=false;
   if(!compiled||!refOk)checks.add(mapOf("name","Bộ test phát hiện lời giải sai mẫu","status","SKIPPED","detail","Chưa đánh giá lời giải sai vì template/lời giải chuẩn chưa chạy đạt. Khắc phục lỗi trước rồi kiểm định lại."));
   else {   workflow.job(job,"RUNNING","Đang kiểm tra lời giải sai mẫu.",mapOf("checks",checks));String incorrect=text(p,"incorrectSample","");if(incorrect.isBlank())incorrect=exercise.template();var bad=sandbox.grade(exercise,incorrect,exercise.tests(),100,config);detected=Set.of("WRONG_ANSWER","COMPILE_ERROR","RUNTIME_ERROR","TIME_LIMIT_EXCEEDED","MEMORY_LIMIT_EXCEEDED","OUTPUT_LIMIT_EXCEEDED").contains(bad.verdict());checks.add(mapOf("name","Bộ test phát hiện lời giải sai mẫu","status",detected?"PASS":"FAIL","detail",result(bad)));}
   checks.add(mapOf("name","Metadata, cấu trúc, file, testcase và tổng điểm","status","PASS","detail","Đã kiểm tra hợp đồng bước 1–5 trên server."));boolean hidden=exercise.tests().stream().anyMatch(Exercise.TestCase::hidden);checks.add(mapOf("name","Có testcase ẩn","status",hidden?"PASS":"WARNING","detail",hidden?"Có Public và Hidden.":"Chỉ có test Public; nên bổ sung test ẩn trước khi dùng thi."));
   boolean stale=!hash.equals(drafts.get(who,id).get("contentHash"));var report=mapOf("kind","VALIDATION","passed",compiled&&refOk&&detected&&!stale,"referencePassed",refOk,"wrongDetected",detected,"stale",stale,"checks",checks,"contentHash",hash,"image",image,"javaTarget",17,"educationalReview","PENDING");drafts.recordCheck(who,id,hash,"VALIDATION",report);workflow.job(job,"DONE",stale?"Nháp đã đổi trong khi chạy; kết quả hết hiệu lực.":Boolean.TRUE.equals(report.get("passed"))?"Kiểm định đạt; cần duyệt nội dung trước phát hành.":"Kiểm định chưa đạt; xem chi tiết để sửa.",report);
  }catch(Exception e){try{workflow.job(job,"FAILED","Không hoàn tất lượt chạy.",mapOf("message",e instanceof Failure?e.getMessage():"Môi trường kiểm định lỗi hoặc không sẵn sàng; kiểm tra Docker và thử lại."));}catch(SQLException ignored){}}
 }
 private static List<Map<String,Object>> importTests(byte[] bytes){
  ProblemTemplate.validateArchive(bytes,220);Map<String,String> inputs=new TreeMap<>(),outputs=new TreeMap<>();int total=0,count=0;
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){ZipEntry entry;Set<String> names=new HashSet<>();while((entry=zip.getNextEntry())!=null){if(++count>220)throw new Failure("INVALID_REQUEST","ZIP tối đa 220 entry.");String name=entry.getName();if(name.startsWith("/")||name.contains("\\")||Arrays.stream(name.split("/",-1)).anyMatch(s->s.equals("..")||s.equals(".")))throw new Failure("INVALID_REQUEST","Đường dẫn ZIP không an toàn.");if(entry.isDirectory()){if(zip.readNBytes(1).length!=0)throw new Failure("INVALID_REQUEST","Directory entry cannot contain data.");continue;}if(!name.matches("[A-Za-z0-9_/-]+\\.(in|out|snippet|expected)")||!names.add(name.toLowerCase(Locale.ROOT)))throw new Failure("INVALID_REQUEST","Chỉ nhận cặp .in/.out hoặc .snippet/.expected, không trùng đường dẫn.");byte[] data=zip.readNBytes(32769);total+=data.length;if(data.length>32768||total>256*1024)throw new Failure("INVALID_REQUEST","Test ZIP vượt giới hạn 32 KiB/file hoặc 256 KiB tổng.");String content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(data)).toString();if(content.indexOf(0)>=0||content.length()>16000)throw new Failure("INVALID_REQUEST","Test phải là UTF-8, tối đa 16000 ký tự.");String stem=name.substring(0,name.lastIndexOf('.'));if(name.endsWith(".in")||name.endsWith(".snippet")){if(inputs.put(stem,content)!=null)throw new Failure("INVALID_REQUEST","Trùng input.");}else if(outputs.put(stem,content)!=null)throw new Failure("INVALID_REQUEST","Trùng expected.");}}
  catch(IOException e){throw new Failure("INVALID_REQUEST","Không đọc được ZIP UTF-8.");}
  if(inputs.isEmpty()||inputs.size()>100||!inputs.keySet().equals(outputs.keySet()))throw new Failure("INVALID_REQUEST","Cần 1–100 cặp input/expected đầy đủ cùng tên.");List<Map<String,Object>> result=new ArrayList<>();for(String stem:inputs.keySet())result.add(mapOf("id","IMP"+(result.size()+1),"input",inputs.get(stem),"expectedOutput",outputs.get(stem),"weight",1,"hidden",stem.startsWith("hidden/"),"group","Basic"));return result;
 }
 @Override public void close(){worker.shutdownNow();}
}
