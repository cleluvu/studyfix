package studyfix;

import studyfix.person3.GradingWorker;
import studyfix.person3.Models;
import studyfix.person3.Models.*;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Person-3: giao bài, nộp bài, worker chấm, thi, xếp hạng.
 * Authz via {@link StudyFixGateway}; persistence via {@link Store}.
 */
public final class Person3Service implements AutoCloseable {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(VN);

    private final Server server;
    private final StudyFixGateway gateway;
    private final GradingWorker worker = new GradingWorker();
    /** roomId → exam end scheduler handle via monitor. */
    private final ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> examTimers =
            new ConcurrentHashMap<>();

    public Person3Service(Server server) {
        this.server = server;
        this.gateway = server.gateway();
    }

    /** @return true if the command was a person-3 verb (handled or rejected with reply). */
    public boolean handle(ClientHandler client, String raw) throws IOException {
        String command = raw.trim();
        String verb = command.split("[ \t]", 2)[0];
        if (!Set.of("/assign", "/submit", "/grades", "/grade", "/exam", "/rank", "/myscores", "/catalog")
                .contains(verb)) {
            return false;
        }
        Identity identity = client.identity();
        if (identity == null) {
            client.send("Bạn cần đăng nhập trước.");
            return true;
        }
        String session = client.getMemberId();
        if (verb.equals("/catalog")) {
            try { handleCatalog(client, identity, session, raw); }
            catch (SecurityException e) { client.send("[P3_ERROR] " + e.getMessage()); }
            catch (SQLException e) {
                server.log("Person3 SQL: " + e.getMessage());
                client.send("[P3_ERROR] Database không sẵn sàng.");
            } catch (IllegalArgumentException e) {
                client.send("[P3_ERROR] " + (e.getMessage() == null ? "Sai cú pháp." : e.getMessage()));
            }
            return true;
        }
        Room room = client.currentRoom();
        if (room == null) {
            client.send("Bạn chưa tham gia phòng. Vào phòng rồi dùng lệnh bài tập/thi.");
            return true;
        }
        String roomId = room.getRoomId();
        try {
            if (verb.equals("/assign")) handleAssign(client, identity, session, roomId, raw);
            else if (verb.equals("/submit")) handleSubmit(client, identity, session, roomId, raw);
            else if (verb.equals("/grades")) handleGrades(client, identity, session, roomId, command);
            else if (verb.equals("/grade")) handleGrade(client, identity, session, roomId, command);
            else if (verb.equals("/exam")) handleExam(client, identity, session, roomId, command);
            else if (verb.equals("/rank")) handleRank(client, identity, session, roomId);
            else if (verb.equals("/myscores")) handleMyScores(client, identity, session, roomId);
        } catch (SecurityException e) {
            client.send("[P3_ERROR] " + e.getMessage());
        } catch (SQLException e) {
            server.log("Person3 SQL: " + e.getMessage());
            client.send("[P3_ERROR] Database không sẵn sàng.");
        } catch (IllegalArgumentException e) {
            client.send("[P3_ERROR] " + (e.getMessage() == null ? "Sai cú pháp." : e.getMessage()));
        }
        return true;
    }

