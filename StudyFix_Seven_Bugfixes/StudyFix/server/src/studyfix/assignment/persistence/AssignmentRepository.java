package studyfix.assignment.persistence;

import studyfix.assignment.model.AssignmentRecord;
import studyfix.assignment.model.Exercise;
import studyfix.assignment.model.ExerciseMode;
import studyfix.assignment.model.SharedCheckRun;
import studyfix.assignment.model.GradingResult;
import studyfix.assignment.model.SubmissionRecord;
import studyfix.assignment.model.ContestSubmissionRecord;
import studyfix.assignment.model.TeacherTrialRecord;
import studyfix.assignment.protocol.AssignmentJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Quản lý database học tập riêng, không mở hay đổi cấu trúc database chính của Store. */
public final class AssignmentRepository {
    private final String url;

    public AssignmentRepository(Path db) throws Exception {
        Path parent = db.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        url = "jdbc:sqlite:" + db.toAbsolutePath();
        try (Connection c = open()) { createSchema(c); ContestDistributionRepository.init(c); QuestionBankRepository.init(c); }
    }

    Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    private void createSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_schema_meta(version INTEGER NOT NULL)");
            s.executeUpdate("INSERT INTO p3_schema_meta(version) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM p3_schema_meta)");
            StructureResultStore.ensure(c);
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_exercises(id TEXT PRIMARY KEY,title TEXT NOT NULL,topic TEXT NOT NULL,difficulty TEXT NOT NULL,statement TEXT NOT NULL,input_format TEXT NOT NULL,output_format TEXT NOT NULL,constraints_text TEXT NOT NULL,template TEXT NOT NULL,hints TEXT NOT NULL,reference_solution TEXT NOT NULL,catalog_version INTEGER NOT NULL,created_at INTEGER NOT NULL,mode TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH',starter_code TEXT NOT NULL DEFAULT '')");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_test_cases(id INTEGER PRIMARY KEY AUTOINCREMENT,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id) ON DELETE CASCADE,ordinal_no INTEGER NOT NULL,input_text TEXT NOT NULL,expected_output TEXT NOT NULL,weight INTEGER NOT NULL,hidden INTEGER NOT NULL,UNIQUE(exercise_id,ordinal_no))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_assignments(id TEXT PRIMARY KEY,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id),room_id TEXT NOT NULL,created_by_user_id TEXT NOT NULL,scope TEXT NOT NULL CHECK(scope IN ('ROOM','INDIVIDUAL')),target_user_id TEXT,target_username TEXT,created_at INTEGER NOT NULL,deadline_at INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',mode TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH')");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_assignment_room_idx ON p3_assignments(room_id,created_at)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_assignment_recipient_snapshots(assignment_id TEXT PRIMARY KEY REFERENCES p3_assignments(id) ON DELETE CASCADE,captured_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_assignment_recipients(assignment_id TEXT NOT NULL REFERENCES p3_assignments(id) ON DELETE CASCADE,user_id TEXT NOT NULL,username TEXT NOT NULL,display_name TEXT NOT NULL,PRIMARY KEY(assignment_id,user_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_submissions(id TEXT PRIMARY KEY,assignment_id TEXT NOT NULL REFERENCES p3_assignments(id),room_id TEXT NOT NULL,user_id TEXT NOT NULL,username TEXT NOT NULL,source_code TEXT NOT NULL,submitted_at INTEGER NOT NULL,status TEXT NOT NULL,score REAL NOT NULL DEFAULT 0,max_score REAL NOT NULL DEFAULT 100,verdict TEXT NOT NULL DEFAULT 'QUEUED',passed_tests INTEGER NOT NULL DEFAULT 0,total_tests INTEGER NOT NULL DEFAULT 0,feedback TEXT NOT NULL DEFAULT '',duration_ms INTEGER NOT NULL DEFAULT 0,submission_mode TEXT NOT NULL DEFAULT 'PERSONAL',idempotency_key TEXT NOT NULL UNIQUE)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_submission_user_idx ON p3_submissions(assignment_id,user_id,submitted_at)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_teacher_trials(id TEXT PRIMARY KEY,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id),room_id TEXT NOT NULL,teacher_user_id TEXT NOT NULL,teacher_username TEXT NOT NULL,source_code TEXT NOT NULL,submitted_at INTEGER NOT NULL,status TEXT NOT NULL,score REAL NOT NULL DEFAULT 0,max_score REAL NOT NULL DEFAULT 100,verdict TEXT NOT NULL DEFAULT 'QUEUED',passed_tests INTEGER NOT NULL DEFAULT 0,total_tests INTEGER NOT NULL DEFAULT 0,feedback TEXT NOT NULL DEFAULT '',duration_ms INTEGER NOT NULL DEFAULT 0,idempotency_key TEXT NOT NULL UNIQUE)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_teacher_trial_owner_idx ON p3_teacher_trials(room_id,teacher_user_id,submitted_at)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_teacher_trial_test_results(id INTEGER PRIMARY KEY AUTOINCREMENT,trial_id TEXT NOT NULL REFERENCES p3_teacher_trials(id) ON DELETE CASCADE,ordinal_no INTEGER NOT NULL,hidden INTEGER NOT NULL,passed INTEGER NOT NULL,message TEXT NOT NULL,duration_ms INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_test_results(id INTEGER PRIMARY KEY AUTOINCREMENT,submission_id TEXT NOT NULL REFERENCES p3_submissions(id) ON DELETE CASCADE,ordinal_no INTEGER NOT NULL,hidden INTEGER NOT NULL,passed INTEGER NOT NULL,message TEXT NOT NULL,duration_ms INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contests(id TEXT PRIMARY KEY,room_id TEXT NOT NULL,title TEXT NOT NULL,created_by_user_id TEXT NOT NULL,created_at INTEGER NOT NULL,start_at INTEGER NOT NULL,end_at INTEGER NOT NULL,status TEXT NOT NULL,scoring_rule_version TEXT NOT NULL,contest_type TEXT NOT NULL DEFAULT 'INDIVIDUAL')");
            // Cấu trúc mới gắn câu hỏi cuộc thi trực tiếp với ID trong ngân hàng đề. Database v3 cũ
            // được nâng cấp bên dưới và vẫn giữ liên kết với bài đã giao.
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_problems(contest_id TEXT NOT NULL REFERENCES p3_contests(id) ON DELETE CASCADE,problem_id TEXT NOT NULL,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id),assignment_id TEXT REFERENCES p3_assignments(id),mode TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH',max_score REAL NOT NULL,title_snapshot TEXT NOT NULL DEFAULT '',statement_snapshot TEXT NOT NULL DEFAULT '',catalog_version INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(contest_id,problem_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_submissions(contest_id TEXT NOT NULL REFERENCES p3_contests(id) ON DELETE CASCADE,submission_id TEXT NOT NULL REFERENCES p3_submissions(id) ON DELETE CASCADE,PRIMARY KEY(contest_id,submission_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_shared_check_runs(" +
                    "id TEXT PRIMARY KEY,request_key TEXT NOT NULL UNIQUE,assignment_id TEXT NOT NULL REFERENCES p3_assignments(id)," +
                    "room_id TEXT NOT NULL,checked_version INTEGER NOT NULL,source_hash TEXT NOT NULL,source_code TEXT NOT NULL," +
                    "requested_by_user_id TEXT NOT NULL,requested_by_username TEXT NOT NULL,created_at INTEGER NOT NULL," +
                    "status TEXT NOT NULL,verdict TEXT NOT NULL,score REAL NOT NULL DEFAULT 0,max_score REAL NOT NULL DEFAULT 100," +
                    "passed_tests INTEGER NOT NULL DEFAULT 0,total_tests INTEGER NOT NULL DEFAULT 0,feedback TEXT NOT NULL DEFAULT '',duration_ms INTEGER NOT NULL DEFAULT 0)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_shared_check_assignment_idx ON p3_shared_check_runs(assignment_id,room_id,created_at)");
        }
        // Nâng cấp theo phiên bản, chỉ thêm cấu trúc trong database học tập, giữ nguyên database chính của Store.
        ensureColumn(c, "p3_submissions", "submission_mode", "TEXT NOT NULL DEFAULT 'PERSONAL'");
        ensureColumn(c,"p3_exercises","java_structure","TEXT NOT NULL DEFAULT 'PROGRAM'");
        ensureColumn(c,"p3_exercises","entry_file","TEXT NOT NULL DEFAULT 'Main.java'");
        ensureColumn(c, "p3_exercises", "mode", "TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH'");
        ensureColumn(c, "p3_exercises", "starter_code", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(c, "p3_assignments", "mode", "TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH'");
        ensureColumn(c, "p3_contests", "contest_type", "TEXT NOT NULL DEFAULT 'INDIVIDUAL'");
        try (Statement fix = c.createStatement()) {
            fix.executeUpdate("UPDATE p3_exercises SET starter_code=template WHERE starter_code IS NULL OR starter_code=''");
            fix.executeUpdate("UPDATE p3_assignments SET mode=(SELECT mode FROM p3_exercises WHERE p3_exercises.id=p3_assignments.exercise_id) WHERE mode IS NULL OR mode=''");
        }
        migrateContestProblems(c);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_problem_submissions(" +
                    "id TEXT PRIMARY KEY,contest_id TEXT NOT NULL,exercise_id TEXT NOT NULL,room_id TEXT NOT NULL,user_id TEXT NOT NULL,username TEXT NOT NULL," +
                    "source_code TEXT NOT NULL,submitted_at INTEGER NOT NULL,status TEXT NOT NULL,score REAL NOT NULL DEFAULT 0,max_score REAL NOT NULL DEFAULT 100," +
                    "verdict TEXT NOT NULL DEFAULT 'QUEUED',passed_tests INTEGER NOT NULL DEFAULT 0,total_tests INTEGER NOT NULL DEFAULT 0,feedback TEXT NOT NULL DEFAULT ''," +
                    "duration_ms INTEGER NOT NULL DEFAULT 0,idempotency_key TEXT NOT NULL UNIQUE," +
                    "problem_id TEXT NOT NULL,FOREIGN KEY(contest_id,problem_id) REFERENCES p3_contest_problems(contest_id,problem_id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS p3_contest_submit_user_idx ON p3_contest_problem_submissions(contest_id,exercise_id,user_id,submitted_at)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS p3_contest_test_results(id INTEGER PRIMARY KEY AUTOINCREMENT,submission_id TEXT NOT NULL REFERENCES p3_contest_problem_submissions(id) ON DELETE CASCADE,ordinal_no INTEGER NOT NULL,hidden INTEGER NOT NULL,passed INTEGER NOT NULL,message TEXT NOT NULL,duration_ms INTEGER NOT NULL)");
        }
        try (Statement v = c.createStatement()) { v.executeUpdate("UPDATE p3_schema_meta SET version=6"); }
    }

    private static void migrateContestProblems(Connection c) throws SQLException {
        boolean hasExerciseId = false;
        try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("PRAGMA table_info(p3_contest_problems)")) {
            while (r.next()) if ("exercise_id".equalsIgnoreCase(r.getString("name"))) { hasExerciseId = true; break; }
        }
        if (hasExerciseId) return;
        boolean oldExists = false;
        try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='p3_contest_problems'")) { oldExists = r.next(); }
        if (!oldExists) return;
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("ALTER TABLE p3_contest_problems RENAME TO p3_contest_problems_legacy_v3");
            s.executeUpdate("CREATE TABLE p3_contest_problems(contest_id TEXT NOT NULL REFERENCES p3_contests(id) ON DELETE CASCADE,problem_id TEXT NOT NULL,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id),assignment_id TEXT REFERENCES p3_assignments(id),mode TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH',max_score REAL NOT NULL,title_snapshot TEXT NOT NULL DEFAULT '',statement_snapshot TEXT NOT NULL DEFAULT '',catalog_version INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(contest_id,problem_id))");
            s.executeUpdate("INSERT INTO p3_contest_problems(contest_id,problem_id,exercise_id,assignment_id,mode,max_score,title_snapshot,statement_snapshot,catalog_version) " +
                    "SELECT cp.contest_id,cp.assignment_id,a.exercise_id,cp.assignment_id,COALESCE(a.mode,e.mode,'BUILD_FROM_SCRATCH'),cp.max_score,e.title,e.statement,e.catalog_version " +
                    "FROM p3_contest_problems_legacy_v3 cp JOIN p3_assignments a ON a.id=cp.assignment_id JOIN p3_exercises e ON e.id=a.exercise_id");
            s.executeUpdate("DROP TABLE p3_contest_problems_legacy_v3");
            c.commit();
        } catch (Exception e) {
            c.rollback();
            if (e instanceof SQLException se) throw se;
            throw new SQLException("Contest problem migration failed", e);
        } finally { c.setAutoCommit(true); }
    }

    private static void ensureColumn(Connection c, String table, String column, String definition) throws SQLException {
        boolean exists = false;
        try (Statement q = c.createStatement(); ResultSet r = q.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (r.next()) if (column.equalsIgnoreCase(r.getString("name"))) { exists = true; break; }
        }
        if (!exists) try (Statement alter = c.createStatement()) {
            alter.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    public void seed(List<Exercise> exercises) throws SQLException {
        persistExercises(exercises, false);
    }

    public void createExercise(Exercise exercise) throws SQLException {
        persistExercises(List.of(exercise), true);
    }

    private void persistExercises(List<Exercise> exercises, boolean strict) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                for (Exercise e : exercises) {
                    boolean inserted;
                    String sql = (strict ? "INSERT" : "INSERT OR IGNORE") + " INTO p3_exercises(id,title,topic,difficulty,statement,input_format,output_format,constraints_text,template,hints,reference_solution,catalog_version,created_at,mode,starter_code) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement p = c.prepareStatement(sql)) {
                        int i = 1;
                        p.setString(i++, e.id()); p.setString(i++, e.title()); p.setString(i++, e.topic());
                        p.setString(i++, e.difficulty()); p.setString(i++, e.statement()); p.setString(i++, e.inputFormat());
                        p.setString(i++, e.outputFormat()); p.setString(i++, e.constraints()); p.setString(i++, e.template());
                        p.setString(i++, e.hints()); p.setString(i++, e.referenceSolution()); p.setInt(i++, 1);
                        p.setLong(i++, System.currentTimeMillis()); p.setString(i++, e.mode().name()); p.setString(i, e.starterCode()); inserted = p.executeUpdate() > 0;
                    }
                    if (inserted) {
                        try (PreparedStatement p = c.prepareStatement("INSERT INTO p3_test_cases(exercise_id,ordinal_no,input_text,expected_output,weight,hidden) VALUES(?,?,?,?,?,?)")) {
                            int n = 0;
                            for (Exercise.TestCase t : e.tests()) {
                                p.setString(1, e.id()); p.setInt(2, ++n); p.setString(3, t.input());
                                p.setString(4, t.expected()); p.setInt(5, t.weight()); p.setInt(6, t.hidden() ? 1 : 0); p.addBatch();
                            }
                            p.executeBatch();
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) throw se;
                throw new SQLException(e);
            }
        }
    }

    public List<Map<String, Object>> listExercises(String query, String topic) throws SQLException {
        return listExercises(query, topic, "");
    }

    public List<Map<String, Object>> listExercises(String query, String topic, String mode) throws SQLException {
        String sql = "SELECT id,title,topic,difficulty,statement,mode,java_structure,entry_file FROM p3_exercises WHERE id NOT IN (SELECT exercise_id FROM p3_question_bank WHERE deleted=1 OR is_current=0 OR publication<>'PUBLISHED' OR bank_scope='EXAM_PRIVATE') AND (?='' OR lower(title||' '||statement||' '||id) LIKE ?) AND (?='' OR topic=?) AND (?='' OR mode=?) ORDER BY mode,topic,id";
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(sql)) {
            String q = query == null ? "" : query.toLowerCase(Locale.ROOT); String t = topic == null ? "" : topic;
            String m = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
            p.setString(1, q); p.setString(2, "%" + q + "%"); p.setString(3, t); p.setString(4, t); p.setString(5,m); p.setString(6,m);
            try (ResultSet r = p.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (r.next()) out.add(Map.of("id", r.getString(1), "title", r.getString(2), "topic", r.getString(3), "difficulty", r.getString(4), "statement", r.getString(5), "mode", r.getString(6),"javaStructure",r.getString(7),"entryFile",r.getString(8)));
                return out;
            }
        }
    }

    public Exercise getExercise(String id) throws SQLException {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT id,title,topic,difficulty,statement,input_format,output_format,constraints_text,template,hints,reference_solution,mode,starter_code,java_structure,entry_file FROM p3_exercises WHERE id=?")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                List<Exercise.TestCase> tests = new ArrayList<>();
                try (PreparedStatement t = c.prepareStatement("SELECT input_text,expected_output,weight,hidden FROM p3_test_cases WHERE exercise_id=? ORDER BY ordinal_no")) {
                    t.setString(1, id);
                    try (ResultSet tr = t.executeQuery()) {
                        while (tr.next()) tests.add(new Exercise.TestCase(tr.getString(1), tr.getString(2), tr.getInt(3), tr.getInt(4) != 0));
                    }
                }
                return new Exercise(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8), r.getString(9), r.getString(10), r.getString(11), ExerciseMode.parse(r.getString(12)), r.getString(13), tests,r.getString(14),r.getString(15));
            }
        }
    }

    /** Lấy code mẫu DEBUG_COLLAB mới nhất đã giao cho phòng này, nếu có. */
    public String latestRoomDebugStarterCode(String roomId) throws SQLException {
        String sql = "SELECT e.starter_code FROM p3_assignments a JOIN p3_exercises e ON e.id=a.exercise_id "
                + "WHERE a.room_id=? AND a.scope='ROOM' AND a.mode='DEBUG_COLLAB' "
                + "ORDER BY a.created_at DESC LIMIT 1";
        try (Connection c=open(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1, roomId);
            try (ResultSet r=p.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    /** Mỗi dòng ghi lại một người nhận bài tại thời điểm giao bài cho phòng. */
    public record AssignmentRecipient(String userId, String username, String displayName) { }

    /** Hỗ trợ dữ liệu cũ: bản ghi chưa có danh sách người nhận vẫn dùng cách tra trước đây. */
    public AssignmentRecord createAssignment(AssignmentRecord a) throws SQLException { return createAssignment(a, null); }

    /** Tạo bài giao và lưu danh sách người nhận trong cùng giao dịch. Danh sách rỗng khác null nghĩa là cố ý không có người nhận. */
    public AssignmentRecord createAssignment(AssignmentRecord a, List<AssignmentRecipient> recipients) throws SQLException {
        String sql = "INSERT INTO p3_assignments(id,exercise_id,room_id,created_by_user_id,scope,target_user_id,target_username,created_at,deadline_at,status,mode) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setString(1,a.id()); p.setString(2,a.exerciseId()); p.setString(3,a.roomId()); p.setString(4,a.createdByUserId()); p.setString(5,a.scope()); p.setString(6,a.targetUserId()); p.setString(7,a.targetUsername()); p.setLong(8,a.createdAt()); p.setLong(9,a.deadlineAt()); p.setString(10,a.status()); p.setString(11,a.mode().name()); p.executeUpdate();
                }
                if (recipients != null) {
                    try (PreparedStatement marker = c.prepareStatement("INSERT INTO p3_assignment_recipient_snapshots(assignment_id,captured_at) VALUES(?,?)")) {
                        marker.setString(1,a.id()); marker.setLong(2,a.createdAt()); marker.executeUpdate();
                    }
                    try (PreparedStatement row = c.prepareStatement("INSERT INTO p3_assignment_recipients(assignment_id,user_id,username,display_name) VALUES(?,?,?,?)")) {
                        for (AssignmentRecipient recipient : recipients) {
                            row.setString(1,a.id()); row.setString(2,recipient.userId()); row.setString(3,recipient.username()); row.setString(4,recipient.displayName()); row.addBatch();
                        }
                        row.executeBatch();
                    }
                }
                c.commit();
                return a;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) throw se;
                throw new SQLException("Không thể lưu assignment và danh sách người nhận.", e);
            } finally { c.setAutoCommit(true); }
        }
    }

    public boolean hasAssignmentRecipientSnapshot(String assignmentId) throws SQLException {
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT 1 FROM p3_assignment_recipient_snapshots WHERE assignment_id=?")) {
            p.setString(1,assignmentId); try (ResultSet r=p.executeQuery()) { return r.next(); }
        }
    }
    public List<AssignmentRecipient> listAssignmentRecipients(String assignmentId) throws SQLException {
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT user_id,username,display_name FROM p3_assignment_recipients WHERE assignment_id=? ORDER BY username COLLATE NOCASE")) {
            p.setString(1,assignmentId); try (ResultSet r=p.executeQuery()) { List<AssignmentRecipient> out=new ArrayList<>(); while(r.next()) out.add(new AssignmentRecipient(r.getString(1),r.getString(2),r.getString(3))); return out; }
        }
    }
    private AssignmentRecord assignment(ResultSet r) throws SQLException { return new AssignmentRecord(r.getString("id"),r.getString("exercise_id"),r.getString("room_id"),r.getString("created_by_user_id"),r.getString("scope"),r.getString("target_user_id"),r.getString("target_username"),r.getLong("created_at"),r.getLong("deadline_at"),r.getString("status"),ExerciseMode.parse(r.getString("mode"))); }
    public AssignmentRecord getAssignment(String id) throws SQLException { try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_assignments WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?assignment(r):null;}} }
    public List<AssignmentRecord> listAssignments(String room,String user,boolean teacher)throws SQLException {String q=teacher?"SELECT * FROM p3_assignments WHERE room_id=? ORDER BY created_at DESC":"SELECT * FROM p3_assignments WHERE room_id=? AND (scope='ROOM' OR target_user_id=?) ORDER BY created_at DESC";try(Connection c=open();PreparedStatement p=c.prepareStatement(q)){p.setString(1,room);if(!teacher)p.setString(2,user);try(ResultSet r=p.executeQuery()){List<AssignmentRecord>out=new ArrayList<>();while(r.next())out.add(assignment(r));return out;}}}

    public SubmissionRecord byIdempotency(String key)throws SQLException {try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_submissions WHERE idempotency_key=?")){p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next()?submission(r):null;}}}
    public SubmissionRecord createSubmission(SubmissionRecord s)throws SQLException{return createSubmission(s,null);}
    public SubmissionRecord createSubmission(SubmissionRecord s,String contestId)throws SQLException {
        try(Connection c=open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_submissions(id,assignment_id,room_id,user_id,username,source_code,submitted_at,status,score,max_score,verdict,passed_tests,total_tests,feedback,duration_ms,submission_mode,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
                    int i=1;p.setString(i++,s.id());p.setString(i++,s.assignmentId());p.setString(i++,s.roomId());p.setString(i++,s.userId());p.setString(i++,s.username());p.setString(i++,s.source());p.setLong(i++,s.submittedAt());p.setString(i++,s.status());p.setDouble(i++,s.score());p.setDouble(i++,s.maxScore());p.setString(i++,s.verdict());p.setInt(i++,s.passedTests());p.setInt(i++,s.totalTests());p.setString(i++,s.feedback());p.setLong(i++,s.durationMillis());p.setString(i++,s.submissionMode());p.setString(i,s.idempotencyKey());p.executeUpdate();
                }
                if(contestId!=null&&!contestId.isBlank())try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_submissions(contest_id,submission_id) VALUES(?,?)")){p.setString(1,contestId);p.setString(2,s.id());p.executeUpdate();}
                c.commit();return s;
            } catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }
    private SubmissionRecord submission(ResultSet r)throws SQLException{return new SubmissionRecord(r.getString("id"),r.getString("assignment_id"),r.getString("room_id"),r.getString("user_id"),r.getString("username"),r.getString("source_code"),r.getLong("submitted_at"),r.getString("status"),r.getDouble("score"),r.getDouble("max_score"),r.getString("verdict"),r.getInt("passed_tests"),r.getInt("total_tests"),r.getString("feedback"),r.getLong("duration_ms"),r.getString("submission_mode"),r.getString("idempotency_key"));}
    public SubmissionRecord getSubmission(String id)throws SQLException{try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_submissions WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?submission(r):null;}}}
    public List<SubmissionRecord> listSubmissions(String assignment,String user,boolean teacher)throws SQLException{String q=teacher?"SELECT * FROM p3_submissions WHERE assignment_id=? ORDER BY submitted_at DESC":"SELECT * FROM p3_submissions WHERE assignment_id=? AND user_id=? ORDER BY submitted_at DESC";try(Connection c=open();PreparedStatement p=c.prepareStatement(q)){p.setString(1,assignment);if(!teacher)p.setString(2,user);try(ResultSet r=p.executeQuery()){List<SubmissionRecord>out=new ArrayList<>();while(r.next())out.add(submission(r));return out;}}}
    /** Chỉ trả true khi chính người dùng này đã có bài nộp chấm xong và đạt cho đúng bài được giao. */
    public boolean hasAcceptedSubmission(String assignmentId, String userId) throws SQLException {
        String sql = "SELECT 1 FROM p3_submissions WHERE assignment_id=? AND user_id=? AND status='DONE' AND upper(verdict) IN ('AC','ACCEPTED','PASS') LIMIT 1";
        try (Connection c=open(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1, assignmentId); p.setString(2, userId);
            try (ResultSet r=p.executeQuery()) { return r.next(); }
        }
    }
    /** Chỉ xét kết quả của người dùng này cho đúng câu hỏi cuộc thi, không lộ kết quả của người khác. */
    public boolean hasAcceptedContestSubmission(String contestId, String exerciseId, String userId) throws SQLException {
        String sql = "SELECT 1 FROM p3_contest_problem_submissions WHERE contest_id=? AND exercise_id=? AND user_id=? AND status='DONE' AND upper(verdict) IN ('AC','ACCEPTED','PASS') LIMIT 1";
        try (Connection c=open(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1, contestId); p.setString(2, exerciseId); p.setString(3, userId);
            try (ResultSet r=p.executeQuery()) { return r.next(); }
        }
    }
    public void markRunning(String id)throws SQLException{updateStatus(id,"RUNNING","RUNNING");}
    public List<String> recoverQueuedSubmissions() throws SQLException {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("UPDATE p3_submissions SET status='QUEUED',verdict='QUEUED',feedback='Server khởi động lại; bài được đưa lại vào hàng đợi.' WHERE status='RUNNING'")) { p.executeUpdate(); }
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("SELECT id FROM p3_submissions WHERE status='QUEUED' ORDER BY submitted_at")) {
            try (ResultSet r = p.executeQuery()) { List<String> ids = new ArrayList<>(); while (r.next()) ids.add(r.getString(1)); return ids; }
        }
    }
    private void updateStatus(String id,String status,String verdict)throws SQLException{try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_submissions SET status=?,verdict=? WHERE id=?")){p.setString(1,status);p.setString(2,verdict);p.setString(3,id);p.executeUpdate();}}
    public void saveResult(String id,GradingResult g)throws SQLException{
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement p=c.prepareStatement("UPDATE p3_submissions SET status='DONE',score=?,max_score=?,verdict=?,passed_tests=?,total_tests=?,feedback=?,duration_ms=? WHERE id=?")){p.setDouble(1,g.score());p.setDouble(2,g.maxScore());p.setString(3,g.verdict());p.setInt(4,g.passed());p.setInt(5,g.total());p.setString(6,g.feedback());p.setLong(7,g.durationMillis());p.setString(8,id);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("DELETE FROM p3_test_results WHERE submission_id=?")){p.setString(1,id);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_test_results(submission_id,ordinal_no,hidden,passed,message,duration_ms) VALUES(?,?,?,?,?,?)")){int n=0;for(Map<String,Object>t:g.publicTests()){p.setString(1,id);p.setInt(2,++n);p.setInt(3,Boolean.TRUE.equals(t.get("hidden"))?1:0);p.setInt(4,Boolean.TRUE.equals(t.get("passed"))?1:0);p.setString(5,String.valueOf(t.getOrDefault("message","")));p.setLong(6,((Number)t.getOrDefault("durationMs",0)).longValue());p.addBatch();}p.executeBatch();}
                StructureResultStore.write(c,id,g.structureResults());c.commit();
            }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }
    public List<Map<String,Object>> structureResults(String id)throws SQLException{try(Connection c=open()){return StructureResultStore.read(c,id);}}
    public List<Map<String,Object>> publicTestResults(String submissionId)throws SQLException{try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT ordinal_no,hidden,passed,message,duration_ms FROM p3_test_results WHERE submission_id=? ORDER BY ordinal_no")){p.setString(1,submissionId);try(ResultSet r=p.executeQuery()){List<Map<String,Object>>out=new ArrayList<>();while(r.next()){boolean hidden=r.getInt(2)!=0;Map<String,Object>m=new LinkedHashMap<>();m.put("test",r.getInt(1));m.put("passed",r.getInt(3)!=0);m.put("message",hidden?"Hidden test: "+(r.getInt(3)!=0?"đạt":"không đạt"):r.getString(4));m.put("durationMs",r.getLong(5));m.put("hidden",hidden);out.add(m);}return out;}}}
    public TeacherTrialRecord teacherTrialByIdempotency(String key) throws SQLException {
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT * FROM p3_teacher_trials WHERE idempotency_key=?")) {
            p.setString(1,key); try(ResultSet r=p.executeQuery()){return r.next()?teacherTrial(r):null;}
        }
    }
    public TeacherTrialRecord getTeacherTrial(String id) throws SQLException {
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT * FROM p3_teacher_trials WHERE id=?")) {
            p.setString(1,id); try(ResultSet r=p.executeQuery()){return r.next()?teacherTrial(r):null;}
        }
    }
    public List<TeacherTrialRecord> listTeacherTrials(String room,String exercise,String teacher) throws SQLException {
        String sql="SELECT * FROM p3_teacher_trials WHERE room_id=? AND teacher_user_id=?" + (exercise==null||exercise.isBlank()?"":" AND exercise_id=?") + " ORDER BY submitted_at DESC LIMIT 50";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,room);p.setString(2,teacher);if(exercise!=null&&!exercise.isBlank())p.setString(3,exercise);
            try(ResultSet r=p.executeQuery()){List<TeacherTrialRecord> out=new ArrayList<>();while(r.next())out.add(teacherTrial(r));return out;}
        }
    }
    public TeacherTrialRecord createTeacherTrial(TeacherTrialRecord t) throws SQLException {
        String sql="INSERT INTO p3_teacher_trials(id,exercise_id,room_id,teacher_user_id,teacher_username,source_code,submitted_at,status,score,max_score,verdict,passed_tests,total_tests,feedback,duration_ms,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){
            int i=1;p.setString(i++,t.id());p.setString(i++,t.exerciseId());p.setString(i++,t.roomId());p.setString(i++,t.teacherUserId());p.setString(i++,t.teacherUsername());p.setString(i++,t.source());p.setLong(i++,t.submittedAt());p.setString(i++,t.status());p.setDouble(i++,t.score());p.setDouble(i++,t.maxScore());p.setString(i++,t.verdict());p.setInt(i++,t.passedTests());p.setInt(i++,t.totalTests());p.setString(i++,t.feedback());p.setLong(i++,t.durationMillis());p.setString(i,t.idempotencyKey());p.executeUpdate();return t;
        }
    }
    public void markTeacherTrialRunning(String id) throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_teacher_trials SET status='RUNNING',verdict='RUNNING',feedback='Đang chạy bộ test trong sandbox.' WHERE id=?")){p.setString(1,id);p.executeUpdate();}
    }
    public List<String> recoverQueuedTeacherTrials() throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_teacher_trials SET status='QUEUED',verdict='QUEUED',feedback='Server khởi động lại; lượt thử được đưa lại vào hàng đợi.' WHERE status='RUNNING'")){p.executeUpdate();}
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT id FROM p3_teacher_trials WHERE status='QUEUED' ORDER BY submitted_at")){try(ResultSet r=p.executeQuery()){List<String> ids=new ArrayList<>();while(r.next())ids.add(r.getString(1));return ids;}}
    }
    public void saveTeacherTrialResult(String id,GradingResult g) throws SQLException {
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement p=c.prepareStatement("UPDATE p3_teacher_trials SET status='DONE',score=?,max_score=?,verdict=?,passed_tests=?,total_tests=?,feedback=?,duration_ms=? WHERE id=?")){
                    p.setDouble(1,g.score());p.setDouble(2,g.maxScore());p.setString(3,g.verdict());p.setInt(4,g.passed());p.setInt(5,g.total());p.setString(6,g.feedback());p.setLong(7,g.durationMillis());p.setString(8,id);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM p3_teacher_trial_test_results WHERE trial_id=?")){p.setString(1,id);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_teacher_trial_test_results(trial_id,ordinal_no,hidden,passed,message,duration_ms) VALUES(?,?,?,?,?,?)")){
                    int n=0;for(Map<String,Object> t:g.publicTests()){p.setString(1,id);p.setInt(2,++n);p.setInt(3,Boolean.TRUE.equals(t.get("hidden"))?1:0);p.setInt(4,Boolean.TRUE.equals(t.get("passed"))?1:0);p.setString(5,String.valueOf(t.getOrDefault("message","")));p.setLong(6,((Number)t.getOrDefault("durationMs",0)).longValue());p.addBatch();}p.executeBatch();
                }
                StructureResultStore.write(c,id,g.structureResults());c.commit();
            }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }
    public List<Map<String,Object>> teacherTrialTestResults(String id) throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT ordinal_no,hidden,passed,message,duration_ms FROM p3_teacher_trial_test_results WHERE trial_id=? ORDER BY ordinal_no")){
            p.setString(1,id);try(ResultSet r=p.executeQuery()){List<Map<String,Object>> out=new ArrayList<>();while(r.next()){boolean hidden=r.getInt(2)!=0;Map<String,Object> m=new LinkedHashMap<>();m.put("test",r.getInt(1));m.put("passed",r.getInt(3)!=0);m.put("message",hidden?"Hidden test: "+(r.getInt(3)!=0?"đạt":"không đạt"):r.getString(4));m.put("durationMs",r.getLong(5));m.put("hidden",hidden);out.add(m);}return out;}
        }
    }
    private static TeacherTrialRecord teacherTrial(ResultSet r) throws SQLException {
        return new TeacherTrialRecord(r.getString("id"),r.getString("exercise_id"),r.getString("room_id"),r.getString("teacher_user_id"),r.getString("teacher_username"),r.getString("source_code"),r.getLong("submitted_at"),r.getString("status"),r.getDouble("score"),r.getDouble("max_score"),r.getString("verdict"),r.getInt("passed_tests"),r.getInt("total_tests"),r.getString("feedback"),r.getLong("duration_ms"),r.getString("idempotency_key"));
    }

    public SharedCheckRun sharedCheckByRequestKey(String key) throws SQLException {
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT * FROM p3_shared_check_runs WHERE request_key=?")) {
            p.setString(1,key); try(ResultSet r=p.executeQuery()){return r.next()?sharedCheck(r):null;}
        }
    }
    public SharedCheckRun getSharedCheck(String id) throws SQLException {
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT * FROM p3_shared_check_runs WHERE id=?")) {
            p.setString(1,id); try(ResultSet r=p.executeQuery()){return r.next()?sharedCheck(r):null;}
        }
    }
    public SharedCheckRun latestSharedCheck(String assignmentId,String roomId) throws SQLException {
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT * FROM p3_shared_check_runs WHERE assignment_id=? AND room_id=? ORDER BY created_at DESC LIMIT 1")) {
            p.setString(1,assignmentId); p.setString(2,roomId); try(ResultSet r=p.executeQuery()){return r.next()?sharedCheck(r):null;}
        }
    }
    public void createSharedCheck(SharedCheckRun r) throws SQLException {
        String sql="INSERT INTO p3_shared_check_runs(id,request_key,assignment_id,room_id,checked_version,source_hash,source_code,requested_by_user_id,requested_by_username,created_at,status,verdict,score,max_score,passed_tests,total_tests,feedback,duration_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){int i=1;p.setString(i++,r.id());p.setString(i++,r.requestKey());p.setString(i++,r.assignmentId());p.setString(i++,r.roomId());p.setLong(i++,r.checkedVersion());p.setString(i++,r.sourceHash());p.setString(i++,r.source());p.setString(i++,r.requestedByUserId());p.setString(i++,r.requestedByUsername());p.setLong(i++,r.createdAt());p.setString(i++,r.status());p.setString(i++,r.verdict());p.setDouble(i++,r.score());p.setDouble(i++,r.maxScore());p.setInt(i++,r.passedTests());p.setInt(i++,r.totalTests());p.setString(i++,r.feedback());p.setLong(i,r.durationMillis());p.executeUpdate();}
    }
    public List<String> recoverQueuedSharedChecks() throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_shared_check_runs SET status='QUEUED',verdict='QUEUED',feedback='Server khởi động lại; lượt kiểm tra được đưa lại vào hàng đợi.' WHERE status='RUNNING'")){p.executeUpdate();}
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT id FROM p3_shared_check_runs WHERE status='QUEUED' ORDER BY created_at")){try(ResultSet r=p.executeQuery()){List<String> ids=new ArrayList<>();while(r.next())ids.add(r.getString(1));return ids;}}
    }
    public void markSharedCheckRunning(String id) throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_shared_check_runs SET status='RUNNING',verdict='RUNNING',feedback='Đang chạy bộ test trong sandbox.' WHERE id=?")){p.setString(1,id);p.executeUpdate();}
    }
    public void saveSharedCheckResult(String id,String status,String verdict,double score,double maxScore,int passed,int total,String feedback,long duration) throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_shared_check_runs SET status=?,verdict=?,score=?,max_score=?,passed_tests=?,total_tests=?,feedback=?,duration_ms=? WHERE id=?")) {p.setString(1,status);p.setString(2,verdict);p.setDouble(3,score);p.setDouble(4,maxScore);p.setInt(5,passed);p.setInt(6,total);p.setString(7,feedback==null?"":feedback);p.setLong(8,duration);p.setString(9,id);p.executeUpdate();}
    }
    private static SharedCheckRun sharedCheck(ResultSet r) throws SQLException {
        return new SharedCheckRun(r.getString("id"),r.getString("request_key"),r.getString("assignment_id"),r.getString("room_id"),r.getLong("checked_version"),r.getString("source_hash"),r.getString("source_code"),r.getString("requested_by_user_id"),r.getString("requested_by_username"),r.getLong("created_at"),r.getString("status"),r.getString("verdict"),r.getDouble("score"),r.getDouble("max_score"),r.getInt("passed_tests"),r.getInt("total_tests"),r.getString("feedback"),r.getLong("duration_ms"));
    }

    /** Hỗ trợ client cũ với kiểu cuộc thi lấy câu hỏi từ bài đã giao. */
    public String createContest(String id,String room,String title,String owner,long now,long start,long end,List<String> assignmentIds)throws SQLException{
        List<ContestProblemSeed> problems = new ArrayList<>();
        for (String assignmentId : assignmentIds) {
            AssignmentRecord assignment = getAssignment(assignmentId);
            if (assignment == null || !room.equals(assignment.roomId())) throw new SQLException("Bài giao không thuộc phòng: " + assignmentId);
            Exercise exercise = getExercise(assignment.exerciseId());
            if (exercise == null) throw new SQLException("Không tìm thấy bài trong ngân hàng: " + assignment.exerciseId());
            problems.add(new ContestProblemSeed(assignmentId, exercise.id(), assignmentId,
                    assignment.mode().name(), 100, exercise.title(), exercise.statement(), 1));
        }
        return createContestRows(id, room, title, owner, now, start, end, "INDIVIDUAL",
                "BEST_SCORE_EARLIEST_ACHIEVED_V1", problems);
    }

    /** Tạo cuộc thi trực tiếp từ ID trong ngân hàng đề, không cần giao bài trước. */
    public String createContestFromExercises(String id,String room,String title,String owner,long now,long start,long end,
                                             String contestType,List<String> exerciseIds)throws SQLException{
        return createContestFromExercises(id,room,title,owner,now,start,end,contestType,exerciseIds,"ALL",0);
    }
    public String createContestFromExercises(String id,String room,String title,String owner,long now,long start,long end,String contestType,List<String> exerciseIds,String distribution,int count)throws SQLException{
        if(!Set.of("ALL","RANDOM").contains(distribution)||("RANDOM".equals(distribution)&&(count<1||count>exerciseIds.size())))throw new SQLException("Invalid distribution policy");
        List<ContestProblemSeed> problems = new ArrayList<>();
        for (String exerciseId : exerciseIds) {
            Exercise exercise = getExercise(exerciseId);
            if (exercise == null) throw new SQLException("Không tìm thấy bài trong ngân hàng: " + exerciseId);
            problems.add(new ContestProblemSeed(exercise.id(), exercise.id(), null, exercise.mode().name(),
                    100, exercise.title(), exercise.statement(), 1));
        }
        return createContestRows(id, room, title, owner, now, start, end, contestType,
                "AC_COUNT_SCORE_TIME_V2", problems,distribution,count);
    }

    private String createContestRows(String id,String room,String title,String owner,long now,long start,long end,
                                     String contestType,String scoringRule,List<ContestProblemSeed> problems)throws SQLException{
        return createContestRows(id,room,title,owner,now,start,end,contestType,scoringRule,problems,"ALL",0);
    }
    private String createContestRows(String id,String room,String title,String owner,long now,long start,long end,String contestType,String scoringRule,List<ContestProblemSeed> problems,String distribution,int count)throws SQLException{
        return createContestRows(id,room,title,owner,now,start,end,contestType,scoringRule,problems,distribution,count,"",Map.of());
    }
    public String createContestFromExam(String id,String room,String title,String owner,long now,long start,long end,Map<String,Object> exam,String distribution,int count)throws SQLException{
        List<ContestProblemSeed> problems=new ArrayList<>();
        for(Object value:AssignmentJson.list(exam.get("items"))){Map<String,Object> item=AssignmentJson.map(value);Exercise e=getExercise(AssignmentJson.text(item,"exerciseId",""));if(e==null)throw new SQLException("Missing frozen question");problems.add(new ContestProblemSeed(e.id(),e.id(),null,e.mode().name(),ExamScoring.questionMax(exam,item,"RANDOM".equals(distribution)?count:AssignmentJson.list(exam.get("items")).size()),e.title(),e.statement(),1));}
        return createContestRows(id,room,title,owner,now,start,end,"INDIVIDUAL",ExamScoring.equalTen(exam)?ExamScoring.RULE:"AC_COUNT_SCORE_TIME_V2",problems,distribution,count,AssignmentJson.text(exam,"id",""),AssignmentJson.map(exam.get("grading")));
    }
    private String createContestRows(String id,String room,String title,String owner,long now,long start,long end,String contestType,String scoringRule,List<ContestProblemSeed> problems,String distribution,int count,String examVersion,Map<String,Object> grading)throws SQLException{
        if (problems.isEmpty()) throw new SQLException("Kỳ thi phải có ít nhất một bài.");
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contests(id,room_id,title,created_by_user_id,created_at,start_at,end_at,status,scoring_rule_version,contest_type) VALUES(?,?,?,?,?,?,?,?,?,?)")){
                    p.setString(1,id);p.setString(2,room);p.setString(3,title);p.setString(4,owner);p.setLong(5,now);p.setLong(6,start);p.setLong(7,end);
                    p.setString(8,now>=start?"RUNNING":"SCHEDULED");p.setString(9,scoringRule);p.setString(10,contestType);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_problems(contest_id,problem_id,exercise_id,assignment_id,mode,max_score,title_snapshot,statement_snapshot,catalog_version) VALUES(?,?,?,?,?,?,?,?,?)")){
                    for(ContestProblemSeed problem:problems){
                        p.setString(1,id);p.setString(2,problem.problemId());p.setString(3,problem.exerciseId());
                        if(problem.assignmentId()==null)p.setNull(4,Types.VARCHAR);else p.setString(4,problem.assignmentId());
                        p.setString(5,problem.mode());p.setDouble(6,problem.maxScore());p.setString(7,problem.title());
                        p.setString(8,problem.statement());p.setInt(9,problem.catalogVersion());p.addBatch();
                    }
                    p.executeBatch();
                }
                ContestDistributionRepository.policy(c,id,distribution,count);
                if(!examVersion.isBlank())try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_exam_contests VALUES(?,?,?)")){p.setString(1,id);p.setString(2,examVersion);p.setString(3,AssignmentJson.stringify(grading));p.executeUpdate();}
                c.commit();return id;
            }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }
    private record ContestProblemSeed(String problemId,String exerciseId,String assignmentId,String mode,double maxScore,
                                      String title,String statement,int catalogVersion){}

    public List<Map<String,Object>> listContests(String room)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT id,title,start_at,end_at,status,scoring_rule_version,contest_type,(SELECT COUNT(*) FROM p3_contest_problems cp WHERE cp.contest_id=p3_contests.id) FROM p3_contests WHERE room_id=? ORDER BY created_at DESC")){
            p.setString(1,room);try(ResultSet r=p.executeQuery()){List<Map<String,Object>>out=new ArrayList<>();while(r.next()){
                long now=System.currentTimeMillis();String st=now>=r.getLong(4)?"CLOSED":now>=r.getLong(3)?"RUNNING":"SCHEDULED";
                Map<String,Object> item=new LinkedHashMap<>(Map.of("id",r.getString(1),"title",r.getString(2),"startAt",r.getLong(3),"endAt",r.getLong(4),"status",st,
                        "scoringRule",r.getString(6),"contestType",r.getString(7),"problemCount",r.getInt(8)));item.putAll(ContestDistributionRepository.config(c,r.getString(1)));out.add(item);
            }return out;}
        }
    }
    public Map<String,Object> contest(String id)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT id,room_id,title,created_by_user_id,start_at,end_at,status,scoring_rule_version,contest_type FROM p3_contests WHERE id=?")){
            p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())return null;long now=System.currentTimeMillis(),start=r.getLong(5),end=r.getLong(6);
                Map<String,Object> result=new LinkedHashMap<>(Map.of("id",r.getString(1),"roomId",r.getString(2),"title",r.getString(3),"owner",r.getString(4),"startAt",start,"endAt",end,
                        "status",now<start?"SCHEDULED":now>=end?"CLOSED":"RUNNING","scoringRule",r.getString(8),"contestType",r.getString(9)));result.putAll(ContestDistributionRepository.config(c,id));return result;}
        }
    }
    /** Chỉ trả ID bài giao cho bài nộp cuộc thi cũ còn gắn với bài đã giao. */
    public List<String> contestAssignments(String contestId)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT assignment_id FROM p3_contest_problems WHERE contest_id=? AND assignment_id IS NOT NULL ORDER BY rowid")){
            p.setString(1,contestId);try(ResultSet r=p.executeQuery()){List<String>out=new ArrayList<>();while(r.next())out.add(r.getString(1));return out;}
        }
    }
    public List<String> contestExerciseIds(String contestId)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT exercise_id FROM p3_contest_problems WHERE contest_id=? ORDER BY rowid")){
            p.setString(1,contestId);try(ResultSet r=p.executeQuery()){List<String>out=new ArrayList<>();while(r.next())out.add(r.getString(1));return out;}
        }
    }
    public String contestProblemId(String contestId,String exerciseId)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT problem_id FROM p3_contest_problems WHERE contest_id=? AND exercise_id=? ORDER BY CASE WHEN assignment_id IS NULL THEN 0 ELSE 1 END, rowid LIMIT 1")){
            p.setString(1,contestId);p.setString(2,exerciseId);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):null;}
        }
    }
    public boolean contestHasExercise(String contestId,String exerciseId)throws SQLException{return contestProblemId(contestId,exerciseId)!=null;}
    public double contestMaxScore(String contest,String exercise)throws SQLException{try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT max_score FROM p3_contest_problems WHERE contest_id=? AND exercise_id=?")){p.setString(1,contest);p.setString(2,exercise);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Missing contest problem");return r.getDouble(1);}}}

    public ContestSubmissionRecord contestSubmissionByIdempotency(String key)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_contest_problem_submissions WHERE idempotency_key=?")){
            p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next()?contestSubmission(r):null;}
        }
    }
    public ContestSubmissionRecord getContestSubmission(String id)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM p3_contest_problem_submissions WHERE id=?")){
            p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?contestSubmission(r):null;}
        }
    }
    private ContestSubmissionRecord contestSubmission(ResultSet r)throws SQLException{
        return new ContestSubmissionRecord(r.getString("id"),r.getString("contest_id"),r.getString("exercise_id"),r.getString("room_id"),
                r.getString("user_id"),r.getString("username"),r.getString("source_code"),r.getLong("submitted_at"),r.getString("status"),
                r.getDouble("score"),r.getDouble("max_score"),r.getString("verdict"),r.getInt("passed_tests"),r.getInt("total_tests"),
                r.getString("feedback"),r.getLong("duration_ms"),r.getString("idempotency_key"));
    }
    public ContestSubmissionRecord createContestSubmission(ContestSubmissionRecord submission,String problemId)throws SQLException{
        String sql="INSERT INTO p3_contest_problem_submissions(id,contest_id,exercise_id,room_id,user_id,username,source_code,submitted_at,status,score,max_score,verdict,passed_tests,total_tests,feedback,duration_ms,idempotency_key,problem_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=open();Statement tx=c.createStatement();PreparedStatement p=c.prepareStatement(sql)){
            tx.execute("BEGIN IMMEDIATE");
            try {
            ExamOrganizationRepository.beforeSubmission(c,submission);
            int i=1;p.setString(i++,submission.id());p.setString(i++,submission.contestId());p.setString(i++,submission.exerciseId());p.setString(i++,submission.roomId());
            p.setString(i++,submission.userId());p.setString(i++,submission.username());p.setString(i++,submission.source());p.setLong(i++,submission.submittedAt());
            p.setString(i++,submission.status());p.setDouble(i++,submission.score());p.setDouble(i++,submission.maxScore());p.setString(i++,submission.verdict());
            p.setInt(i++,submission.passedTests());p.setInt(i++,submission.totalTests());p.setString(i++,submission.feedback());p.setLong(i++,submission.durationMillis());
            p.setString(i++,submission.idempotencyKey());p.setString(i,problemId);p.executeUpdate();tx.execute("COMMIT");return submission;
            } catch(SQLException|RuntimeException e){try{tx.execute("ROLLBACK");}catch(SQLException ignored){}throw e;}
        }
    }
    public List<String> recoverQueuedContestSubmissions()throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_contest_problem_submissions SET status='QUEUED',verdict='QUEUED',feedback='Server khởi động lại; bài thi được đưa lại vào hàng đợi.' WHERE status='RUNNING'")){p.executeUpdate();}
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT id FROM p3_contest_problem_submissions WHERE status='QUEUED' ORDER BY submitted_at")){
            try(ResultSet r=p.executeQuery()){List<String>ids=new ArrayList<>();while(r.next())ids.add(r.getString(1));return ids;}
        }
    }
    public void markContestSubmissionRunning(String id)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE p3_contest_problem_submissions SET status='RUNNING',verdict='RUNNING',feedback='Đang chấm bài thi.' WHERE id=?")){p.setString(1,id);p.executeUpdate();}
    }
    public void saveContestSubmissionResult(String id,GradingResult g)throws SQLException{
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement p=c.prepareStatement("UPDATE p3_contest_problem_submissions SET status='DONE',score=?,max_score=?,verdict=?,passed_tests=?,total_tests=?,feedback=?,duration_ms=? WHERE id=?")){
                    p.setDouble(1,g.score());p.setDouble(2,g.maxScore());p.setString(3,g.verdict());p.setInt(4,g.passed());p.setInt(5,g.total());p.setString(6,g.feedback());p.setLong(7,g.durationMillis());p.setString(8,id);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM p3_contest_test_results WHERE submission_id=?")){p.setString(1,id);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_contest_test_results(submission_id,ordinal_no,hidden,passed,message,duration_ms) VALUES(?,?,?,?,?,?)")){
                    int n=0;for(Map<String,Object>t:g.publicTests()){p.setString(1,id);p.setInt(2,++n);p.setInt(3,Boolean.TRUE.equals(t.get("hidden"))?1:0);p.setInt(4,Boolean.TRUE.equals(t.get("passed"))?1:0);p.setString(5,String.valueOf(t.getOrDefault("message","")));p.setLong(6,((Number)t.getOrDefault("durationMs",0)).longValue());p.addBatch();}p.executeBatch();
                }
                StructureResultStore.write(c,id,g.structureResults());c.commit();
            }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }
    public List<Map<String,Object>> contestSubmissionTestResults(String id)throws SQLException{
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT ordinal_no,hidden,passed,message,duration_ms FROM p3_contest_test_results WHERE submission_id=? ORDER BY ordinal_no")){
            p.setString(1,id);try(ResultSet r=p.executeQuery()){List<Map<String,Object>>out=new ArrayList<>();while(r.next()){boolean hidden=r.getInt(2)!=0;out.add(Map.of("test",r.getInt(1),"passed",r.getInt(3)!=0,
                    "message",hidden?"Hidden test: "+(r.getInt(3)!=0?"đạt":"không đạt"):r.getString(4),"durationMs",r.getLong(5),"hidden",hidden));}return out;}
        }
    }
    public List<ContestSubmissionRecord> listContestSubmissions(String contestId,String exerciseId,String userId,boolean teacher)throws SQLException{
        String sql=teacher?"SELECT * FROM p3_contest_problem_submissions WHERE contest_id=? AND exercise_id=? ORDER BY submitted_at DESC":
                "SELECT * FROM p3_contest_problem_submissions WHERE contest_id=? AND exercise_id=? AND user_id=? ORDER BY submitted_at DESC";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,contestId);p.setString(2,exerciseId);if(!teacher)p.setString(3,userId);
            try(ResultSet r=p.executeQuery()){List<ContestSubmissionRecord>out=new ArrayList<>();while(r.next())out.add(contestSubmission(r));return out;}
        }
    }

    public List<Map<String,Object>> leaderboard(String contestId)throws SQLException{
        Map<String,Object> contest = contest(contestId);
        if(contest==null)throw new SQLException("Không tìm thấy kỳ thi");
        String rule=String.valueOf(contest.get("scoringRule"));
        if("BEST_SCORE_EARLIEST_ACHIEVED_V1".equals(rule)) return legacyLeaderboard(contestId);
        Map<String,Map<String,Object>> userRows=new HashMap<>();
        Map<String,Map<String,Double>> bestScores=new HashMap<>();
        Map<String,Map<String,Long>> earliestAcs=new HashMap<>();
        String sql="SELECT user_id,username,exercise_id,score,verdict,submitted_at FROM p3_contest_problem_submissions WHERE contest_id=? AND status='DONE' ORDER BY submitted_at ASC";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,contestId);try(ResultSet r=p.executeQuery()){while(r.next()){
            String user=r.getString(1),name=r.getString(2),exercise=r.getString(3),verdict=r.getString(5);double score=r.getDouble(4);long submittedAt=r.getLong(6);
            userRows.put(user,AssignmentJson.mapOf("userId",user,"username",name));
            bestScores.computeIfAbsent(user,k->new HashMap<>()).merge(exercise,score,Math::max);
            if("ACCEPTED".equalsIgnoreCase(verdict)||"AC".equalsIgnoreCase(verdict)||"PASS".equalsIgnoreCase(verdict)){
                earliestAcs.computeIfAbsent(user,k->new HashMap<>()).merge(exercise,submittedAt,Math::min);
            }
        }}}
        List<Map<String,Object>> out=new ArrayList<>();
        for(String user:userRows.keySet()){
            double totalScore=bestScores.getOrDefault(user,Map.of()).values().stream().mapToDouble(Double::doubleValue).sum();if(ExamScoring.RULE.equals(rule))totalScore=ExamScoring.result(totalScore);
            Map<String,Long> acTimes=earliestAcs.getOrDefault(user,Map.of());
            long lastAc=acTimes.values().stream().mapToLong(Long::longValue).max().orElse(Long.MAX_VALUE);
            Map<String,Object> row=new LinkedHashMap<>(userRows.get(user));row.put("score",totalScore);row.put("acceptedCount",acTimes.size());row.put("tieBreakAt",lastAc);out.add(row);
        }
        out.sort((a,b)->{
            int n=Integer.compare(((Number)b.get("acceptedCount")).intValue(),((Number)a.get("acceptedCount")).intValue());if(n!=0)return n;
            n=Double.compare(((Number)b.get("score")).doubleValue(),((Number)a.get("score")).doubleValue());if(n!=0)return n;
            n=Long.compare(((Number)a.get("tieBreakAt")).longValue(),((Number)b.get("tieBreakAt")).longValue());if(n!=0)return n;
            return String.valueOf(a.get("username")).compareToIgnoreCase(String.valueOf(b.get("username")));
        });
        for(int i=0;i<out.size();i++){Map<String,Object> m=new LinkedHashMap<>(out.get(i));m.put("rank",i+1);out.set(i,m);}return out;
    }
    /** Giữ cách tính bảng xếp hạng v1 cho những cuộc thi đã tạo trước đây. */
    private List<Map<String,Object>> legacyLeaderboard(String contestId)throws SQLException{
        Map<String,Double> scores=new HashMap<>();Map<String,String> names=new HashMap<>();Map<String,Long> achieved=new HashMap<>();
        String sql="SELECT s.user_id,s.username,s.assignment_id,s.score,s.submitted_at FROM p3_contest_submissions cs JOIN p3_submissions s ON s.id=cs.submission_id WHERE cs.contest_id=? AND s.status='DONE' ORDER BY s.user_id,s.assignment_id,s.score DESC,s.submitted_at ASC";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,contestId);try(ResultSet r=p.executeQuery()){Map<String,Set<String>>seen=new HashMap<>();while(r.next()){String user=r.getString(1),aid=r.getString(3);if(!seen.computeIfAbsent(user,k->new HashSet<>()).add(aid))continue;scores.merge(user,r.getDouble(4),Double::sum);names.put(user,r.getString(2));achieved.merge(user,r.getLong(5),Math::max);}}}
        List<Map<String,Object>>out=new ArrayList<>();for(String u:scores.keySet())out.add(Map.of("userId",u,"username",names.getOrDefault(u,u),"score",scores.get(u),"tieBreakAt",achieved.getOrDefault(u,Long.MAX_VALUE)));
        out.sort((a,b)->{int z=Double.compare(((Number)b.get("score")).doubleValue(),((Number)a.get("score")).doubleValue());if(z!=0)return z;return Long.compare(((Number)a.get("tieBreakAt")).longValue(),((Number)b.get("tieBreakAt")).longValue());});
        for(int i=0;i<out.size();i++){Map<String,Object> m=new LinkedHashMap<>(out.get(i));m.put("rank",i+1);out.set(i,m);}return out;
    }

}
