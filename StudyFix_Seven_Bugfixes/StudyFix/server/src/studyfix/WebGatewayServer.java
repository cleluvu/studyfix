package studyfix;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import studyfix.assignment.protocol.AssignmentJson;
import studyfix.assignment.transport.AssignmentTcpServer;
import studyfix.assignment.grading.GradingSandbox;
import studyfix.assignment.grading.DockerJavaSandbox;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nhận yêu cầu HTTP từ trình duyệt trong cùng chương trình server. Việc đăng nhập đi qua
 * server TCP hiện có; quyền truy cập vẫn do Store, Identity và Room kiểm tra.
 * Trình duyệt không tự cung cấp mã phiên nội bộ cho module bài tập.
 */
public final class WebGatewayServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 128 * 1024;
    private final long sessionTtlMillis = Math.max(1000,Long.getLong("studyfix.web.sessionTimeoutMillis",TimeUnit.MINUTES.toMillis(2)));
    private static final Set<String> ASSIGNMENT_TYPES = Set.of(
            "EXAM_CONFIG","EXAM_STATUS","EXAM_EVENT","EXAM_MONITOR","EXAM_HISTORY","EXAM_SNAPSHOT",
            "EXAMBANK_LIST","EXAMBANK_SOURCES","EXAMBANK_GET","EXAMBANK_PREVIEW","EXAMBANK_SAVE","EXAMBANK_APPROVE","EXAMBANK_ARCHIVE","EXAMBANK_CLONE","EXAMBANK_EXPORT","EXAMBANK_IMPORT_COMMIT",
            "OJ_PUBLISH_BATCH","OJ_STATUS","OJ_PUBLISH","BUILDER_STATUS","BUILDER_REVIEWERS","BUILDER_REVIEW_LIST","BUILDER_REVIEW_GET","BUILDER_REVIEW_DECIDE","BUILDER_EXPORT","BUILDER_REVIEW","BUILDER_PUBLISH","BUILDER_ARCHIVE","BUILDER_TEST_IMPORT","BUILDER_TEST_GENERATE","BUILDER_VALIDATE","BUILDER_EXPECTED","BUILDER_COMPILE","BUILDER_TEMPLATE_IMPORT","BUILDER_ADVANCE","BUILDER_ASSETS","BUILDER_CAPABILITIES","BUILDER_LIST","BUILDER_GET","BUILDER_CREATE","BUILDER_SAVE","BUILDER_HISTORY","BUILDER_RESTORE","BUILDER_DELETE_DRAFT","BUILDER_SUGGEST_CODE",
            "BANK_LIST", "BANK_GET", "BANK_UPDATE", "BANK_DELETE", "CATALOG_CREATE", "PING", "PRACTICE_WEEK_LIST","PRACTICE_WEEK_SAVE","PRACTICE_WEEK_DELETE","PRACTICE_WEEK_ADD","PRACTICE_WEEK_REMOVE","PRACTICE_LIST", "PRACTICE_GET", "PRACTICE_SUBMIT", "PRACTICE_RUN", "PRACTICE_SUBMISSIONS", "PRACTICE_RESULT", "CATALOG_LIST", "CATALOG_GET", "ROOM_STUDENTS_LIST", "CLASS_PROGRESS",
            "ASSIGN_CREATE", "ASSIGN_LIST", "ASSIGN_GET", "DEBUG_STARTER_PREPARE", "DEBUG_STARTER_LOAD",
            "SHARED_CODE_STATE", "SHARED_CHECK_CREATE", "SHARED_CHECK_LATEST", "SHARED_CHECK_STATUS",
            "SHARED_CHECK_RESULT", "SUBMIT_CREATE", "SUBMISSION_LIST", "SUBMISSION_RESULT",
            "CONTEST_RECIPIENTS","CONTEST_DISTRIBUTE","CONTEST_CREATE", "CONTEST_LIST", "CONTEST_PROBLEMS_GET", "CONTEST_SUBMIT",
            "CONTEST_SUBMISSION_RESULT", "CONTEST_SUBMISSION_LIST", "LEADERBOARD_GET",
            "TEACHER_TRIAL_CREATE", "TEACHER_TRIAL_LIST", "TEACHER_TRIAL_RESULT");
    private static final Pattern LOGIN_OK = Pattern.compile("\\[LOGIN_OK] session=([^ ]+) userId=([^ ]+) role=(TEACHER|STUDENT) \\| (.*)");

    private final Server core;
    private final AssignmentTcpServer assignments;
    private final ClassroomService classroom;
    private final PresenceRepository presence;
    private final AdminManagement admin;
    private final PresenceWebSocketServer presenceSocket;
    private final MeetingCalls calls = new MeetingCalls();
    private final HttpServer http;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSession> sessionsByCoreId = new ConcurrentHashMap<>();
    private final Map<String, WebSession> sessionsByPeerId = new ConcurrentHashMap<>();
    /** Mỗi phòng chỉ có một người chia sẻ màn hình trên trình duyệt; hai bên đều có thể gửi tín hiệu kết nối. */
    private final Map<String, String> activeScreenSharersByRoom = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<>();
    private final Path staticRoot;
    private volatile boolean closed;
    private volatile boolean started;

    public WebGatewayServer(Server core, AssignmentTcpServer assignments) throws IOException {
        this(core, assignments, new DockerJavaSandbox(System.getProperty("studyfix.assignment.docker.image", "eclipse-temurin:21-jdk"), 2, 45));
    }
    WebGatewayServer(Server core, AssignmentTcpServer assignments, GradingSandbox sandbox) throws IOException {
        this.core = Objects.requireNonNull(core);
        this.assignments = Objects.requireNonNull(assignments);
        this.presence = new PresenceRepository(core.store());
        this.admin = new AdminManagement(core,this::revokeUser,this::detachConnection);
        try { presence.recover(); } catch(java.sql.SQLException e) { throw new IOException("Không khôi phục được trạng thái kết nối.",e); }
        String bindHost = System.getProperty("studyfix.web.host", envOrDefault("STUDYFIX_WEB_HOST", "0.0.0.0"));
        int port = Integer.getInteger("studyfix.web.port", envPort("STUDYFIX_WEB_PORT", 8080));
        this.http = createHttpServer(bindHost, port);
        this.executor = new ThreadPoolExecutor(4, 32, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256), r -> { Thread t = new Thread(r, "studyfix-web-http"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());
        this.scheduler = Executors.newScheduledThreadPool(2,r -> { Thread t = new Thread(r, "studyfix-web-maintenance"); t.setDaemon(true); return t; });
        this.http.setExecutor(executor);
        this.http.createContext("/", this::handle);
        Path dist = Path.of(System.getProperty("studyfix.web.dist", "../web-client/dist")).toAbsolutePath().normalize();
        Path fallback = Path.of(System.getProperty("studyfix.web.root", "../web-client/static")).toAbsolutePath().normalize();
        this.staticRoot = Files.isRegularFile(dist.resolve("index.html")) ? dist : fallback;
        int wsPort=Integer.getInteger("studyfix.web.ws.port",envPort("STUDYFIX_WS_PORT",port==0?0:port+1));
        this.presenceSocket="false".equalsIgnoreCase(System.getenv("STUDYFIX_WS_ENABLED"))?null:new PresenceWebSocketServer(new InetSocketAddress(bindHost,wsPort),http.getAddress().getPort(),tlsContext(),new PresenceWebSocketServer.Access(){
            public Object authenticate(String token,InetSocketAddress peer)throws Exception{
                WebSession session=sessions.get(token);if(session==null)throw new SecurityException();webSocketHeartbeat(session,peer);return session;
            }
            public void heartbeat(Object session,InetSocketAddress peer)throws Exception{webSocketHeartbeat((WebSession)session,peer);}
            public Map<String,Object> snapshot(Object session,boolean adminView)throws Exception{
                WebSession s=(WebSession)session;validateLiveSession(s);
                Map<String,Object> exam=examSnapshot(s);
                Map<String,Object> org=assignments.handleWebRequest(map("type","EO_PULSE","sessionId",s.coreSessionId));
                exam.put("organization", "OK".equals(org.get("status"))?org.get("data"):Map.of());
                if(s.identity.role()!=Identity.Role.TEACHER)return core.store().management()==null?map("type","ALIVE","examSnapshot",exam):map("type","ADMISSION_UPDATE","admissions",new ClassAdmission(core.store().management()).studentStatus(s.identity.userId(),s.coreSessionId),"examSnapshot",exam);
                if(adminView){Map<String,Object> data=admin.view(s.identity,"");return map("type","ADMIN_UPDATE","data",data,"examSnapshot",exam);}
                String room=currentRoomId(s);Room managed=core.rooms.get(room);
                return map("type","PRESENCE","roomId",room,"connections",managed!=null&&managed.isOwner(s.identity)?presence.students(room):List.of(),"joinRequests",core.store().management()==null?List.of():new ClassAdmission(core.store().management()).pending(s.identity.userId()),"examSnapshot",exam);
            }
        });
        classroom = new ClassroomService(core, assignments::classroomExercise, sandbox);
        scheduler.scheduleWithFixedDelay(this::cleanup, 1, 1, TimeUnit.SECONDS);
    }

    private Map<String,Object> examSnapshot(WebSession session){
        String room=currentRoomId(session);if(room.isBlank())return map("modes",List.of(),"monitors",Map.of());
        Map<String,Object> result=assignments.handleWebRequest(map("type","EXAM_SNAPSHOT","sessionId",session.coreSessionId,"roomId",room));
        return "OK".equals(result.get("status"))?AssignmentJson.map(result.get("data")):map("modes",List.of(),"monitors",Map.of());
    }
    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private static int envPort(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0 || parsed > 65535) throw new NumberFormatException("outside TCP port range");
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Biến môi trường " + name + " phải là cổng từ 0 đến 65535.", e);
        }
    }

    private static HttpServer createHttpServer(String bindHost, int port) throws IOException {
        SSLContext ssl=tlsContext();
        if(ssl==null)return HttpServer.create(new InetSocketAddress(bindHost,port),64);
        HttpsServer secure=HttpsServer.create(new InetSocketAddress(bindHost,port),64);secure.setHttpsConfigurator(new HttpsConfigurator(ssl));return secure;
    }
    private static SSLContext tlsContext()throws IOException{
        String keyStorePath = System.getProperty("studyfix.web.keystore", envOrDefault("STUDYFIX_WEB_KEYSTORE", "")).trim();
        if (keyStorePath.isBlank()) return null;
        String password = System.getProperty("studyfix.web.keystore.password", envOrDefault("STUDYFIX_WEB_KEYSTORE_PASSWORD", ""));
        if (password.isEmpty()) throw new IOException("Đã cấu hình studyfix.web.keystore nhưng thiếu studyfix.web.keystore.password.");
        String type = System.getProperty("studyfix.web.keystore.type", "PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(keyStorePath))) {
            KeyStore store = KeyStore.getInstance(type);
            store.load(in, password.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(store, password.toCharArray());
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(kmf.getKeyManagers(), null, null);
            return ssl;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IOException("Không khởi tạo được HTTPS gateway. Kiểm tra keystore, loại keystore và mật khẩu.", e);
        }
    }

    public synchronized void start() {
        if (closed) throw new IllegalStateException("Web gateway đã đóng.");
        if (started) return;
        if(presenceSocket!=null)try{presenceSocket.startChecked();}catch(IOException e){throw new IllegalStateException(e.getMessage(),e);}
        http.start();
        started = true;
        System.out.println("StudyFix Web Gateway chạy tại " + scheme() + "://" + http.getAddress().getHostString() + ":" + http.getAddress().getPort());
        System.out.println("FE source/fallback: " + staticRoot);
    }
    public int port() { return http.getAddress().getPort(); }
    int webSocketPort(){return presenceSocket==null?-1:presenceSocket.getPort();}
    private Map<String,Object> webSocketConfig(){return map("enabled",presenceSocket!=null,"port",webSocketPort(),"path","/presence","tls",scheme().equals("https"));}
    public String scheme() { return http instanceof com.sun.net.httpserver.HttpsServer ? "https" : "http"; }
    public boolean isRunning() { return started && !closed; }

    private void handle(HttpExchange ex) throws IOException {
        try {
            securityHeaders(ex.getResponseHeaders());
            if(presenceSocket!=null)try{
                URI host=URI.create(scheme()+"://"+ex.getRequestHeaders().getFirst("Host"));
                URI ws=new URI(scheme().equals("https")?"wss":"ws",null,host.getHost(),webSocketPort(),null,null,null);
                String csp=ex.getResponseHeaders().getFirst("Content-Security-Policy");ex.getResponseHeaders().set("Content-Security-Policy",csp.replace("connect-src 'self'","connect-src 'self' "+ws));
            }catch(URISyntaxException|IllegalArgumentException ignored){}
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { corsPreflight(ex); return; }
                handleApi(ex, path);
            } else {
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
                    json(ex, 405, Map.of("ok", false, "message", "Phương thức không được hỗ trợ.")); return;
                }
                serveStatic(ex, path);
            }
        } catch (ApiException e) {
            json(ex, e.status, Map.of("ok", false, "error", e.code, "message", e.getMessage()));
        } catch (SecurityException e) {
            json(ex, 403, Map.of("ok", false, "error", "FORBIDDEN", "message", safeMessage(e)));
        } catch (IllegalArgumentException e) {
            json(ex, 400, Map.of("ok", false, "error", "INVALID_REQUEST", "message", safeMessage(e)));
        } catch (IllegalStateException e) {
            json(ex, 409, Map.of("ok", false, "error", "STATE_CONFLICT", "message", safeMessage(e)));
        } catch (java.sql.SQLException e) {
            boolean duplicate=(e.getErrorCode()&0xff)==19 || e.getSQLState()!=null&&e.getSQLState().startsWith("23");
            if(core.store().management()==null)json(ex,500,map("ok",false,"error","INTERNAL_ERROR","message","Không thể hoàn tất yêu cầu. Kiểm tra nhật ký server."));
            else json(ex,duplicate?409:503,map("ok",false,"error",duplicate?"DATA_CONFLICT":"DATABASE_UNAVAILABLE","message",duplicate?"Dữ liệu đã tồn tại hoặc không thỏa ràng buộc. Hãy kiểm tra mã/tài khoản.":"Không kết nối hoặc ghi được database. Kiểm tra đường dẫn và quyền truy cập file SQLite."));
        } catch (Exception e) {
            System.err.println("Web gateway error: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            json(ex, 500, Map.of("ok", false, "error", "INTERNAL_ERROR", "message", "Không thể hoàn tất yêu cầu. Kiểm tra nhật ký server."));
        } finally {
            ex.close();
        }
    }

    private void handleApi(HttpExchange ex, String path) throws Exception {
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        if (path.equals("/api/health") && method.equals("GET")) {
            json(ex, 200, map("ok", true, "service", "StudyFix Web Gateway", "scheme", scheme(), "webPort", port(),
                    "controlPort", core.port(), "assignmentPort", assignments.port(),"managementDatabase","sqlite","webSocket",webSocketConfig(),"time", System.currentTimeMillis())); return;
        }
        if (path.equals("/api/auth/login") && method.equals("POST")) { login(ex); return; }
        if (path.equals("/api/auth/logout") && method.equals("POST")) { WebSession s = requireSession(ex); s.disconnectReason="LOGOUT";closeSession(s); json(ex, 200, Map.of("ok", true)); return; }

        WebSession s = requireSession(ex);
        touchSession(s,ex);
        if(path.equals("/api/admin/students/import-preview")&&method.equals("POST")){
            AdminManagement.requireAdmin(s.identity);byte[] bytes=ex.getRequestBody().readNBytes(1048577);if(bytes.length>1048576)throw new ApiException(413,"BODY_TOO_LARGE","File Excel tối đa 1 MiB.");
            json(ex,200,map("ok",true,"rows",StudentSpreadsheet.read(bytes)));return;
        }
        if(path.equals("/api/student/submission")&&method.equals("GET")){
            json(ex,200,map("ok",true,"submission",assignments.studentSubmission(s.identity,queryText(ex.getRequestURI(),"id"))));return;
        }
        if(path.equals("/api/student/portal")&&method.equals("GET")){
            Map<String,Object> request=new LinkedHashMap<>();request.put("classId",queryText(ex.getRequestURI(),"classId"));
            for(String key:List.of("days","page"))request.put(key,queryLong(ex.getRequestURI(),key,key.equals("days")?0:1));
            json(ex,200,map("ok",true,"data",new StudentPortal(core,assignments).view(s.identity,request)));return;
        }
        if(path.equals("/api/teacher/dashboard")&&(method.equals("GET")||method.equals("POST"))){
            AdminManagement.requireAdmin(s.identity);
            TeacherDashboard dashboard=new TeacherDashboard(core.store().management(),assignments);
            Map<String,Object> data;
            if(method.equals("POST"))data=dashboard.update(s.identity,readBody(ex));
            else {
                Map<String,Object> request=new LinkedHashMap<>();
                for(String key:List.of("classId","weekId","verdict"))request.put(key,queryText(ex.getRequestURI(),key));
                for(String key:List.of("days","page"))if(!queryText(ex.getRequestURI(),key).isBlank())request.put(key,queryLong(ex.getRequestURI(),key,key.equals("days")?30:1));
                data=dashboard.view(s.identity,admin.view(s.identity,""),request);
            }
            json(ex,200,map("ok",true,"data",data));return;
        }
        if(path.equals("/api/admin")&&(method.equals("GET")||method.equals("POST"))){
            AdminManagement.requireAdmin(s.identity);cleanup();
            Map<String,Object> data=method.equals("GET")?admin.view(s.identity,queryText(ex.getRequestURI(),"classId")):admin.action(s.identity,readBody(ex));
            json(ex,200,map("ok",true,"data",data));return;
        }
        if(path.equals("/api/auth/heartbeat")&&method.equals("POST")) {
            json(ex,200,map("ok",true,"serverTime",System.currentTimeMillis(),"timeoutMillis",sessionTtlMillis));return;
        }
        if(path.equals("/api/auth/departing")&&method.equals("POST")) {
            long generation=s.departureGeneration.incrementAndGet();
            s.departure=scheduler.schedule(()->{if(s.departureGeneration.get()==generation)closeSession(s);},8,TimeUnit.SECONDS);
            json(ex,200,map("ok",true));return;
        }
        if (path.equals("/api/me") && method.equals("GET")) {
            String roomId = currentRoomId(s);
            Room current=roomId.isBlank()?null:core.rooms.get(roomId);
            json(ex, 200, map("ok", true, "identity", identityMap(s.identity), "webSocket",webSocketConfig(),"peerId", s.peerId, "roomId", roomId,"roomCode",current==null?"":current.getRoomCode())); return;
        }
        if (path.equals("/api/rooms") && method.equals("GET")) { rooms(ex, s); return; }
        if (path.equals("/api/rooms/current") && method.equals("GET")) { currentRoom(ex, s); return; }
        if (path.equals("/api/rooms/current/presence") && method.equals("GET")) {
            Room room=requireCurrentRoom(s);
            if(!room.isOwner(s.identity))throw new ApiException(403,"FORBIDDEN","Chỉ giảng viên quản lý lớp này được xem kết nối.");
            json(ex,200,map("ok",true,"connections",roomPresence(room),"timeoutMillis",sessionTtlMillis));return;
        }
        if (path.equals("/api/rooms/current/history") && method.equals("GET")) { roomHistory(ex, s); return; }
        if (path.equals("/api/rooms/current/chat") && method.equals("GET")) {
            Room room=requireCurrentRoom(s);
            json(ex,200,new ClassroomRepository(core.store()).chatStatus(room.getRoomId(),s.identity.userId(),queryLong(ex.getRequestURI(),"after",0)));return;
        }
        if(path.equals("/api/oj/import/preview")&&method.equals("POST")){
            AdminManagement.requireAdmin(s.identity);byte[] bytes=ex.getRequestBody().readNBytes(16*1024*1024+1);if(bytes.length>16*1024*1024)throw new ApiException(413,"BODY_TOO_LARGE","OJ ZIP exceeds 16 MiB");
            Map<String,Object> preview;try{preview=assignments.ojImports().preview(s.identity,bytes);}catch(studyfix.assignment.persistence.ProblemBuilderRepository.Failure|IOException|IllegalArgumentException invalid){throw new ApiException(400,"INVALID_OJ_PACKAGE","Gói OJ không hợp lệ: "+invalid.getMessage());}json(ex,200,map("ok",true,"data",preview));return;
        }
        if(path.equals("/api/oj/import/commit")&&method.equals("POST")){
            AdminManagement.requireAdmin(s.identity);Map<String,Object> request=readBody(ex);json(ex,200,map("ok",true,"data",assignments.ojImports().commit(s.identity,text(request,"previewId",""),text(request,"sha256",""))));return;
        }
        if(path.equals("/api/exam-bank/import")&&method.equals("POST")){
            if(s.identity.role()!=Identity.Role.TEACHER)throw new ApiException(403,"FORBIDDEN","Teacher only");
            int limit=studyfix.assignment.service.ExamBankService.MAX_IMPORT_BYTES;
            byte[] bytes=ex.getRequestBody().readNBytes(limit+1);
            if(bytes.length>limit)throw new ApiException(413,"BODY_TOO_LARGE","Exam package exceeds 2 MiB.");
            try{json(ex,200,map("ok",true,"data",assignments.examBank().previewImport(s.identity,bytes,queryText(ex.getRequestURI(),"format").equals("ZIP"),queryText(ex.getRequestURI(),"update").equals("true"))));return;}
            catch(SecurityException e){throw new ApiException(403,"FORBIDDEN",e.getMessage());}
            catch(Exception e){throw new ApiException(400,"INVALID_EXAM_PACKAGE",e.getMessage());}
        }
        if(path.startsWith("/api/problem-builder/assets/")&&method.equals("GET")){
            try{var asset=assignments.problemBuilder().asset(s.identity,path.substring("/api/problem-builder/assets/".length()));ex.getResponseHeaders().set("Content-Type",asset.media());ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");ex.getResponseHeaders().set("Cache-Control","private, no-store");ex.getResponseHeaders().set("Content-Security-Policy","sandbox");if(!asset.media().startsWith("image/"))ex.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+asset.id()+"\"");ex.sendResponseHeaders(200,asset.bytes().length);try(var out=ex.getResponseBody()){out.write(asset.bytes());}return;}catch(studyfix.assignment.persistence.ProblemBuilderRepository.Failure e){throw new ApiException(e.code.equals("FORBIDDEN")?403:404,e.code,e.getMessage());}
        }
        if(path.startsWith("/api/problem-builder/uploads/")&&method.equals("POST")){
            if(s.identity.role()!=Identity.Role.TEACHER)throw new ApiException(403,"FORBIDDEN","Teacher only");byte[] bytes=ex.getRequestBody().readNBytes(studyfix.assignment.persistence.ProblemBuilderAssets.MAX_BYTES+1);if(bytes.length>studyfix.assignment.persistence.ProblemBuilderAssets.MAX_BYTES)throw new ApiException(413,"BODY_TOO_LARGE","File exceeds 2 MiB");
            try{var asset=assignments.problemBuilder().upload(s.identity,path.substring("/api/problem-builder/uploads/".length()),queryText(ex.getRequestURI(),"name"),bytes);json(ex,200,map("ok",true,"asset",asset));return;}catch(studyfix.assignment.persistence.ProblemBuilderRepository.Failure e){throw new ApiException(e.code.equals("FORBIDDEN")?403:e.code.equals("NOT_FOUND")?404:400,e.code,e.getMessage());}
        }
        if (path.equals("/api/rooms/current/command") && method.equals("POST")) { roomCommand(ex, s); return; }
        if (path.equals("/api/classroom") && (method.equals("GET") || method.equals("POST"))) {
            Room room = requireCurrentRoom(s);
            ClientHandler client = core.clients.get(s.coreSessionId);
            Map<String,Object> command=method.equals("POST")?readBody(ex):Map.of();
            if("SELECT_EXERCISE".equals(command.get("action"))){if(!room.isOwner(s.identity))throw new SecurityException("Chỉ giáo viên chủ phòng được chọn đề.");assignments.requirePublishedQuestion(String.valueOf(command.get("exerciseId")));}
            Map<String,Object> view = method.equals("GET") ? classroom.view(room, client) : classroom.action(room, client, command);
            json(ex, 200, map("ok", true, "classroom", view)); return;
        }
        if (path.startsWith("/api/classroom/checks/") && method.equals("GET")) {
            json(ex, 200, map("ok", true, "check", classroom.checkDetail(requireCurrentRoom(s), core.clients.get(s.coreSessionId), path.substring("/api/classroom/checks/".length())))); return;
        }
        if (path.startsWith("/api/classroom/branches/") && method.equals("GET")) {
            json(ex, 200, map("ok", true, "branch", classroom.branchDetail(requireCurrentRoom(s), core.clients.get(s.coreSessionId), path.substring("/api/classroom/branches/".length()), Math.toIntExact(queryLong(ex.getRequestURI(), "round", 0))))); return;
        }
        if (path.startsWith("/api/classroom/rounds/") && method.equals("GET")) {
            int round = Integer.parseInt(path.substring("/api/classroom/rounds/".length()));
            json(ex, 200, map("ok", true, "report", classroom.roundReport(requireCurrentRoom(s), core.clients.get(s.coreSessionId), round))); return;
        }
        if (path.equals("/api/events") && method.equals("GET")) { events(ex, s); return; }
        if (path.equals("/api/assignments") && method.equals("POST")) { assignmentRequest(ex, s); return; }
        if (path.equals("/api/screen/signal") && method.equals("POST")) { screenSignal(ex, s); return; }
        if (path.equals("/api/screen/signals") && method.equals("GET")) { pollScreenSignals(ex, s); return; }
        if (path.equals("/api/call/signals") && method.equals("GET")) {
            Room room=requireCurrentRoom(s);pruneCalls();
            json(ex,200,calls.poll(s.peerId,room.getRoomId(),queryLong(ex.getRequestURI(),"after",0)));return;
        }
        if (path.equals("/api/call/signal") && method.equals("POST")) { callSignal(ex,s);return; }
        throw new ApiException(404, "NOT_FOUND", "Không tìm thấy API.");
    }

    private void login(HttpExchange ex) throws Exception {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        Map<String,Object> body = readBody(ex);
        String username = text(body, "studentCode", text(body, "username", "")).trim();
        String password = text(body, "password", "");
        if (username.isBlank() || password.isBlank() || username.length() > 64 || password.length() > 256)
            throw new ApiException(400, "INVALID_REQUEST", "Vui lòng nhập tên đăng nhập và mật khẩu hợp lệ.");
        if (!allowLoginAttempt(ip,username)) throw new ApiException(429, "RATE_LIMITED", "Đăng nhập quá nhiều lần. Vui lòng thử lại sau một phút.");
        WebSession session = WebSession.login(core, username, password,ip);
        try { presence.login(session.connectionId,session.coreSessionId,session.identity,ip,session.loginTime); }
        catch(java.sql.SQLException e){session.close();throw new ApiException(503,"PRESENCE_UNAVAILABLE","Không lưu được phiên đăng nhập. Vui lòng thử lại.");}
        sessions.put(session.token, session);
        sessionsByCoreId.put(session.coreSessionId, session);
        sessionsByPeerId.put(session.peerId, session);
        session.heartbeat = scheduler.scheduleAtFixedRate(() -> {
            if (!session.closed) try { session.send("/ping"); } catch (IOException ignored) { }
        }, 8, 8, TimeUnit.SECONDS);
        if (session.closed) session.heartbeat.cancel(false);
        json(ex, 200, map("ok", true, "token", session.token, "peerId", session.peerId,"webSocket",webSocketConfig(),
                "identity", identityMap(session.identity), "roomId", currentRoomId(session)));
    }

    private synchronized boolean allowLoginAttempt(String ip,String login) {
        long now = System.currentTimeMillis();
        Deque<Long> attempts=loginAttempts.computeIfAbsent("account:"+ip+":"+login.toLowerCase(Locale.ROOT),k->new ArrayDeque<>());
        Deque<Long> sharedIp=loginAttempts.computeIfAbsent("ip:"+ip,k->new ArrayDeque<>());
        for(Deque<Long> queue:List.of(attempts,sharedIp))while(!queue.isEmpty()&&now-queue.peekFirst()>60_000)queue.removeFirst();
        if(attempts.size()>=10||sharedIp.size()>=120)return false;
        attempts.addLast(now);sharedIp.addLast(now);
        return true;
    }
    private synchronized void pruneLoginAttempts(){long now=System.currentTimeMillis();loginAttempts.entrySet().removeIf(e->{Deque<Long> q=e.getValue();while(!q.isEmpty()&&now-q.peekFirst()>60_000)q.removeFirst();return q.isEmpty();});}

    private WebSession requireSession(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) throw new ApiException(401, "UNAUTHENTICATED", "Phiên đăng nhập không tồn tại. Vui lòng đăng nhập lại.");
        WebSession s = sessions.get(auth.substring(7).trim());
        if (s == null) throw new ApiException(401,"UNAUTHENTICATED","Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        synchronized(s){
            if(expired(s,System.currentTimeMillis())){s.disconnectReason=s.closed?"CONNECTION_LOST":"HEARTBEAT_TIMEOUT";closeSession(s);throw new ApiException(401,"UNAUTHENTICATED","Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");}
            validateLiveSession(s);
        }
        return s;
    }

    private void rooms(HttpExchange ex, WebSession s) throws IOException,java.sql.SQLException {
        String search=queryText(ex.getRequestURI(),"q").strip();
        if(search.length()>160)throw new IllegalArgumentException("Từ khóa tìm phòng tối đa 160 ký tự.");
        String needle=roomSearchKey(search);
        List<Map<String,Object>> result = new ArrayList<>();
        Set<String> visible=core.store().management()==null?null:core.store().management().visibleClasses(s.identity);
        List<Map<String,Object>> admissions=core.store().management()!=null&&s.identity.role()==Identity.Role.STUDENT?new ClassAdmission(core.store().management()).studentStatus(s.identity.userId(),s.coreSessionId):List.of();
        Map<String,String> admissionStatus=new HashMap<>();for(Map<String,Object> row:admissions)admissionStatus.put(String.valueOf(row.get("classId")),String.valueOf(row.get("status")));
            for (Room room : core.rooms.values()) {
                if(visible!=null&&!visible.contains(room.getRoomId()))continue;
                Room.Snapshot snap = room.snapshot();
                if(room.isExpired())continue;
                if(!roomSearchKey(room.getRoomCode()).contains(needle)
                        &&!roomSearchKey("Phòng "+room.getRoomCode()).contains(needle))continue;
                result.add(map("roomId", snap.roomId(),"roomCode",room.getRoomCode(), "ownerId", snap.ownerId(), "version", snap.version(),
                        "memberCount", snap.members().size(), "online", true, "isMine", snap.ownerId().equals(s.identity.userId()),"admissionStatus",visible==null||s.identity.role()==Identity.Role.TEACHER?"ENROLLED":admissionStatus.getOrDefault(snap.roomId(),"NONE")));
            }
        result.sort(Comparator.comparing(r -> RoomCode.key(String.valueOf(r.get("roomCode")))));
        int pageSize=5;
        int total=result.size(),totalPages=Math.max(1,(total+pageSize-1)/pageSize);
        int page=(int)Math.max(1,Math.min(totalPages,queryLong(ex.getRequestURI(),"page",1)));
        int from=(page-1)*pageSize,to=Math.min(total,from+pageSize);
        json(ex, 200, map("ok", true, "rooms", result.subList(from,to),"admissions",admissions, "role", s.identity.role().name(),"total",total,"page",page,"pageSize",pageSize,"totalPages",totalPages,"customRoomCodes",true,"emptyRoomAutoDelete",false,"manualRoomDelete",true));
    }

    private String currentRoomId(WebSession session) {
        ClientHandler handler = core.clients.get(session.coreSessionId);
        if (handler == null || !handler.isConnected()) throw new ApiException(401, "SESSION_CLOSED", "Phiên TCP đã đóng. Vui lòng đăng nhập lại.");
        String roomId = handler.getCurrentRoomId();
        return roomId == null ? "" : roomId;
    }

    private Room requireCurrentRoom(WebSession s) {
        String id = currentRoomId(s);
        if (id.isBlank()) throw new ApiException(409, "NO_ROOM", "Bạn chưa vào phòng học. Hãy tạo phòng hoặc nhập mã phòng để tham gia.");
        Room room = core.rooms.get(id);
        if (room == null || !room.isMember(s.coreSessionId)) throw new ApiException(409, "ROOM_NOT_ACTIVE", "Phòng hiện tại không còn khả dụng.");
        return room;
    }

    private void currentRoom(HttpExchange ex, WebSession s) throws IOException {
        Room room = requireCurrentRoom(s);
        Room.Snapshot snap = room.snapshot();
        List<Map<String,Object>> members = new ArrayList<>();
        for (Room.MemberView member : room.memberDetails()) {
            WebSession webMember = sessionsByCoreId.get(member.sessionId());
            members.add(map("peerId", webMember == null ? "" : webMember.peerId,
                    "username", member.username(), "displayName", member.displayName(), "role", member.role(),
                    "online", member.online(), "isEditor", member.editor(), "inQueue", member.queued(),
                    "isTeacherOwner", member.userId().equals(snap.ownerId())));
        }
        members.sort(Comparator.comparing(m -> !Boolean.TRUE.equals(m.get("isTeacherOwner"))));
        Room.RecoverableDraft recovery = room.recoverableDraftFor(s.coreSessionId);
        Map<String,Object> recoveryView = recovery == null ? Map.of() : map("sourceCode", recovery.sourceCode(), "baseVersion", recovery.baseVersion(), "savedAt", recovery.savedAt());
        List<Map<String,Object>> historySummary = new ArrayList<>();
        for (CodeRevision revision : snap.history()) historySummary.add(map("version", revision.getVersion(),
                "editorName", revision.getEditorName(), "createdAt", revision.getCreatedAt().toString(), "changes", revision.getChanges()));
        String editorPeerId = "";
        WebSession editor = snap.editor() == null ? null : sessionsByCoreId.get(snap.editor());
        if (editor != null) editorPeerId = editor.peerId;
        List<Map<String,Object>> connections=room.isOwner(s.identity)?roomPresence(room):null;
        Map<String,Object> roomView=map("roomId", snap.roomId(),"roomCode",room.getRoomCode(), "ownerId", snap.ownerId(),
                "code", snap.code(), "version", snap.version(), "editorPeerId", editorPeerId,
                "token", Objects.equals(snap.editor(), s.coreSessionId) ? snap.token() : 0L,
                "recoverableDraft", recoveryView,
                "draftActive", room.hasActiveDraft(), "editTurnActive", room.hasActiveEditTurn(),
                "queue", snap.queue().stream().map(id -> { WebSession ws = sessionsByCoreId.get(id); return ws == null ? "(client Swing)" : ws.peerId; }).toList(),
                "members", members, "history", historySummary);
        if(connections!=null)roomView.put("studentConnections",connections);
        json(ex,200,map("ok",true,"room",roomView));
    }

    private void roomHistory(HttpExchange ex, WebSession s) throws IOException {
        Room room = requireCurrentRoom(s);
        List<Map<String,Object>> history = new ArrayList<>();
        for (CodeRevision r : room.snapshot().history()) history.add(map("version", r.getVersion(), "editorId", r.getEditorId(),
                "editorName", r.getEditorName(), "createdAt", r.getCreatedAt().toString(), "code", r.getSourceCode(),
                "previousVersion", r.getPreviousVersion(), "changes", r.getChanges()));
        json(ex, 200, map("ok", true, "history", history));
    }

    private void roomCommand(HttpExchange ex, WebSession s) throws Exception {
        Map<String,Object> body = readBody(ex);
        String action = text(body, "action", "").toUpperCase(Locale.ROOT);
        if (action.equals("ACK_DRAFT_RECOVERY")) {
            Room current = requireCurrentRoom(s);
            current.acknowledgeRecoverableDraft(s.coreSessionId);
            json(ex, 200, map("ok", true, "acknowledged", true));
            return;
        }
        String command;
        switch (action) {
            case "CREATE_ROOM" -> {
                if (s.identity.role() != Identity.Role.TEACHER) throw new ApiException(403, "FORBIDDEN", "Chỉ giáo viên được tạo phòng.");
                if (!currentRoomId(s).isBlank()) throw new ApiException(409, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng.");
                String roomCode=RoomCode.normalize(text(body,"roomCode",""));
                ClientHandler client=core.clients.get(s.coreSessionId);
                if(client==null)throw new ApiException(401,"SESSION_CLOSED","Phiên đăng nhập đã đóng.");
                Room created=client.createAndJoinRoom(roomCode);
                json(ex,200,map("ok",true,"roomId",created.getRoomId(),"roomCode",created.getRoomCode()));return;
            }
            case "DELETE_ROOM" -> {
                String id=text(body,"roomId","");
                List<String> members=core.deleteRoom(s.identity,id);
                if(core.store().management()!=null)core.store().management().transaction(c->{try(var p=c.prepareStatement("UPDATE classes SET active=0 WHERE class_id=?")){p.setString(1,id);p.executeUpdate();}return null;});
                for(WebSession member:sessions.values())if(members.contains(member.coreSessionId)) {
                    calls.leave(member.peerId);
                    endScreenShare(id,member.peerId,"Chủ phòng đã xóa phòng.");
                }
                json(ex,200,map("ok",true,"deletedRoomId",id));return;
            }
            case "JOIN_ROOM" -> {
                if (!currentRoomId(s).isBlank()) throw new ApiException(409, "ALREADY_IN_ROOM", "Hãy rời phòng hiện tại trước khi tham gia phòng khác.");
                String roomId = RoomCode.normalize(text(body, "roomId", ""));
                if(s.identity.role()==Identity.Role.STUDENT&&core.store().management()!=null){
                    Room target=core.findRoom(roomId);if(target==null)throw new ApiException(404,"ROOM_NOT_FOUND","Không tìm thấy lớp học đang mở.");
                    StudentPortal.requireEntry(core,s.identity,target);
                    Map<String,Object> admission=new ClassAdmission(core.store().management()).request(s.identity,target.getRoomId(),s.coreSessionId);
                    if(!"APPROVED".equals(admission.get("status"))){json(ex,202,map("ok",true,"admission",admission));return;}
                }
                command = "/join " + roomId;
            }
            case "LEAVE_ROOM" -> {
                calls.leave(s.peerId);
                String activeRoom = currentRoomId(s);
                if (!activeRoom.isBlank()) endScreenShare(activeRoom, s.peerId, "Người chia sẻ đã rời phòng.");
                ClientHandler client=core.clients.get(s.coreSessionId);
                if(client==null)throw new ApiException(401,"SESSION_CLOSED","Phiên đăng nhập đã đóng.");
                client.leaveCurrentRoom();
                json(ex,200,map("ok",true,"leftRoomId",activeRoom));return;
            }
            case "CHAT" -> {
                String message = text(body, "text", "").trim();
                if (message.isBlank() || message.length() > 2000 || message.contains("\u0000")) throw new ApiException(400, "INVALID_CHAT", "Tin nhắn phải có từ 1 đến 2000 ký tự.");
                command = "/chat " + message;
            }
            case "REFRESH_CODE" -> command = "/code";
            case "SHOW_HISTORY" -> command = "/history";
            case "REQUEST_EDIT" -> command = "/request";
            case "TEACHER_REQUEST_EDIT" -> {
                if (s.identity.role() != Identity.Role.TEACHER) throw new ApiException(403, "FORBIDDEN", "Chỉ giáo viên được ưu tiên quyền sửa.");
                command = "/teacher-request";
            }
            case "RELEASE_EDIT" -> command = "/release " + positiveLong(body, "token");
            case "UPDATE_CODE" -> {
                long version = nonNegativeLong(body, "version"); long token = positiveLong(body, "token"); String code = text(body, "code", "");
                if (code.getBytes(StandardCharsets.UTF_8).length > 32_000) throw new ApiException(413, "CODE_TOO_LARGE", "Code vượt giới hạn 32 KB.");
                command = "/update " + version + " " + token + " " + code;
            }
            case "FINISH_DRAFT" -> command = "/finish " + positiveLong(body, "token");
            case "CANCEL_DRAFT" -> command = "/cancel " + positiveLong(body, "token");
            case "SHOW_DRAFT" -> command = "/draft " + positiveLong(body, "token");
            case "SELECT_LINES" -> {
                long version = nonNegativeLong(body, "version"); long token = positiveLong(body, "token");
                String selection = text(body, "selection", "").trim();
                if (!selection.matches("[0-9,\\- ]{1,200}")) throw new ApiException(400, "INVALID_SELECTION", "Vùng chọn dòng không hợp lệ.");
                command = "/select " + version + " " + token + " " + selection;
            }
            case "SET_LINE" -> command = "/setline " + positiveLong(body, "token") + " " + positiveLong(body, "line") + " " + text(body, "text", "");
            case "DELETE_LINE" -> command = "/delete " + positiveLong(body, "token") + " " + positiveLong(body, "line");
            case "KEEP_LINE" -> command = "/keep " + positiveLong(body, "token") + " " + positiveLong(body, "line");
            case "SET_RANGE" -> command = "/setrange " + positiveLong(body, "token") + " " + text(body, "text", "");
            case "DELETE_RANGE" -> command = "/deleterange " + positiveLong(body, "token");
            case "KEEP_RANGE" -> command = "/keeprange " + positiveLong(body, "token");
            default -> throw new ApiException(400, "UNKNOWN_ACTION", "Thao tác phòng học không hợp lệ.");
        }
        // Server TCP vẫn kiểm tra thành viên phòng, chủ phòng và token quyền sửa code.
        s.send(command);
        json(ex, 202, map("ok", true, "sent", true, "action", action, "message", "Đã gửi yêu cầu tới StudyFix Server."));
    }

    private void events(HttpExchange ex, WebSession s) throws IOException {
        long after = queryLong(ex.getRequestURI(), "after", 0);
        List<Map<String,Object>> result = s.eventsAfter(after, 100);
        long next = result.isEmpty() ? after : ((Number) result.get(result.size() - 1).get("id")).longValue();
        json(ex, 200, map("ok", true, "events", result, "next", next, "connected", s.socket.isConnected() && !s.socket.isClosed()));
    }

    private void assignmentRequest(HttpExchange ex, WebSession s) throws IOException {
        Map<String,Object> body = readBody(ex);
        String type = text(body, "type", "").toUpperCase(Locale.ROOT);
        if (!ASSIGNMENT_TYPES.contains(type) && !studyfix.assignment.service.ExamOrganizationService.TYPES.contains(type)) throw new ApiException(400, "UNKNOWN_ASSIGNMENT_ACTION", "Nghiệp vụ bài tập không hợp lệ.");
        Room room=(type.startsWith("EO_")||((type.startsWith("CONTEST_")||type.startsWith("EXAM_")||type.equals("LEADERBOARD_GET"))&&currentRoomId(s).isBlank())||type.startsWith("EXAMBANK_")||type.startsWith("PRACTICE_")||type.startsWith("BANK_")||type.startsWith("OJ_")||type.startsWith("BUILDER_")||type.equals("CATALOG_CREATE"))?null:requireCurrentRoom(s);
        Map<String,Object> request = new LinkedHashMap<>(body);
        request.keySet().removeAll(Set.of("sessionId", "roomId", "requestId"));
        request.put("type", type); request.put("sessionId", s.coreSessionId);
        // EO roomId identifies an owned physical lab, not a caller-supplied classroom session.
        request.put("roomId", type.startsWith("EO_")?text(body,"roomId",""):room==null?"":room.getRoomId());
        request.put("requestId", UUID.randomUUID().toString());
        Map<String,Object> response = assignments.handleWebRequest(request);
        int status = "OK".equalsIgnoreCase(text(response, "status", "")) ? 200 : 400;
        json(ex, status, response);
    }

    private void screenSignal(HttpExchange ex, WebSession s) throws IOException {
        Room room = requireCurrentRoom(s);
        String roomId = room.getRoomId();
        Map<String,Object> body = readBody(ex);
        String kind = text(body, "kind", "").toUpperCase(Locale.ROOT);
        String targetPeerId = text(body, "toPeerId", "").trim();
        String payload = text(body, "payload", "");
        if (!Set.of("START", "REQUEST", "OFFER", "ANSWER", "ICE", "END").contains(kind))
            throw new ApiException(400, "INVALID_SIGNAL", "Loại tín hiệu chia sẻ màn hình không hợp lệ.");
        if (kind.length() + payload.length() > 100_000 || payload.length() > 96_000)
            throw new ApiException(413, "SIGNAL_TOO_LARGE", "Tín hiệu WebRTC vượt giới hạn.");

        if (kind.equals("START")) {
            registerScreenShare(roomId, s, payload);
            json(ex, 202, map("ok", true, "activePresenterPeerId", s.peerId));
            return;
        }
        if (kind.equals("END")) {
            if (!endScreenShare(roomId, s.peerId, ""))
                throw new ApiException(409, "NOT_ACTIVE_SCREEN_PRESENTER", "Phiên này không phải người đang chia sẻ màn hình.");
            json(ex, 202, map("ok", true, "stopped", true));
            return;
        }
        if (kind.equals("REQUEST")) {
            String presenter = activePresenterForRoom(roomId);
            if (presenter.isBlank()) throw new ApiException(409, "NO_ACTIVE_SCREEN_SHARE", "Hiện chưa có ai chia sẻ màn hình trong phòng.");
            if (presenter.equals(s.peerId)) {
                json(ex, 200, map("ok", true, "self", true));
                return;
            }
            WebSession target = sessionsByPeerId.get(presenter);
            if (!sessionIsInRoom(target, roomId)) {
                activeScreenSharersByRoom.remove(roomId, presenter);
                throw new ApiException(409, "NO_ACTIVE_SCREEN_SHARE", "Người chia sẻ đã rời phòng.");
            }
            target.addScreenSignal(roomId, "REQUEST", s.peerId, s.identity.displayName());
            json(ex, 202, map("ok", true, "sent", true, "activePresenterPeerId", presenter));
            return;
        }

        if (targetPeerId.isBlank())
            throw new ApiException(400, "MISSING_PEER", "Thiếu người nhận tín hiệu.");
        WebSession target = sessionsByPeerId.get(targetPeerId);
        if (!sessionIsInRoom(target, roomId))
            throw new ApiException(404, "PEER_NOT_AVAILABLE", "Thiết bị nhận không còn trong phòng hoặc chưa mở phiên web.");

        String active = activePresenterForRoom(roomId);
        if (kind.equals("OFFER")) {
            // Hỗ trợ client cũ đã tạo offer trước khi gửi START.
            if (active.isBlank()) {
                registerScreenShare(roomId, s, s.identity.displayName());
                active = s.peerId;
            }
            if (!active.equals(s.peerId))
                throw new ApiException(403, "NOT_SCREEN_PRESENTER", "Chỉ người đang chia sẻ màn hình mới được gửi OFFER.");
            if (targetPeerId.equals(active))
                throw new ApiException(400, "INVALID_SCREEN_TARGET", "Không thể gửi OFFER cho chính người chia sẻ.");
        } else if (kind.equals("ANSWER")) {
            if (active.isBlank() || !targetPeerId.equals(active) || s.peerId.equals(active))
                throw new ApiException(403, "INVALID_SCREEN_ANSWER", "ANSWER phải được gửi từ người xem tới người đang chia sẻ trong cùng phòng.");
        } else if (kind.equals("ICE")) {
            if (active.isBlank() || (!s.peerId.equals(active) && !targetPeerId.equals(active)))
                throw new ApiException(403, "INVALID_SCREEN_ICE", "ICE không thuộc phiên chia sẻ màn hình đang hoạt động.");
        }
        target.addScreenSignal(roomId, kind, s.peerId, payload);
        json(ex, 202, map("ok", true, "sent", true));
    }

    private synchronized void registerScreenShare(String roomId, WebSession publisher, String displayName) {
        String current = activePresenterForRoom(roomId);
        if (!current.isBlank() && !current.equals(publisher.peerId))
            throw new ApiException(409, "SCREEN_SHARE_ACTIVE", "Đang có thành viên khác chia sẻ màn hình. Hãy đợi họ dừng chia sẻ trước.");
        // Nếu cùng phiên trình duyệt gửi START nhiều lần thì chỉ xử lý như một lần.
        // Không phát lại START vì sẽ khiến máy khác tạo trùng offer WebRTC.
        if (publisher.peerId.equals(current)) return;
        activeScreenSharersByRoom.put(roomId, publisher.peerId);
        broadcastScreenSignal(roomId, "START", publisher.peerId,
                displayName == null || displayName.isBlank() ? publisher.identity.displayName() : displayName,
                publisher.peerId);
    }

    private boolean endScreenShare(String roomId, String peerId, String payload) {
        if (roomId == null || roomId.isBlank() || peerId == null || peerId.isBlank()) return false;
        boolean removed = activeScreenSharersByRoom.remove(roomId, peerId);
        if (removed) broadcastScreenSignal(roomId, "END", peerId, payload == null ? "" : payload, peerId);
        return removed;
    }

    private String activePresenterForRoom(String roomId) {
        String peerId = activeScreenSharersByRoom.getOrDefault(roomId, "");
        if (peerId.isBlank()) return "";
        WebSession publisher = sessionsByPeerId.get(peerId);
        if (!sessionIsInRoom(publisher, roomId)) {
            if (activeScreenSharersByRoom.remove(roomId, peerId))
                broadcastScreenSignal(roomId, "END", peerId, "Người chia sẻ đã mất kết nối.", peerId);
            return "";
        }
        return peerId;
    }

    private boolean sessionIsInRoom(WebSession session, String roomId) {
        if (session == null || session.closed || !sessions.containsKey(session.token)) return false;
        try { return Objects.equals(currentRoomId(session), roomId); }
        catch (RuntimeException e) { return false; }
    }

    private void broadcastScreenSignal(String roomId, String kind, String fromPeerId, String payload, String excludePeerId) {
        for (WebSession recipient : sessions.values()) {
            if (recipient.closed || recipient.peerId.equals(excludePeerId)) continue;
            if (sessionIsInRoom(recipient, roomId)) recipient.addScreenSignal(roomId, kind, fromPeerId, payload);
        }
    }

    private void pollScreenSignals(HttpExchange ex, WebSession s) throws IOException {
        Room room = requireCurrentRoom(s);
        String roomId = room.getRoomId();
        long after = queryLong(ex.getRequestURI(), "after", 0);
        String presenterPeerId = activePresenterForRoom(roomId);
        WebSession presenter = presenterPeerId.isBlank() ? null : sessionsByPeerId.get(presenterPeerId);
        json(ex, 200, map("ok", true, "signals", s.screenSignalsAfter(after, roomId),
                "activePresenterPeerId", presenterPeerId,
                "activePresenterName", presenter == null ? "" : presenter.identity.displayName()));
    }

    private void pruneCalls() {
        calls.prune((peer,room)->sessionIsInRoom(sessionsByPeerId.get(peer),room));
    }
    private void callSignal(HttpExchange ex,WebSession s) throws IOException {
        String room=requireCurrentRoom(s).getRoomId();Map<String,Object> body=readBody(ex);
        String kind=text(body,"kind","").toUpperCase(Locale.ROOT);pruneCalls();
        boolean audio=Boolean.TRUE.equals(body.get("audio")),video=Boolean.TRUE.equals(body.get("video"));
        switch(kind) {
            case "JOIN" -> calls.join(s.peerId,room,s.identity,audio,video);
            case "STATE" -> calls.state(s.peerId,room,audio,video);
            case "LEAVE" -> calls.leave(s.peerId);
            case "OFFER","ANSWER","ICE" -> {
                String to=text(body,"toPeerId","");
                if(!sessionIsInRoom(sessionsByPeerId.get(to),room))throw new SecurityException("Người nhận không ở trong phòng hiện tại.");
                calls.signal(s.peerId,room,to,kind,text(body,"payload",""));
            }
            default -> throw new IllegalArgumentException("Loại tín hiệu cuộc gọi không hợp lệ.");
        }
        json(ex,200,map("ok",true));
    }
    private void serveStatic(HttpExchange ex, String urlPath) throws IOException {
        if (!Files.isRegularFile(staticRoot.resolve("index.html"))) {
            json(ex, 503, map("ok", false, "error", "WEB_CLIENT_MISSING", "message", "Chưa có web-client/static/index.html. Xem thư mục web-client trong source.")); return;
        }
        String decoded;
        try { decoded = URLDecoder.decode(urlPath, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { json(ex, 400, Map.of("ok", false, "message", "Đường dẫn không hợp lệ.")); return; }
        if (decoded.equals("/")) decoded = "/index.html";
        Path file = staticRoot.resolve(decoded.substring(1)).normalize();
        if (!file.startsWith(staticRoot) || !Files.isRegularFile(file)) {
            // Chỉ trả trang chính cho đường dẫn điều hướng của ứng dụng; file tài nguyên không tồn tại vẫn trả 404.
            if (decoded.contains(".") && !decoded.endsWith(".html")) { json(ex, 404, Map.of("ok", false, "message", "Không tìm thấy tài nguyên.")); return; }
            file = staticRoot.resolve("index.html");
        }
        String contentType = contentType(file);
        ex.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }

    private static String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    private static Map<String,Object> readBody(HttpExchange ex) throws IOException {
        long announced = 0;
        try { announced = Long.parseLong(ex.getRequestHeaders().getFirst("Content-Length")); } catch (Exception ignored) { }
        if (announced > MAX_BODY_BYTES) throw new ApiException(413, "BODY_TOO_LARGE", "Nội dung yêu cầu vượt giới hạn." );
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new ApiException(413, "BODY_TOO_LARGE", "Nội dung yêu cầu vượt giới hạn.");
        if (bytes.length == 0) return new LinkedHashMap<>();
        String json = new String(bytes, StandardCharsets.UTF_8);
        try { return AssignmentJson.object(json); }
        catch (RuntimeException e) { throw new ApiException(400, "INVALID_JSON", "JSON yêu cầu không hợp lệ."); }
    }

    private static String roomSearchKey(String value) {
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT).replaceAll("\\p{M}+","").replace('đ','d');
    }
    private static String queryText(URI uri,String key) {
        String query=uri.getRawQuery();if(query==null)return "";
        for(String pair:query.split("&")) {
            String[] bits=pair.split("=",2);
            if(bits.length==2&&bits[0].equals(key))return URLDecoder.decode(bits[1],StandardCharsets.UTF_8);
        }
        return "";
    }
    private static long queryLong(URI uri, String key, long fallback) {
        String query = uri.getRawQuery(); if (query == null) return fallback;
        for (String pair : query.split("&")) { String[] bits = pair.split("=", 2); if (bits.length == 2 && bits[0].equals(key)) try { return Long.parseLong(bits[1]); } catch (NumberFormatException ignored) { } }
        return fallback;
    }
    private static long positiveLong(Map<String,Object> body, String key) { long value = AssignmentJson.number(body, key, -1); if (value <= 0) throw new ApiException(400, "INVALID_NUMBER", key + " phải là số dương."); return value; }
    private static long nonNegativeLong(Map<String,Object> body, String key) { long value = AssignmentJson.number(body, key, -1); if (value < 0) throw new ApiException(400, "INVALID_NUMBER", key + " phải là số không âm."); return value; }
    private static String text(Map<String,Object> body, String key, String fallback) { return AssignmentJson.text(body, key, fallback); }
    private static Map<String,Object> map(Object... pairs) { return AssignmentJson.mapOf(pairs); }
    private Map<String,Object> identityMap(Identity i) {
        try{return map("userId", i.userId(), "username", i.username(), "studentCode",presence.studentCode(i.userId()),"displayName", i.displayName(), "role", i.role().name());}
        catch(java.sql.SQLException e){throw new ApiException(503,"STORAGE_ERROR","Không đọc được thông tin tài khoản.");}
    }
    private static String safeMessage(Throwable e) { String m=e.getMessage(); return m==null||m.isBlank()?"Yêu cầu không hợp lệ.":m.replaceAll("[\\r\\n]", " "); }

    private static void securityHeaders(Headers h) {
        h.set("X-Content-Type-Options", "nosniff"); h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "same-origin"); h.set("Permissions-Policy", "camera=(self), microphone=(self), geolocation=()");
        h.set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; worker-src 'self' blob:; media-src 'self' blob:; img-src 'self' data: blob:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    }
    private static void corsPreflight(HttpExchange ex) throws IOException {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:"))) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Vary", "Origin"); ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        }
        ex.sendResponseHeaders(204, -1);
    }
    private static void json(HttpExchange ex, int status, Object object) throws IOException {
        byte[] b = AssignmentJson.stringify(object).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store"); ex.sendResponseHeaders(status, b.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(b); }
    }

    private void cleanup() {
        if (closed) return; long now=System.currentTimeMillis();
        for (WebSession s : sessions.values()) synchronized(s){
            if(expired(s,now)){if(!s.closed)s.disconnectReason="HEARTBEAT_TIMEOUT";closeSession(s);}
            else try{observePresence(s,false);}
            catch(java.sql.SQLException e){core.log("Không cập nhật được trạng thái kết nối; sẽ thử lại.");}
            catch(ApiException e){s.disconnectReason="CONNECTION_LOST";closeSession(s);}
        }
        pruneLoginAttempts();
    }
    private void closeSession(WebSession s) {
        if (s == null) return;
        calls.leave(s.peerId);
        try {
            String roomId = currentRoomId(s);
            if (!roomId.isBlank()) endScreenShare(roomId, s.peerId, "Người chia sẻ đã mất kết nối.");
        } catch (RuntimeException ignored) { }
        synchronized(s){
            if(!s.closed)try{observePresence(s,true);}catch(java.sql.SQLException|ApiException ignored){}
            s.close();
            // Giữ phiên đã đóng trong bộ dọn dẹp nếu DB tạm lỗi để thử ghi Offline lại.
            try{presence.offline(s.connectionId,s.lastSeen,s.disconnectReason);sessions.remove(s.token,s);}
            catch(java.sql.SQLException e){core.log("Chưa ghi được Offline; phiên đã thu hồi, sẽ thử ghi lại.");}
            sessionsByCoreId.remove(s.coreSessionId,s);sessionsByPeerId.remove(s.peerId,s);
        }
    }

    private boolean expired(WebSession s,long now){return s.closed||s.socket.isClosed()||now-s.lastSeen>=sessionTtlMillis;}
    private void touchSession(WebSession s,HttpExchange ex)throws java.sql.SQLException {
        synchronized(s){
            if(expired(s,System.currentTimeMillis()))throw new ApiException(401,"UNAUTHENTICATED","Phiên đã đóng.");
            s.touch();s.clientIp=ex.getRemoteAddress().getAddress().getHostAddress();
            // Không đọc X-Forwarded-For/Forwarded: dự án chưa cấu hình proxy tin cậy.
            observePresence(s,false);
        }
    }
    private void observePresence(WebSession s,boolean force)throws java.sql.SQLException {
        String room=currentRoomId(s);long now=System.currentTimeMillis();
        if(force||!room.equals(s.presenceRoom)||now-s.presenceSavedAt>=2000){
            if(!room.isBlank()){Room current=core.rooms.get(room);if(current!=null)admin.syncClass(current);}
            presence.observe(s.connectionId,room,s.clientIp,s.lastSeen);s.presenceRoom=room;s.presenceSavedAt=now;
        }
    }
    private void validateLiveSession(WebSession s){
        if(expired(s,System.currentTimeMillis()))throw new ApiException(401,"UNAUTHENTICATED","Phiên đã hết hạn.");
        if(core.store().management()!=null&&System.currentTimeMillis()-s.accountCheckedAt>=5000)try{
            if(!core.store().management().active(s.identity.userId())){s.disconnectReason="ACCOUNT_DISABLED";closeSession(s);throw new ApiException(401,"ACCOUNT_DISABLED","Tài khoản đã bị khóa.");}s.accountCheckedAt=System.currentTimeMillis();
        }catch(java.sql.SQLException e){throw new ApiException(503,"AUTH_DATABASE_UNAVAILABLE","Không xác minh được tài khoản SQLite.");}
    }
    private void webSocketHeartbeat(WebSession s,InetSocketAddress peer)throws java.sql.SQLException{
        synchronized(s){validateLiveSession(s);s.touch();s.clientIp=peer.getAddress().getHostAddress();observePresence(s,false);}
    }
    private void revokeUser(String user){
        for(WebSession s:List.copyOf(sessions.values()))if(s.identity.userId().equals(user)){s.disconnectReason="ACCOUNT_REVOKED";closeSession(s);}
        for(ClientHandler client:core.clients.values())if(client.identity()!=null&&client.identity().userId().equals(user))client.closeConnection();
    }
    private void detachConnection(String connection,String room){
        for(WebSession session:sessions.values())if(session.connectionId.equals(connection)){
            ClientHandler client=core.clients.get(session.coreSessionId);
            if(client!=null)client.leaveClassIfCurrent(room);
        }
    }
    private List<Map<String,Object>> roomPresence(Room room)throws IOException {
        cleanup();
        try{return presence.students(room.getRoomId());}catch(java.sql.SQLException e){throw new IOException("Không đọc được danh sách kết nối.",e);}
    }

    @Override public synchronized void close() {
        if (closed) return; closed=true; started=false; http.stop(1); scheduler.shutdownNow();
        if(presenceSocket!=null)presenceSocket.close();
        classroom.close();
        for (WebSession s : List.copyOf(sessions.values())) {s.disconnectReason="SERVER_STOP";closeSession(s);}
        executor.shutdownNow();
    }

    private record Event(long id, String message, long timestamp) { Map<String,Object> asMap() { return map("id",id,"message",message,"timestamp",timestamp); } }
    private record ScreenSignal(long id, String roomId, String kind, String fromPeerId, String payload, long timestamp) {
        Map<String,Object> asMap() { return map("id",id,"roomId",roomId,"kind",kind,"fromPeerId",fromPeerId,"payload",payload,"timestamp",timestamp); }
    }

    private static final class WebSession {
        final String token=UUID.randomUUID().toString(); final String peerId=UUID.randomUUID().toString();
        final String connectionId=UUID.randomUUID().toString();
        final long loginTime=System.currentTimeMillis();
        volatile String clientIp,presenceRoom="",disconnectReason="CONNECTION_LOST";
        volatile long presenceSavedAt;
        volatile long accountCheckedAt;
        final String coreSessionId; final Identity identity; final Socket socket;
        final DataInputStream in; final DataOutputStream out;
        final AtomicLong eventSequence=new AtomicLong(), screenSequence=new AtomicLong(),departureGeneration=new AtomicLong();
        final Deque<Event> events=new ArrayDeque<>(); final Deque<ScreenSignal> screenSignals=new ArrayDeque<>();
        volatile long lastSeen=System.currentTimeMillis(); volatile boolean closed;
        volatile ScheduledFuture<?> heartbeat;
        volatile ScheduledFuture<?> departure;
        void touch(){lastSeen=System.currentTimeMillis();departureGeneration.incrementAndGet();ScheduledFuture<?> pending=departure;if(pending!=null)pending.cancel(false);}
        private WebSession(Socket socket, DataInputStream in, DataOutputStream out, String coreSessionId, Identity identity) {
            this.socket=socket; this.in=in; this.out=out; this.coreSessionId=coreSessionId; this.identity=identity;
        }
        static WebSession login(Server core, String username, String password,String ip) throws Exception {
            Socket socket = new Socket(); socket.connect(new InetSocketAddress("127.0.0.1", core.port()), 4000); socket.setTcpNoDelay(true); socket.setSoTimeout(8000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            Protocol.send(out, "/login " + username + " " + password);
            String response;
            try { response=Protocol.receive(in); } catch (Exception e) { socket.close(); throw new ApiException(503,"AUTH_SERVER_UNAVAILABLE","Không nhận được phản hồi xác thực từ server."); }
            Matcher m=LOGIN_OK.matcher(response);
            if (!m.matches()) {
                socket.close();
                if(response.startsWith("[LOGIN_FAILED] Database"))throw new ApiException(503,"AUTH_DATABASE_UNAVAILABLE","Database xác thực chưa sẵn sàng. Kiểm tra dịch vụ và cấu hình SQLite.");
                throw new ApiException(401,"LOGIN_FAILED", response.startsWith("[LOGIN_FAILED]")?"Sai tên đăng nhập hoặc mật khẩu.":safeMessage(new IOException(response)));
            }
            Identity identity=core.gateway().identity(m.group(1));
            socket.setSoTimeout(0);
            WebSession result=new WebSession(socket,in,out,m.group(1),identity);
            result.clientIp=ip;
            Thread reader=new Thread(result::readLoop,"studyfix-web-session-"+username); reader.setDaemon(true); reader.start();
            return result;
        }
        synchronized void send(String command) throws IOException { if (closed) throw new IOException("Phiên web đã đóng."); Protocol.send(out, command); }
        private void readLoop() {
            try { while(!closed) { String message=Protocol.receive(in); if (message.equals("/pong")) continue; addEvent(message); } }
            catch (IOException e) { if(!closed) addEvent("[CONNECTION_LOST] Kết nối tới StudyFix Server đã đóng."); }
            finally { close(); }
        }
        private synchronized void addEvent(String message) {
            Event e=new Event(eventSequence.incrementAndGet(),message,System.currentTimeMillis()); events.addLast(e); while(events.size()>500) events.removeFirst();
        }
        synchronized List<Map<String,Object>> eventsAfter(long after,int max) { List<Map<String,Object>> out=new ArrayList<>(); for(Event e:events) if(e.id()>after){out.add(e.asMap());if(out.size()>=max)break;} return out; }
        synchronized void addScreenSignal(String roomId,String kind,String fromPeerId,String payload) { screenSignals.addLast(new ScreenSignal(screenSequence.incrementAndGet(),roomId,kind,fromPeerId,payload,System.currentTimeMillis()));while(screenSignals.size()>200)screenSignals.removeFirst(); }
        synchronized List<Map<String,Object>> screenSignalsAfter(long after,String roomId) { List<Map<String,Object>> result=new ArrayList<>();for(ScreenSignal s:screenSignals)if(s.id()>after&&Objects.equals(s.roomId(),roomId))result.add(s.asMap());return result; }
        void close(){
            closed=true;
            ScheduledFuture<?> pending=departure;if(pending!=null)pending.cancel(false);
            ScheduledFuture<?> ping=heartbeat;if(ping!=null)ping.cancel(false);
            // Closing the socket is sufficient to detach the core session and cannot block on a write.
            try{socket.close();}catch(IOException ignored){}
        }
    }
    private static final class ApiException extends RuntimeException {
        final int status; final String code;
        ApiException(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
}