    private void handleAssign(ClientHandler client, Identity identity, String session,
                              String roomId, String raw) throws IOException, SQLException {
        // /assign list
        // /assign show id
        // /assign close id
        // /assign create maxScore title | statement | tests
        String body = raw.length() > 7 ? raw.substring(7).trim() : "";
        if (body.isEmpty() || body.equals("list")) {
            requireMember(session, roomId);
            List<Assignment> list = server.store().listAssignments(roomId);
            client.send(formatAssignList(list));
            return;
        }
        if (body.startsWith("show ")) {
            requireMember(session, roomId);
            long id = Long.parseLong(body.substring(5).trim());
            Assignment a = server.store().getAssignment(id);
            if (a == null || !a.roomId().equals(roomId)) throw new IllegalArgumentException("Không tìm thấy bài tập.");
            client.send(formatAssignDetail(a));
            return;
        }
        if (body.startsWith("close ")) {
            requireTeacher(session, roomId);
            long id = Long.parseLong(body.substring(6).trim());
            Assignment a = server.store().getAssignment(id);
            if (a == null || !a.roomId().equals(roomId)) throw new IllegalArgumentException("Không tìm thấy bài tập.");
            server.store().closeAssignment(id);
            gateway.sendToRoom(session, roomId, "[ASSIGN] CLOSED id=" + id + " title=" + a.title());
            client.send("[ASSIGN] Đã đóng bài tập #" + id);
            return;
        }
        if (body.startsWith("create ")) {
            requireTeacher(session, roomId);
            // create <maxScore> <title> | <statement> | <tests>
            String rest = body.substring(7).trim();
            String[] pipe = rest.split("\\s*\\|\\s*", 3);
            if (pipe.length < 2) {
                throw new IllegalArgumentException(
                        "Cách dùng: /assign create điểm tiêu_đề | đề_bài [| test: stdin>>>stdout;;...]");
            }
            String[] head = pipe[0].trim().split("\\s+", 2);
            if (head.length < 2) {
                throw new IllegalArgumentException("Thiếu điểm tối đa hoặc tiêu đề.");
            }
            int maxScore = Integer.parseInt(head[0]);
            if (maxScore < 1 || maxScore > 1000) throw new IllegalArgumentException("Điểm tối đa 1–1000.");
            String title = head[1].trim();
            String statement = pipe[1].trim();
            String tests = pipe.length >= 3 ? pipe[2].trim() : "";
            if (title.isEmpty() || statement.isEmpty()) {
                throw new IllegalArgumentException("Tiêu đề và đề bài không được trống.");
            }
            if (title.length() > 120 || statement.length() > 8000 || tests.length() > 8000) {
                throw new IllegalArgumentException("Tiêu đề/đề/test quá dài.");
            }
            parseTests(tests); // validate
            Assignment created = server.store().createAssignment(
                    roomId, title, statement, maxScore, tests, identity.userId());
            String event = "[ASSIGN] CREATED id=" + created.id() + " title=" + created.title()
                    + " max=" + created.maxScore();
            gateway.sendToRoom(session, roomId, event);
            client.send(formatAssignDetail(created));
            server.log("Giao bài #" + created.id() + " phòng " + roomId);
            return;
        }
        throw new IllegalArgumentException(
                "Cách dùng: /assign list|show id|close id|create điểm tiêu_đề | đề [| tests]");
    }

