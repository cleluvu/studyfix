package studyfix;

import studyfix.assignment.grading.GradingSandbox;
import studyfix.assignment.model.*;
import studyfix.assignment.protocol.AssignmentJson;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static studyfix.assignment.protocol.AssignmentJson.*;
import static studyfix.ClassroomRepository.*;

/** Tách code Master thành nhánh riêng cho sinh viên, lưu kết quả kiểm tra rồi cho giáo viên chọn nhánh để gộp. */
final class ClassroomService implements AutoCloseable {
    @FunctionalInterface interface Exercises { Exercise get(String id) throws SQLException; }
    private final Server core;
    private final ClassroomRepository repo;
    private final Exercises exercises;
    private final GradingSandbox sandbox;
    private final ThreadPoolExecutor grader;
    private final ConcurrentMap<String,Check> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    ClassroomService(Server core, Exercises exercises, GradingSandbox sandbox) {
        this.core=core;this.repo=new ClassroomRepository(core.store());this.exercises=exercises;this.sandbox=sandbox;
        grader=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"studyfix-classroom-grader");t.setDaemon(true);return t;});
    }
    Map<String,Object> view(Room room, ClientHandler client) throws SQLException {
        synchronized(room) {
            member(room,client);
            Session s=repo.session(room.getRoomId());
            boolean owner=room.isOwner(client.identity());
            List<Branch> branches=repo.branches(s);
            if(client.identity().role()==Identity.Role.STUDENT && s.phase().equals("BRANCHING")
                    && branches.stream().noneMatch(b->b.userId().equals(client.identity().userId()))) {
                if(branches.size()>=128)throw new IllegalStateException("Phiên học đã đủ 128 nhánh.");
                s=new Session(s.room(),s.phase(),s.round(),s.exerciseId(),s.baseVersion(),System.currentTimeMillis());
                Branch b=branch(room,s,client.identity());
                repo.change(s,client.identity(),"JOIN_BRANCH",client.identity().username(),List.of(b),null);
                branches=repo.branches(s);
            }
            List<Map<String,Object>> rows=new ArrayList<>();
            Set<String> online=new HashSet<>();
            for(Room.MemberView m:room.memberDetails()) if(m.online())online.add(m.userId());
            for(Branch b:branches) if(owner||b.userId().equals(client.identity().userId())) {
                List<Check> checks=repo.checks(s,b.userId(),owner?1:20);
                Map<String,Object> latest=checks.isEmpty()?Map.of():checkMap(checks.getFirst(),false);
                rows.add(mapOf("userId",b.userId(),"username",b.username(),"displayName",b.name(),"code",owner?"":b.code(),
                        "codePreview",owner?b.code().lines().limit(14).reduce("",(a,line)->a+line.substring(0,Math.min(140,line.length()))+"\n"):"",
                        "version",b.version(),"baseVersion",b.baseVersion(),"updatedAt",b.updatedAt(),"online",online.contains(b.userId()),
                        "latestCheck",latest,"resultStale",!checks.isEmpty()&&checks.getFirst().branchVersion()!=b.version(),
                        "checks",owner?List.of():checks.stream().map(x->checkMap(x,false)).toList()));
            }
            Exercise exercise=s.exerciseId().isBlank()?null:exercises.get(s.exerciseId());
            Map<String,Object> result=mapOf("phase",s.phase(),"round",s.round(),"exerciseId",s.exerciseId(),"exercise",exercise==null?Map.of():exercise.publicView(),
                    "baseVersion",s.baseVersion(),"masterVersion",room.snapshot().version(),"updatedAt",s.updatedAt(),"isOwner",owner,"branches",rows,
                    "events",owner?repo.report(s.room()):List.of(),"chat",repo.chat(s.room()),"queueSize",grader.getQueue().size());
            return result;
        }
    }
    Map<String,Object> action(Room room, ClientHandler client, Map<String,Object> body) throws SQLException {
        synchronized(room) {
            if(closed.get())throw new IllegalStateException("Dịch vụ phòng học đã dừng.");
            member(room,client);
            Identity who=client.identity();
            Session s=repo.session(room.getRoomId());
            String action=text(body,"action","").toUpperCase(Locale.ROOT);
            if(action.equals("BRANCH_SAVE")||action.equals("BRANCH_CHECK")) {
                if(who.role()!=Identity.Role.STUDENT)throw new SecurityException("Chỉ học sinh làm việc trên nhánh cá nhân.");
                phase(s,"BRANCHING");
                sameRound(s,body);
                Branch b=repo.branches(s).stream().filter(x->x.userId().equals(who.userId())).findFirst()
                        .orElseThrow(()->new IllegalStateException("Chưa có nhánh. Đồng bộ trạng thái phòng trước."));
                long expected=number(body,"expectedVersion",-1);
                if(expected!=b.version())throw new IllegalStateException("Nhánh đã đổi phiên bản ở phiên đăng nhập khác. Sao chép nháp và tải lại trước khi lưu.");
                if(action.equals("BRANCH_SAVE")) {
                    String code=source(body);
                    if(!b.code().equals(code)) {
                        repo.saveBranch(new Branch(b.room(),b.round(),b.userId(),b.username(),b.name(),code,b.version()+1,b.baseVersion(),System.currentTimeMillis()),expected);
                        core.log("Lưu nhánh phòng " + room.getRoomCode() + " [ID=" + room.getRoomId() + "] học sinh="
                                + who.displayName() + " (" + who.username() + ") vòng=" + s.round() + " v" + (b.version()+1));
                    }
                } else {
                    List<Check> previous=repo.checks(s,who.userId(),1);
                    if(!previous.isEmpty()&&(Set.of("QUEUED","RUNNING").contains(previous.getFirst().status())||System.currentTimeMillis()-previous.getFirst().createdAt()<3000))
                        throw new IllegalStateException("Đang chấm hoặc vừa gửi yêu cầu. Vui lòng đợi ít nhất 3 giây.");
                    Exercise e=requireExercise(s.exerciseId());
                    Check check=new Check(UUID.randomUUID().toString(),s.room(),s.round(),who.userId(),b.version(),b.code(),e.id(),System.currentTimeMillis(),"QUEUED","{}");
                    repo.createCheck(check);pending.put(check.id(),check);
                    try { grader.execute(()->grade(check,e)); }
                    catch(RejectedExecutionException full) {pending.remove(check.id());repo.result(check.id(),"JUDGE_ERROR",mapOf("verdict","JUDGE_ERROR","feedback","Hàng đợi chấm đầy; thử lại sau."));throw new IllegalStateException("Hàng đợi chấm đầy; thử lại sau.");}
                    core.log("Kiểm tra nhánh phòng " + room.getRoomCode() + " [ID=" + room.getRoomId() + "] học sinh="
                            + who.displayName() + " (" + who.username() + ") vòng=" + s.round() + " nhánh v" + b.version());
                }
                return view(room,client);
            }
            if(!room.isOwner(who))throw new SecurityException("Chỉ giáo viên chủ phòng được điều khiển phiên học và Merge.");
            long now=System.currentTimeMillis();
            Session next;
            CodeRevision revision=null;
            List<Branch> branches=List.of();
            String detail=action;
            // Chặn yêu cầu từ nút bấm cũ để tránh áp dụng nhầm sang bước hoặc vòng học mới.
            if(!text(body,"expectedPhase","").equals(s.phase())||number(body,"round",-1)!=s.round())
                throw new IllegalStateException("Phiên học đã đổi trạng thái. Đồng bộ rồi thử lại.");
            switch(action) {
                case "START" -> {
                    phase(s,"WAITING");idle(room);
                    next=new Session(s.room(),"LECTURE",s.round(),s.exerciseId(),room.snapshot().version(),now);
                }
                case "NEW_SESSION" -> {
                    phase(s,"ENDED");idle(room);
                    next=new Session(s.room(),"WAITING",s.round(),"",room.snapshot().version(),now);
                }
                case "MASTER_SAVE", "SELECT_EXERCISE" -> {
                    phase(s,"LECTURE");idle(room);
                    long expected=masterVersion(room,body);
                    String code;
                    String exerciseId=s.exerciseId();
                    if(action.equals("SELECT_EXERCISE")) {
                        Exercise e=requireExercise(text(body,"exerciseId",""));exerciseId=e.id();code=e.starterCode();detail=e.title();
                    } else code=source(body);
                    revision=revision(room,who,code,expected,action.equals("SELECT_EXERCISE")?"Chọn bài: "+detail:"Giáo viên lưu Master");
                    next=new Session(s.room(),"LECTURE",s.round(),exerciseId,revision.getVersion(),now);
                }
                case "FORK" -> {
                    phase(s,"LECTURE");idle(room);masterVersion(room,body);requireExercise(s.exerciseId());
                    next=new Session(s.room(),"BRANCHING",s.round()+1,s.exerciseId(),room.snapshot().version(),now);
                    Map<String,Identity> students=new LinkedHashMap<>();
                    for(Room.MemberView m:room.memberDetails()) if(m.online()&&m.role().equals("STUDENT"))
                        students.put(m.userId(),new Identity(m.userId(),m.username(),m.displayName(),Identity.Role.STUDENT));
                    if(students.isEmpty())throw new IllegalStateException("Cần ít nhất một học sinh trong phòng để tách nhánh.");
                    branches=students.values().stream().map(student->branch(room,next,student)).toList();
                    detail="Tách "+branches.size()+" nhánh từ Master v"+next.baseVersion();
                }
                case "FREEZE" -> {phase(s,"BRANCHING");next=new Session(s.room(),"MERGING",s.round(),s.exerciseId(),s.baseVersion(),now);}
                case "RESUME" -> {phase(s,"MERGING");next=new Session(s.room(),"BRANCHING",s.round(),s.exerciseId(),s.baseVersion(),now);}
                case "MERGE" -> {
                    phase(s,"MERGING");idle(room);long expected=masterVersion(room,body);
                    if(expected!=s.baseVersion())throw new IllegalStateException("Master đã thay đổi so với lúc tách nhánh; không thể ghi đè.");
                    Branch selected=repo.branches(s).stream().filter(b->b.userId().equals(text(body,"userId",""))).findFirst()
                            .orElseThrow(()->new IllegalArgumentException("Nhánh không thuộc vòng sửa lỗi hiện tại."));
                    if(selected.version()!=number(body,"branchVersion",-1))throw new IllegalStateException("Nhánh đã thay đổi; xem lại trước khi Merge.");
                    detail="Chọn nhánh của "+selected.username()+" v"+selected.version()+" (vòng "+s.round()+")";
                    revision=revision(room,who,selected.code(),expected,detail);
                    next=new Session(s.room(),"LECTURE",s.round(),s.exerciseId(),revision.getVersion(),now);
                }
                case "END" -> {
                    if(s.phase().equals("WAITING")||s.phase().equals("ENDED"))throw new IllegalStateException("Phiên chưa bắt đầu hoặc đã kết thúc.");
                    idle(room);next=new Session(s.room(),"ENDED",s.round(),s.exerciseId(),s.baseVersion(),now);
                }
                default -> throw new IllegalArgumentException("Thao tác phòng học không được hỗ trợ.");
            }
            repo.change(next,who,action,detail,branches,revision);
            if(revision!=null)room.publishClassroomRevision(revision,next.phase());
            else room.classroomNotice(next.phase());
            core.log("Lớp học phòng " + room.getRoomCode() + " [ID=" + room.getRoomId() + "] "
                    + who.displayName() + " (" + who.username() + ") " + action + " · " + detail
                    + " · " + next.phase() + " vòng=" + next.round() + " Master v" + room.snapshot().version());
            return view(room,client);
        }
    }
    Map<String,Object> checkDetail(Room room, ClientHandler client, String id) throws SQLException {
        synchronized(room) {
            member(room,client);
            Check x=repo.check(room.getRoomId(),id);
            if(x==null||(!room.isOwner(client.identity())&&!x.userId().equals(client.identity().userId())))
                throw new SecurityException("Không tìm thấy lượt chấm được phép xem.");
            return checkMap(x,true);
        }
    }
    Map<String,Object> branchDetail(Room room, ClientHandler client, String userId, int round) throws SQLException {
        synchronized(room) {
            member(room,client);
            if(!room.isOwner(client.identity())&&!client.identity().userId().equals(userId))throw new SecurityException("Bạn chỉ được xem nhánh của chính mình.");
            Session s=repo.session(room.getRoomId());
            s=historical(s,round);
            Branch b=repo.branches(s).stream().filter(x->x.userId().equals(userId)).findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy nhánh."));
            return mapOf("userId",b.userId(),"username",b.username(),"displayName",b.name(),"code",b.code(),"version",b.version(),"round",b.round(),"checks",repo.checks(s,b.userId(),20).stream().map(x->checkMap(x,false)).toList());
        }
    }
    Map<String,Object> roundReport(Room room, ClientHandler client, int round) throws SQLException {
        synchronized(room) {
            member(room,client);Session s=historical(repo.session(room.getRoomId()),round);
            List<Map<String,Object>> rows=new ArrayList<>();
            for(Branch b:repo.branches(s)) if(room.isOwner(client.identity())||b.userId().equals(client.identity().userId())) {
                List<Check> checks=repo.checks(s,b.userId(),1);
                rows.add(mapOf("userId",b.userId(),"displayName",b.name(),"username",b.username(),"version",b.version(),"latestCheck",checks.isEmpty()?Map.of():checkMap(checks.getFirst(),false),"resultStale",!checks.isEmpty()&&checks.getFirst().branchVersion()!=b.version()));
            }
            return mapOf("round",s.round(),"branches",rows);
        }
    }
    private static Session historical(Session current,int round) {
        if(round==0)return current;
        if(round<1||round>current.round())throw new IllegalArgumentException("Vòng sửa lỗi không hợp lệ.");
        return new Session(current.room(),current.phase(),round,current.exerciseId(),current.baseVersion(),current.updatedAt());
    }
    private Exercise requireExercise(String id) throws SQLException {
        Exercise e=exercises.get(id);
        if(e==null||e.mode()!=ExerciseMode.DEBUG_COLLAB)throw new IllegalArgumentException("Chọn một bài sửa lỗi DEBUG_COLLAB trong ngân hàng.");
        return e;
    }
    private static void member(Room room, ClientHandler client) {
        if(client==null||!client.isConnected()||client.identity()==null||!room.isMember(client.getMemberId()))throw new SecurityException("Bạn chưa tham gia phòng học này.");
    }
    private static void phase(Session s,String expected) {if(!s.phase().equals(expected))throw new IllegalStateException("Thao tác yêu cầu "+expected+", hiện tại là "+s.phase()+".");}
    private static void sameRound(Session s,Map<String,Object> b) {if(number(b,"round",-1)!=s.round())throw new IllegalStateException("Nhánh thuộc vòng sửa lỗi cũ. Hãy tải lại phòng.");}
    private static void idle(Room room) {if(room.hasActiveDraft()||room.hasActiveEditTurn())throw new IllegalStateException("Hãy hoàn tất hoặc hủy nháp và trả lượt sửa cũ trước.");}
    private static long masterVersion(Room room,Map<String,Object> b) {
        long expected=number(b,"expectedVersion",-1);
        if(expected!=room.snapshot().version())throw new IllegalStateException("Master đã đổi phiên bản. Tải lại trước khi tiếp tục.");
        return expected;
    }
    private static String source(Map<String,Object> b) {
        String code=text(b,"code","");
        if(code.isBlank()||code.getBytes(StandardCharsets.UTF_8).length>32000)throw new IllegalArgumentException("Code phải có nội dung và tối đa 32000 byte UTF-8.");
        return code;
    }
    private static Branch branch(Room r,Session s,Identity who) {return new Branch(s.room(),s.round(),who.userId(),who.username(),who.displayName(),r.snapshot().code(),0,s.baseVersion(),System.currentTimeMillis());}
    private static CodeRevision revision(Room r,Identity who,String code,long expected,String reason) {
        source(mapOf("code",code));
        return new CodeRevision(expected+1,who.userId(),who.displayName(),code,Instant.now(),expected,reason+"\n"+CodeDiff.display(CodeDiff.between(r.snapshot().code(),code)));
    }
    private static Map<String,Object> checkMap(Check c,boolean includeCode) {
        Map<String,Object> out=mapOf("id",c.id(),"branchVersion",c.branchVersion(),"createdAt",c.createdAt(),"status",c.status(),"result",AssignmentJson.object(c.result()));
        if(includeCode)out.put("code",c.code());
        return out;
    }
    private void grade(Check check,Exercise exercise) {
        try {
            if(closed.get())return;
            repo.result(check.id(),"RUNNING",Map.of());
            GradingResult r=sandbox.grade(check.id(),check.code(),exercise.tests(),100);
            if(!closed.get()) {
                repo.result(check.id(),"DONE",mapOf("verdict",r.verdict(),"score",r.score(),"maxScore",r.maxScore(),"passed",r.passed(),"total",r.total(),"feedback",r.feedback(),"durationMs",r.durationMillis(),"publicTests",r.publicTests()));
                Room room=core.rooms.get(check.room());
                core.log("Kết quả nhánh phòng " + (room==null?check.room():room.getRoomCode()) + " [ID=" + check.room()
                        + "] userId=" + check.userId() + " vòng=" + check.round() + " nhánh v" + check.branchVersion()
                        + " · " + r.verdict() + " " + r.passed() + "/" + r.total());
            }
        } catch(Exception e) {
            core.log("Lỗi chấm nhánh "+check.id()+": "+e.getClass().getSimpleName());
            if(!closed.get())try{repo.result(check.id(),"JUDGE_ERROR",mapOf("verdict","JUDGE_ERROR","feedback","Dịch vụ chấm gặp lỗi. Hãy thử lại."));}catch(SQLException failure){core.log("Không lưu được lỗi chấm: "+failure.getMessage());}
        } finally {pending.remove(check.id());}
    }
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        grader.shutdownNow();
        for(Check c:pending.values())try{repo.result(c.id(),"JUDGE_ERROR",mapOf("verdict","JUDGE_ERROR","feedback","Dịch vụ đã dừng. Hãy chạy kiểm tra lại."));}catch(SQLException e){core.log("Không lưu được trạng thái dừng chấm: "+e.getMessage());}
        try{grader.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
