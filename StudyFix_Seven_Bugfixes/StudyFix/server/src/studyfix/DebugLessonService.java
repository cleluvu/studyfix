package studyfix;

import studyfix.assignment.grading.GradingSandbox;
import studyfix.assignment.model.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Bounded asynchronous validation; only sandbox-verified snapshots can become a lesson. */
final class DebugLessonService implements AutoCloseable {
    interface Bank {
        List<Map<String,Object>> list(Identity who) throws SQLException;
        Exercise get(Identity who, String id) throws SQLException;
    }
    private record Job(String room, String owner, long createdAt, Map<String,Object> result) { }
    private final Bank bank;
    private final DebugLessonRepository repo;
    private final GradingSandbox sandbox;
    private final Map<String,Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4), r -> {
        Thread t = new Thread(r,"studyfix-debug-lesson-validator"); t.setDaemon(true); return t;
    });
    private volatile boolean closed;
    DebugLessonService(Store store, Bank bank, GradingSandbox sandbox) {
        this.repo = new DebugLessonRepository(store); this.bank = bank; this.sandbox = sandbox;
    }
    List<Map<String,Object>> sources(Identity who) throws SQLException { return bank.list(who); }
    Map<String,Object> source(Identity who,String id) throws SQLException {
        Exercise e=bank.get(who,id); eligible(e);
        Map<String,Object> view=new LinkedHashMap<>(e.publicView()); view.put("referenceSolution",e.referenceSolution()); return view;
    }
    static void eligible(Exercise e) {
        if(e==null)throw new IllegalArgumentException("Không tìm thấy nội dung bài.");
        String issue=e.debugPreparationIssue();
        if(!issue.isBlank())throw new IllegalArgumentException(issue+". Chức năng soạn bài lỗi hiện chỉ hỗ trợ Program/Main.java.");
    }    private static String source(String code) {
        if (code == null || code.isBlank() || code.getBytes(StandardCharsets.UTF_8).length > 32000) throw new IllegalArgumentException("Code phải có nội dung và tối đa 32000 byte UTF-8.");
        return code;
    }
    synchronized Map<String,Object> create(String room, Identity who, Map<String,Object> body) throws SQLException {
        if (closed) throw new IllegalStateException("Dịch vụ tạo lỗi đã dừng.");
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(e -> now - e.getValue().createdAt() > TimeUnit.MINUTES.toMillis(30) && !Set.of("QUEUED","RUNNING").contains(e.getValue().result().get("status")));
        if (jobs.values().stream().anyMatch(j -> j.room().equals(room) && Set.of("QUEUED","RUNNING").contains(j.result().get("status")))) throw new IllegalStateException("Phòng đang kiểm chứng một bài. Vui lòng đợi.");
        if (jobs.size() >= 64) throw new IllegalStateException("Có nhiều bản xem trước. Vui lòng thử lại sau.");
        String sourceId = text(body,"sourceId","");
        Exercise e = bank.get(who,sourceId); eligible(e);
        String type = text(body,"type","MIXED"); int count = integer(body,"count",1);
        if (!Set.of("CONDITION","ARITHMETIC","MIXED").contains(type) || count < 1 || count > 3) throw new IllegalArgumentException("Chọn nhóm lỗi hợp lệ và từ 1 đến 3 lỗi.");
        String custom = body.containsKey("code") ? source(text(body,"code","")) : null;
        if (custom != null && custom.equals(e.referenceSolution())) throw new IllegalArgumentException("Code sửa lỗi phải khác lời giải chuẩn.");
        String excluded="";
        String previousId=text(body,"excludePreviewId","");
        if(!previousId.isBlank()) {
            if(custom!=null)throw new IllegalArgumentException("Không kết hợp chỉnh sửa thủ công với tạo biến thể khác.");
            Map<String,Object> previous=owned(room,who,previousId);
            if(!sourceId.equals(previous.get("sourceId")))throw new IllegalArgumentException("Bản trước không thuộc cùng bài nguồn.");
            excluded=text(previous,"starterCode","");
        }
        final String excludedCode=excluded;
        String id = "DEBUG_SNAPSHOT_" + UUID.randomUUID();
        Job job = new Job(room,who.userId(),now,mapOf("id",id,"status","QUEUED")); jobs.put(id,job);
        try { worker.execute(() -> validate(id,job,e,sourceId,type,count,custom,excludedCode)); }
        catch (RejectedExecutionException full) { jobs.remove(id); throw new IllegalStateException("Hàng đợi kiểm chứng đầy. Hãy thử lại sau."); }
        return job.result();
    }
    private void update(String id, Job job, Map<String,Object> result) { jobs.put(id,new Job(job.room(),job.owner(),job.createdAt(),result)); }
    private void validate(String id, Job job, Exercise e, String sourceId, String type, int count, String custom, String excludedCode) {
        try {
            update(id,job,mapOf("id",id,"status","RUNNING"));
            GradingResult correct = sandbox.grade(id+"-reference",e.referenceSolution(),e.tests(),100);
            if (!correct.verdict().equals("ACCEPTED") || correct.total() != e.tests().size() || correct.passed() != correct.total())
                throw new IllegalStateException("Lời giải chuẩn chưa đạt toàn bộ testcase ("+correct.verdict()+"). Kiểm tra code chuẩn, testcase và Docker.");
            List<DebugCodeMutator.Variant> candidates = custom == null ? DebugCodeMutator.variants(e.referenceSolution(),type,count,UUID.randomUUID().getLeastSignificantBits()) : List.of(new DebugCodeMutator.Variant(custom,List.of()));
            candidates=candidates.stream().filter(candidate->!candidate.code().equals(excludedCode)).limit(6).toList();
            if(candidates.isEmpty()&&!excludedCode.isEmpty())throw new IllegalStateException("Không có biến thể khác với cấu hình này. Bản trước được giữ nguyên; hãy đổi nhóm/số lỗi hoặc chỉnh sửa code rồi kiểm chứng lại.");
            int attempt = 0;
            for (DebugCodeMutator.Variant candidate : candidates) {
                if (closed || Thread.currentThread().isInterrupted()) return;
                GradingResult broken = sandbox.grade(id+"-candidate-"+(++attempt),candidate.code(),e.tests(),100);
                if (Set.of("JUDGE_ERROR","SANDBOX_UNAVAILABLE").contains(broken.verdict())) throw new IllegalStateException("Sandbox chưa sẵn sàng. Kiểm tra Docker và image Java, rồi thử lại.");
                // Reject compile errors, runtime failures, timeouts and equivalent mutations.
                if (!broken.verdict().equals("WRONG_ANSWER") || broken.passed() >= broken.total() || broken.total() != e.tests().size()) continue;
                Map<String,Object> snapshot = DebugLessonRepository.snapshot(e,id,candidate.code());
                snapshot.put("roomId",job.room()); snapshot.put("ownerId",job.owner()); snapshot.put("sourceId",sourceId);
                snapshot.put("createdAt",job.createdAt()); snapshot.put("status","READY"); snapshot.put("type",custom == null ? type : "CUSTOM"); snapshot.put("count",count);
                snapshot.put("changes",candidate.edits().stream().map(edit -> mapOf("line",edit.line(),"before",edit.before(),"after",edit.after())).toList());
                snapshot.put("diff",CodeDiff.display(CodeDiff.between(e.referenceSolution(),candidate.code())));
                snapshot.put("validation",mapOf("referenceVerdict",correct.verdict(),"brokenVerdict",broken.verdict(),"passed",broken.passed(),"total",broken.total()));
                if (closed) return;
                repo.save(id,job.room(),job.owner(),snapshot);
                update(id,job,teacherView(snapshot)); return;
            }
            throw new IllegalStateException(excludedCode.isEmpty()?"Chưa tìm được bản lỗi logic bị testcase phát hiện. Hãy đổi nhóm/số lỗi hoặc sửa code rồi kiểm chứng lại.":"Không tìm được biến thể khác vượt qua kiểm chứng. Bản trước được giữ nguyên; hãy đổi nhóm/số lỗi hoặc chỉnh sửa code.");
        } catch (Exception ex) {
            if (!closed) update(id,job,mapOf("id",id,"status","FAILED","message",ex instanceof IllegalArgumentException || ex instanceof IllegalStateException ? ex.getMessage() : "Kiểm chứng gặp lỗi. Kiểm tra Docker và thử lại."));
        }
    }
    List<Map<String,Object>> recent(String room,Identity who) throws SQLException {
        Map<String,Map<String,Object>> out=new LinkedHashMap<>();
        for(Map<String,Object> row:repo.recent(room,who.userId()))out.put(text(row,"id",""),row);
        jobs.forEach((id,job)->{
            if(job.room().equals(room)&&job.owner().equals(who.userId())&&!out.containsKey(id))out.put(id,mapOf("id",id,"title",text(job.result(),"title","Bản xem trước"),"status",job.result().get("status"),"createdAt",job.createdAt()));
        });
        return out.values().stream().sorted(Comparator.comparingLong((Map<String,Object> x)->number(x,"createdAt",0)).reversed()).limit(20).toList();
    }
    Map<String,Object> status(String room, Identity who, String id) throws SQLException {
        Job job = jobs.get(id);
        if (job != null) {
            if (!job.room().equals(room) || !job.owner().equals(who.userId())) throw new SecurityException("Bản xem trước không thuộc phòng và giáo viên này.");
            return job.result();
        }
        return teacherView(owned(room,who,id));
    }
    Map<String,Object> owned(String room, Identity who, String id) throws SQLException {
        Map<String,Object> data = repo.get(id);
        if (data == null || !room.equals(data.get("roomId")) || !who.userId().equals(data.get("ownerId"))) throw new SecurityException("Không tìm thấy bản đã kiểm chứng trong phòng này.");
        return data;
    }
    Exercise exercise(String id) throws SQLException {
        Map<String,Object> data = repo.get(id); return data == null ? null : DebugLessonRepository.exercise(data);
    }
    private static Map<String,Object> teacherView(Map<String,Object> data) {
        Map<String,Object> view = new LinkedHashMap<>(data); view.remove("tests"); view.remove("ownerId"); return view;
    }
    @Override public void close() {
        closed = true; worker.shutdownNow();
        try { worker.awaitTermination(5,TimeUnit.SECONDS); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}