    private void handleCatalog(ClientHandler client, Identity identity, String session, String raw)
            throws IOException, SQLException {
        String body = raw.length() > 8 ? raw.substring(8).trim() : "";
        if (body.isEmpty() || body.equals("list") || body.startsWith("list")) {
            String topic = "ALL";
            if (body.startsWith("list")) {
                String rest = body.substring(4).trim();
                if (!rest.isEmpty()) topic = rest.split("\\s+")[0];
            }
            List<CatalogItem> list = server.store().listCatalog(topic);
            StringBuilder sb = new StringBuilder("[CATALOG_LIST] topic=").append(topic.toUpperCase(Locale.ROOT))
                    .append(" count=").append(list.size()).append('\n');
            if (list.isEmpty()) sb.append("(không có đề — topic: IO STRING CLASS STACK QUEUE TREE)\n");
            for (CatalogItem it : list) {
                sb.append('#').append(it.id()).append(" [").append(it.topic()).append("] ")
                        .append(it.code()).append(" ").append(it.title())
                        .append(" — ").append(it.testCount()).append(" test, max ").append(it.maxScore())
                        .append('\n');
            }
            client.send(sb.toString().stripTrailing());
            return;
        }
        if (body.startsWith("show ")) {
            long id = Long.parseLong(body.substring(5).trim());
            CatalogItem it = server.store().getCatalog(id);
            if (it == null) throw new IllegalArgumentException("Không tìm thấy đề catalog #" + id);
            client.send("[CATALOG] id=" + it.id() + " " + it.topic() + " " + it.code()
                    + " max=" + it.maxScore() + " tests=" + it.testCount()
                    + " title=" + it.title()
                    + "\n--- ĐỀ ---\n" + it.statement()
                    + "\n(Test ẩn phía server; giao vào phòng bằng /catalog assign " + it.id() + ")");
            return;
        }
        if (body.startsWith("assign ")) {
            Room room = client.currentRoom();
            if (room == null) throw new IllegalArgumentException("Vào phòng trước khi gán đề.");
            String roomId = room.getRoomId();
            requireTeacher(session, roomId);
            long id = Long.parseLong(body.substring(7).trim());
            CatalogItem it = server.store().getCatalog(id);
            if (it == null) throw new IllegalArgumentException("Không tìm thấy đề catalog #" + id);
            Assignment created = server.store().createAssignment(
                    roomId, it.code() + " " + it.title(), it.statement(),
                    it.maxScore(), it.testsRaw(), identity.userId(), it.topic());
            String event = "[ASSIGN] CREATED id=" + created.id() + " title=" + created.title()
                    + " max=" + created.maxScore() + " fromCatalog=" + it.id();
            gateway.sendToRoom(session, roomId, event);
            client.send(formatAssignDetail(created));
            server.log("Gán catalog #" + it.id() + " -> bài #" + created.id() + " phòng " + roomId);
            return;
        }
        throw new IllegalArgumentException("Cách dùng: /catalog list [IO|STRING|CLASS|STACK|QUEUE|TREE] | show id | assign id");
    }

    private void handleSubmit(ClientHandler client, Identity identity, String session,
                              String roomId, String raw) throws IOException, SQLException {
        requireMember(session, roomId);
        // /submit id   OR  /submit id | code...
        String body = raw.length() > 7 ? raw.substring(7).trim() : "";
        if (body.isEmpty()) throw new IllegalArgumentException("Cách dùng: /submit mã_bài [| code]");
        String code;
        long assignId;
        int pipe = body.indexOf('|');
        if (pipe < 0) {
            assignId = Long.parseLong(body.trim());
            Room.Snapshot snap = gateway.codeSnapshot(session, roomId);
            code = snap.code();
        } else {
            assignId = Long.parseLong(body.substring(0, pipe).trim());
            code = body.substring(pipe + 1);
            if (code.startsWith(" ")) code = code.substring(1);
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Code trống — không nộp được.");
        }
        if (code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32_000) {
            throw new IllegalArgumentException("Code vượt 32000 byte.");
        }
        Assignment a = server.store().getAssignment(assignId);
        if (a == null || !a.roomId().equals(roomId)) throw new IllegalArgumentException("Không tìm thấy bài tập.");
        if (!"OPEN".equals(a.status())) throw new IllegalArgumentException("Bài tập đã đóng.");
        Exam exam = server.store().activeExam(roomId);
        if (exam != null && Instant.now().isAfter(exam.endsAt())) {
            endExamInternal(roomId, session, false);
            throw new IllegalArgumentException("Kỳ thi đã hết giờ.");
        }
        Submission sub = server.store().createSubmission(
                assignId, roomId, identity.userId(), identity.displayName(), code, a.maxScore());
        client.send("[SUBMIT_OK] submission=" + sub.id() + " assignment=" + assignId
                + " status=PENDING — đang xếp hàng chấm…");
        gateway.sendToRoom(session, roomId,
                "[SUBMIT] user=" + identity.displayName() + " assignment=" + assignId
                        + " submission=" + sub.id());
        List<TestCase> tests = a.tests();
        worker.gradeAsync(sub.id(), code, a.maxScore(), tests, (sid, result) -> {
            try {
                server.store().completeGrade(sid, result.score(), result.details());
                String msg = "[GRADE_RESULT] submission=" + sid
                        + " assignment=" + assignId
                        + " user=" + identity.displayName()
                        + " score=" + result.score() + "/" + result.maxScore()
                        + "\n" + result.details();
                try {
                    client.send(msg);
                    gateway.sendToRoom(session, roomId,
                            "[GRADE_RESULT] submission=" + sid + " assignment=" + assignId
                                    + " user=" + identity.displayName()
                                    + " score=" + result.score() + "/" + result.maxScore());
                } catch (IOException | SecurityException ignored) {
                    // Session may have left; grade is persisted.
                }
                server.log("Chấm xong submission=" + sid + " score=" + result.score());
            } catch (SQLException e) {
                server.log("Lỗi lưu điểm submission=" + sid + ": " + e.getMessage());
            }
        });
    }

