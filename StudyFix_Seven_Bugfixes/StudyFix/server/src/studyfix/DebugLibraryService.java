package studyfix;

import studyfix.assignment.grading.GradingSandbox;
import studyfix.assignment.model.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Manual preparation outside a live room; no automatic mutation controls. */
final class DebugLibraryService implements AutoCloseable {
    private final DebugLessonService.Bank bank;
    private final DebugLibraryRepository repo;
    private final DebugLessonRepository lessons;
    private final GradingSandbox sandbox;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"studyfix-debug-library");t.setDaemon(true);return t;});
    private volatile boolean closed;
    DebugLibraryService(Store store,DebugLessonService.Bank bank,GradingSandbox sandbox){this.bank=bank;this.repo=new DebugLibraryRepository(store);this.lessons=new DebugLessonRepository(store);this.sandbox=sandbox;}
    private static void teacher(Identity who){if(who==null||who.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ giáo viên được chuẩn bị và xem danh sách bài lỗi.");}
    List<Map<String,Object>> sources(Identity who)throws SQLException{teacher(who);return bank.list(who);}
    Map<String,Object> source(Identity who,String id)throws SQLException{teacher(who);Exercise e=bank.get(who,id);DebugLessonService.eligible(e);Map<String,Object> out=new LinkedHashMap<>(e.publicView());out.put("referenceSolution",e.referenceSolution());return out;}
    List<Map<String,Object>> list(Identity who)throws SQLException{teacher(who);return repo.list(who.userId());}
    Map<String,Object> get(Identity who,String id)throws SQLException{teacher(who);Map<String,Object> out=new LinkedHashMap<>(repo.get(id,who.userId()));out.remove("tests");return out;}
    synchronized Map<String,Object> save(Identity who,String id,Map<String,Object> request)throws SQLException {
        teacher(who);if(closed)throw new IllegalStateException("Dịch vụ đã dừng.");
        if(repo.busy(who.userId()))throw new IllegalStateException("Bạn đang có bài được kiểm chứng. Vui lòng đợi rồi lưu tiếp.");
        String title=text(request,"title","").strip(),code=text(request,"code","");
        if(title.isBlank()||title.length()>160)throw new IllegalArgumentException("Tên bài lỗi cần từ 1 đến 160 ký tự.");
        if(code.isBlank()||code.getBytes(StandardCharsets.UTF_8).length>32000)throw new IllegalArgumentException("Code cần có nội dung và tối đa 32000 byte UTF-8.");
        long expected;Map<String,Object> data;
        if(id.isBlank()){
            id="DEBUG_LIBRARY_"+UUID.randomUUID();expected=0;
            String sourceId=text(request,"sourceId","");Exercise e=bank.get(who,sourceId);DebugLessonService.eligible(e);
            data=DebugLessonRepository.snapshot(e,id,code);data.put("sourceId",sourceId);data.put("sourceTitle",e.title());
        }else {
            data=new LinkedHashMap<>(repo.get(id,who.userId()));expected=number(request,"expectedVersion",-1);
            if(expected!=number(data,"version",0))throw new IllegalStateException("Bài đã được sửa ở phiên khác. Hãy mở lại trước khi lưu.");
            if(request.containsKey("sourceId")&&!text(request,"sourceId","").equals(text(data,"sourceId","")))throw new IllegalArgumentException("Muốn đổi bài nguồn, hãy tạo bài lỗi mới.");
            for(String key:List.of("status","version","validation","message","createdAt","updatedAt"))data.remove(key);
        }
        if(code.equals(text(data,"referenceSolution","")))throw new IllegalArgumentException("Code đang giống code chuẩn. Hãy tự tạo lỗi trước khi lưu.");
        data.put("title",title);data.put("starterCode",code);data.put("template",code);data.put("type","MANUAL");data.put("changes",List.of());
        data.put("diff",CodeDiff.display(CodeDiff.between(text(data,"referenceSolution",""),code)));
        repo.save(id,who.userId(),data,expected);
        final String savedId=id;final long version=expected+1;final Map<String,Object> snapshot=data;
        try{worker.execute(()->validate(savedId,who.userId(),version,snapshot));}
        catch(RejectedExecutionException full){snapshot.put("message","Hàng đợi kiểm chứng đầy. Mở lại bài và lưu để thử lại.");repo.result(id,who.userId(),version,"FAILED",snapshot);throw new IllegalStateException("Hàng đợi kiểm chứng đầy. Code đã lưu trong danh sách để bạn thử lại.");}
        return get(who,id);
    }
    private void validate(String id,String owner,long version,Map<String,Object> data){
        try{
            if(closed)return;repo.result(id,owner,version,"RUNNING",data);
            Exercise e=DebugLessonRepository.exercise(data);
            GradingResult correct=sandbox.grade(id+"-"+version+"-reference",e.referenceSolution(),e.tests(),100);
            if(!correct.verdict().equals("ACCEPTED")||correct.passed()!=e.tests().size())throw new IllegalStateException("Code chuẩn chưa đạt toàn bộ testcase ("+correct.verdict()+"). Kiểm tra code chuẩn, testcase và Docker.");
            if(closed)return;
            GradingResult broken=sandbox.grade(id+"-"+version+"-buggy",e.starterCode(),e.tests(),100);
            if(broken.verdict().equals("ACCEPTED"))throw new IllegalStateException("Code bạn soạn vẫn đạt toàn bộ testcase. Hãy sửa để tạo lỗi rồi lưu lại; code hiện tại đã được giữ trong danh sách.");
            if(!Set.of("WRONG_ANSWER","COMPILE_ERROR","RUNTIME_ERROR","TIME_LIMIT_EXCEEDED","MEMORY_LIMIT_EXCEEDED","OUTPUT_LIMIT_EXCEEDED").contains(broken.verdict()))throw new IllegalStateException("Chưa kiểm chứng được do dịch vụ chấm ("+broken.verdict()+"). Kiểm tra Docker rồi lưu lại.");
            data.put("validation",mapOf("referenceVerdict",correct.verdict(),"brokenVerdict",broken.verdict(),"passed",broken.passed(),"total",e.tests().size()));
            if(!closed)repo.result(id,owner,version,"READY",data);
        }catch(Exception failure){if(!closed)try{data.put("message",failure instanceof IllegalStateException?failure.getMessage():"Kiểm chứng gặp lỗi. Code đã lưu; kiểm tra Docker rồi lưu lại.");repo.result(id,owner,version,"FAILED",data);}catch(SQLException ignored){}}
    }
    String copyToRoom(Identity who,String id,String room,long expectedVersion)throws SQLException {
        teacher(who);Map<String,Object> data=new LinkedHashMap<>(repo.get(id,who.userId()));
        if(number(data,"version",0)!=expectedVersion)throw new IllegalStateException("Bài lỗi đã thay đổi. Mở lại danh sách rồi chọn đúng phiên bản.");
        if(!"READY".equals(data.get("status")))throw new IllegalStateException("Chỉ chọn bài đã kiểm chứng và sẵn sàng giao.");
        String snapshotId="DEBUG_SNAPSHOT_"+UUID.randomUUID();data.put("libraryId",id);data.put("libraryVersion",data.remove("version"));data.put("id",snapshotId);data.put("roomId",room);data.put("ownerId",who.userId());data.put("createdAt",System.currentTimeMillis());
        lessons.save(snapshotId,room,who.userId(),data);return snapshotId;
    }
    void delete(Identity who,String id,long expected)throws SQLException{teacher(who);repo.get(id,who.userId());repo.delete(id,who.userId(),expected);}
    @Override public void close(){closed=true;worker.shutdownNow();try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}