    private void handleGrades(ClientHandler client, Identity identity, String session,
                              String roomId, String command) throws IOException, SQLException {
        requireMember(session, roomId);
        String[] p = command.split("\\s+");
        Long assignFilter = null;
        if (p.length == 2) assignFilter = Long.parseLong(p[1]);
        else if (p.length > 2) throw new IllegalArgumentException("Cách dùng: /grades [mã_bài]");
        boolean teacher = gateway.isTeacher(session, roomId);
        List<Submission> list = server.store().listSubmissions(roomId, assignFilter);
        StringBuilder sb = new StringBuilder("[GRADES] phòng ").append(roomId).append('\n');
        int shown = 0;
        for (Submission s : list) {
            if (!teacher && !s.userId().equals(identity.userId())) continue;
            sb.append('#').append(s.id())
                    .append(" bài=").append(s.assignmentId())
                    .append(" | ").append(s.displayName())
                    .append(" | ").append(s.status())
                    .append(" | điểm=")
                    .append(s.score() == null ? "—" : s.score() + "/" + s.maxScore())
                    .append(" | ").append(FMT.format(s.submittedAt()))
                    .append('\n');
            shown++;
        }
        if (shown == 0) sb.append("(chưa có bài nộp)\n");
        client.send(sb.toString().stripTrailing());
    }

    private void handleGrade(ClientHandler client, Identity identity, String session,
                             String roomId, String command) throws IOException, SQLException {
        // /grade set submissionId score
        requireTeacher(session, roomId);
        String[] p = command.split("\\s+");
        if (p.length != 4 || !p[1].equals("set")) {
            throw new IllegalArgumentException("Cách dùng: /grade set mã_nộp điểm");
        }
        long sid = Long.parseLong(p[2]);
        int score = Integer.parseInt(p[3]);
        Submission sub = server.store().getSubmission(sid);
        if (sub == null || !sub.roomId().equals(roomId)) {
            throw new IllegalArgumentException("Không tìm thấy bài nộp.");
        }
        if (score < 0 || score > sub.maxScore()) {
            throw new IllegalArgumentException("Điểm phải trong 0–" + sub.maxScore());
        }
        server.store().completeGrade(sid, score,
                "Giáo viên chấm tay: " + score + "/" + sub.maxScore()
                        + " bởi " + identity.displayName());
        String msg = "[GRADE_RESULT] submission=" + sid + " assignment=" + sub.assignmentId()
                + " user=" + sub.displayName() + " score=" + score + "/" + sub.maxScore()
                + " (chấm tay)";
        gateway.sendToRoom(session, roomId, msg);
        client.send(msg);
    }

    private void handleExam(ClientHandler client, Identity identity, String session,
                            String roomId, String command) throws IOException, SQLException {
        String[] p = command.split("\\s+", 3);
        if (p.length < 2) {
            throw new IllegalArgumentException("Cách dùng: /exam start phút [tiêu_đề] | status | end");
        }
        switch (p[1]) {
            case "status" -> {
                requireMember(session, roomId);
                Exam exam = server.store().activeExam(roomId);
                if (exam == null) {
                    Exam last = server.store().latestExam(roomId);
                    client.send(last == null ? "[EXAM] Không có kỳ thi trong phòng."
                            : "[EXAM] Kỳ gần nhất #" + last.id() + " status=" + last.status()
                            + " " + FMT.format(last.startsAt()) + " → " + FMT.format(last.endsAt()));
                } else {
                    long left = Math.max(0, Duration.between(Instant.now(), exam.endsAt()).getSeconds());
                    client.send("[EXAM] RUNNING id=" + exam.id() + " title=" + exam.title()
                            + " còn " + left + "s | hết lúc " + FMT.format(exam.endsAt()));
                }
            }
            case "start" -> {
                requireTeacher(session, roomId);
                if (p.length < 3) throw new IllegalArgumentException("Cách dùng: /exam start phút [tiêu_đề]");
                String rest = p[2].trim();
                String[] parts = rest.split("\\s+", 2);
                int minutes = Integer.parseInt(parts[0]);
                if (minutes < 1 || minutes > 180) throw new IllegalArgumentException("Thời gian thi 1–180 phút.");
                String title = parts.length > 1 ? parts[1].trim() : "Kỳ thi phòng " + roomId;
                if (server.store().activeExam(roomId) != null) {
                    throw new IllegalArgumentException("Đã có kỳ thi đang chạy. /exam end trước.");
                }
                Instant start = Instant.now();
                Instant end = start.plus(Duration.ofMinutes(minutes));
                Exam exam = server.store().createExam(roomId, title, start, end, identity.userId());
                scheduleExamEnd(roomId, exam.id(), end);
                String event = "[EXAM] STARTED id=" + exam.id() + " title=" + title
                        + " minutes=" + minutes + " ends=" + FMT.format(end);
                gateway.sendToRoom(session, roomId, event);
                client.send(event);
                server.log("Bắt đầu thi #" + exam.id() + " phòng " + roomId + " " + minutes + " phút");
            }
            case "end" -> {
                requireTeacher(session, roomId);
                endExamInternal(roomId, session, true);
            }
            default -> throw new IllegalArgumentException("Cách dùng: /exam start|status|end");
        }
    }

    private void scheduleExamEnd(String roomId, long examId, Instant end) {
        cancelExamTimer(roomId);
        long delay = Math.max(1, Duration.between(Instant.now(), end).toMillis());
        var future = server.monitor.schedule(() -> {
            try {
                Exam active = server.store().activeExam(roomId);
                if (active != null && active.id() == examId) {
                    server.store().endExam(examId);
                    server.store().closeOpenAssignments(roomId);
                    String msg = "[EXAM] ENDED id=" + examId + " (hết giờ) — bài tập OPEN đã đóng.";
                    // Broadcast without actor: notify all members via room
                    Room room = server.rooms.get(roomId);
                    if (room != null) room.broadcast(msg);
                    server.log("Hết giờ thi #" + examId + " phòng " + roomId);
                }
            } catch (Exception e) {
                server.log("Lỗi kết thúc thi tự động: " + e.getMessage());
            } finally {
                examTimers.remove(roomId);
            }
        }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
        examTimers.put(roomId, future);
    }

    private void cancelExamTimer(String roomId) {
        var f = examTimers.remove(roomId);
        if (f != null) f.cancel(false);
    }

    private void endExamInternal(String roomId, String actorSession, boolean replyToActor)
            throws IOException, SQLException {
        Exam active = server.store().activeExam(roomId);
        if (active == null) {
            if (replyToActor) {
                ClientHandler c = server.clients.get(actorSession);
                if (c != null) c.send("[EXAM] Không có kỳ thi đang chạy.");
            }
            return;
        }
        cancelExamTimer(roomId);
        server.store().endExam(active.id());
        server.store().closeOpenAssignments(roomId);
        String event = "[EXAM] ENDED id=" + active.id() + " title=" + active.title()
                + " — bài tập OPEN đã đóng.";
        if (actorSession != null && gateway.isMember(actorSession, roomId)) {
            gateway.sendToRoom(actorSession, roomId, event);
        } else {
            Room room = server.rooms.get(roomId);
            if (room != null) room.broadcast(event);
        }
        server.log("Kết thúc thi #" + active.id() + " phòng " + roomId);
    }

    private void handleRank(ClientHandler client, Identity identity, String session, String roomId)
            throws IOException, SQLException {
        requireMember(session, roomId);
        List<RankEntry> ranks = server.store().ranking(roomId);
        StringBuilder sb = new StringBuilder("[RANK] phòng ").append(roomId).append('\n');
        if (ranks.isEmpty()) sb.append("(chưa có điểm)\n");
        else {
            for (RankEntry e : ranks) {
                sb.append('#').append(e.rank()).append(' ')
                        .append(e.displayName())
                        .append(" — ").append(e.totalScore()).append(" điểm")
                        .append(" (").append(e.submissions()).append(" lần nộp)\n");
            }
        }
        client.send(sb.toString().stripTrailing());
    }

    private void handleMyScores(ClientHandler client, Identity identity, String session, String roomId)
            throws IOException, SQLException {
        requireMember(session, roomId);
        List<Submission> list = server.store().listSubmissions(roomId, null);
        StringBuilder sb = new StringBuilder("[MYSCORES] ").append(identity.displayName()).append('\n');
        int bestTotal = 0;
        Map<Long, Integer> best = new LinkedHashMap<>();
        for (Submission s : list) {
            if (!s.userId().equals(identity.userId())) continue;
            sb.append("nộp #").append(s.id()).append(" bài=").append(s.assignmentId())
                    .append(" ").append(s.status()).append(" ")
                    .append(s.score() == null ? "—" : s.score() + "/" + s.maxScore())
                    .append('\n');
            if (s.score() != null) {
                best.merge(s.assignmentId(), s.score(), Math::max);
            }
        }
        for (int v : best.values()) bestTotal += v;
        sb.append("Tổng điểm tốt nhất theo bài: ").append(bestTotal);
        client.send(sb.toString());
    }

    public RoomOverview overview(String roomId) throws SQLException {
        return new RoomOverview(
                server.store().listAssignments(roomId),
                server.store().listSubmissions(roomId, null),
                server.store().activeExam(roomId),
                server.store().ranking(roomId));
    }

    private void requireMember(String session, String roomId) {
        if (!gateway.isMember(session, roomId)) throw new SecurityException("Không thuộc phòng.");
    }

    private void requireTeacher(String session, String roomId) {
        requireMember(session, roomId);
        if (!gateway.isTeacher(session, roomId)) {
            throw new SecurityException("Chỉ giáo viên chủ phòng được thực hiện.");
        }
    }

    public static List<TestCase> parseTests(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<TestCase> out = new ArrayList<>();
        for (String part : raw.split(";;")) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            int sep = piece.indexOf(">>>");
            if (sep < 0) {
                throw new IllegalArgumentException(
                        "Mỗi test: stdin>>>stdout, các test cách nhau ;;");
            }
            out.add(new TestCase(piece.substring(0, sep), piece.substring(sep + 3)));
        }
        return out;
    }

    private static String formatAssignList(List<Assignment> list) {
        StringBuilder sb = new StringBuilder("[ASSIGN_LIST]\n");
        if (list.isEmpty()) sb.append("(chưa có bài tập)\n");
        for (Assignment a : list) {
            sb.append('#').append(a.id()).append(" [").append(a.status()).append("] ");
            if (a.topic() != null && !a.topic().isBlank()) sb.append(a.topic()).append(' ');
            sb.append(a.title()).append(" — max ").append(a.maxScore())
                    .append(" — ").append(a.tests().size()).append(" test\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String formatAssignDetail(Assignment a) {
        return "[ASSIGN] id=" + a.id()
                + " status=" + a.status()
                + " max=" + a.maxScore()
                + " title=" + a.title()
                + "\n--- ĐỀ ---\n" + a.statement()
                + "\n--- TESTS: " + a.tests().size() + " case (ẩn, chấm tự động) ---\n"
                + "Tạo: " + FMT.format(a.createdAt());
    }

    @Override
    public void close() {
        for (String roomId : new ArrayList<>(examTimers.keySet())) cancelExamTimer(roomId);
        worker.close();
    }
}
