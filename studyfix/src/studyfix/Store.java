package studyfix;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One serialized JDBC connection. RAM is updated only after commit returns. */
final class Store implements AutoCloseable {
    record StoredRoom(String id, String owner, String code, long version, List<CodeRevision> history) { }
    private static final Logger LOG = Logger.getLogger(Store.class.getName());
    private final String databaseUrl;
    private Connection db;
    private SQLException unavailable;
    Store(Path path) throws SQLException, IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        databaseUrl = "jdbc:sqlite:" + path.toAbsolutePath();
        db = openConnection();
        try {
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS users (user_id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('TEACHER','STUDENT')))");
                s.execute("CREATE TABLE IF NOT EXISTS rooms (room_id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id TEXT NOT NULL REFERENCES users(user_id), code TEXT NOT NULL, version INTEGER NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS revisions (room_id INTEGER NOT NULL REFERENCES rooms(room_id), version INTEGER NOT NULL, editor_id TEXT NOT NULL, editor_name TEXT NOT NULL, created_at TEXT NOT NULL, code TEXT NOT NULL, PRIMARY KEY(room_id,version))");
            }
            migrateHistory();
            ensurePerson3Schema();
            seedCatalog();
            seed("teacher", "Teacher123!", "Giáo viên demo", Identity.Role.TEACHER);
            seed("teacher2", "Teacher234!", "Giáo viên 2", Identity.Role.TEACHER);
            seed("student1", "Student123!", "Học sinh 1", Identity.Role.STUDENT);
            seed("student2", "Student234!", "Học sinh 2", Identity.Role.STUDENT);
        } catch (SQLException | RuntimeException e) { db.close(); throw e; }
    }
    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
            return connection;
        } catch (SQLException e) {
            try { connection.close(); } catch (SQLException closeFailure) { e.addSuppressed(closeFailure); }
            throw e;
        }
    }
    @FunctionalInterface private interface Transaction<T> { T execute() throws SQLException; }

    /** Cleanup must never turn an already committed write into a reported failure. */
    private <T> T transaction(Transaction<T> work) throws SQLException {
        requireAvailable();
        db.setAutoCommit(false);
        T result;
        try {
            result = work.execute();
            db.commit();
        } catch (SQLException | RuntimeException failure) {
            try { db.rollback(); }
            catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                // Never enable autocommit after failed rollback: it could commit partial work.
                replaceConnection(failure);
                throw failure;
            }
            restoreAutoCommit(failure);
            throw failure;
        }
        restoreAutoCommit(null);
        return result;
    }
    private void restoreAutoCommit(Throwable originalFailure) {
        try { db.setAutoCommit(true); }
        catch (SQLException cleanupFailure) {
            if (originalFailure != null) originalFailure.addSuppressed(cleanupFailure);
            replaceConnection(cleanupFailure);
        }
    }
    private void replaceConnection(Throwable failure) {
        unavailable = new SQLException("Database connection unavailable after JDBC cleanup failure", failure);
        try { db.close(); }
        catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
        try { db = openConnection(); unavailable = null; }
        catch (SQLException reopenFailure) {
            failure.addSuppressed(reopenFailure);
            // The closed connection rejects subsequent operations; committed RAM can still publish.
        }
        LOG.log(Level.WARNING, "JDBC cleanup failed; replaced connection if available. Committed writes remain successful.", failure);
    }
    private void requireAvailable() throws SQLException {
        if (unavailable != null) throw new SQLException("Database connection unavailable; restart server to retry", unavailable);
    }
    /** Nullable columns are the migration marker; DDL and backfill commit together. */
    private void migrateHistory() throws SQLException {
        transaction(() -> {
            Set<String> columns = new HashSet<>();
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(revisions)")) {
                while (r.next()) columns.add(r.getString("name"));
            }
            try (Statement s = db.createStatement()) {
                if (!columns.contains("previous_version")) s.execute("ALTER TABLE revisions ADD COLUMN previous_version INTEGER");
                if (!columns.contains("changes")) s.execute("ALTER TABLE revisions ADD COLUMN changes TEXT");
            }
            // Stream the existing snapshots; never infer authors from names or room owners.
            try (Statement s = db.createStatement();
                 ResultSet r = s.executeQuery("SELECT * FROM revisions ORDER BY room_id,version");
                 PreparedStatement u = db.prepareStatement("UPDATE revisions SET previous_version=?,changes=? WHERE room_id=? AND version=?")) {
                String lastRoom = null, previousCode = null;
                long lastVersion = -1;
                while (r.next()) {
                    String room = r.getString("room_id"), code = r.getString("code");
                    long version = r.getLong("version");
                    if (r.getString("changes") == null) {
                        String diff = version == 0 ? CodeDiff.INITIAL
                                : room.equals(lastRoom) && lastVersion == version - 1
                                ? CodeDiff.between(previousCode, code) : CodeDiff.MISSING;
                        if (version == 0) u.setNull(1, Types.BIGINT); else u.setLong(1, version - 1);
                        u.setString(2, diff); u.setString(3, room); u.setLong(4, version); u.executeUpdate();
                    }
                    lastRoom = room; lastVersion = version; previousCode = code;
                }
            }
            return null;
        });
    }
    private void seed(String username, String password, String name, Identity.Role role) throws SQLException {
        try (PreparedStatement q = db.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
            q.setString(1, username); try (ResultSet r = q.executeQuery()) { if (r.next()) return; }
        }
        char[] secret = password.toCharArray();
        try (PreparedStatement s = db.prepareStatement("INSERT INTO users VALUES(?,?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, username);
            s.setString(3, Passwords.hash(secret)); s.setString(4, name); s.setString(5, role.name()); s.executeUpdate();
        } finally { Arrays.fill(secret, '\0'); }
    }
    synchronized Identity login(String username, char[] password) throws SQLException {
        try {
            requireAvailable();
            try (PreparedStatement s = db.prepareStatement("SELECT * FROM users WHERE username=?")) {
                s.setString(1, username);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next() || !Passwords.verify(password, r.getString("password_hash"))) return null;
                    return new Identity(r.getString("user_id"), username, r.getString("display_name"), Identity.Role.valueOf(r.getString("role")));
                }
            }
        } finally { Arrays.fill(password, '\0'); }
    }
    synchronized List<StoredRoom> loadRooms() throws SQLException {
        requireAvailable();
        List<StoredRoom> result = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM rooms ORDER BY room_id")) {
            while (r.next()) {
                String id = r.getString("room_id");
                result.add(new StoredRoom(id, r.getString("owner_id"), r.getString("code"), r.getLong("version"), loadHistory(id)));
            }
        }
        return result;
    }
    private static Long nullableLong(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column);
        return r.wasNull() ? null : value;
    }
    private List<CodeRevision> loadHistory(String id) throws SQLException {
        List<CodeRevision> result = new ArrayList<>();
        try (PreparedStatement s = db.prepareStatement("SELECT * FROM revisions WHERE room_id=? ORDER BY version")) {
            s.setString(1, id);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) result.add(new CodeRevision(r.getLong("version"), r.getString("editor_id"), r.getString("editor_name"), r.getString("code"), Instant.parse(r.getString("created_at")), nullableLong(r, "previous_version"), r.getString("changes")));
            }
        }
        return result;
    }
    synchronized StoredRoom createRoom(Identity owner, String code) throws SQLException {
        return transaction(() -> {
            String id;
            try (PreparedStatement s = db.prepareStatement("INSERT INTO rooms(owner_id,code,version) VALUES(?,?,0)")) {
                s.setString(1, owner.userId()); s.setString(2, code); s.executeUpdate();
            }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) { r.next(); id = r.getString(1); }
            CodeRevision initial = new CodeRevision(0, "SYSTEM", "Hệ thống", code);
            insertRevision(id, initial);
            return new StoredRoom(id, owner.userId(), code, 0, List.of(initial));
        });
    }
    synchronized void saveRevision(String room, long expected, CodeRevision revision) throws SQLException {
        transaction(() -> {
            try (PreparedStatement s = db.prepareStatement("UPDATE rooms SET code=?,version=? WHERE room_id=? AND version=?")) {
                s.setString(1, revision.getSourceCode()); s.setLong(2, revision.getVersion()); s.setString(3, room); s.setLong(4, expected);
                if (s.executeUpdate() != 1) throw new SQLException("Version database không khớp");
            }
            insertRevision(room, revision);
            return null;
        });
    }
    private void insertRevision(String room, CodeRevision r) throws SQLException {
        try (PreparedStatement s = db.prepareStatement("INSERT INTO revisions(room_id,version,editor_id,editor_name,created_at,code,previous_version,changes) VALUES(?,?,?,?,?,?,?,?)")) {
            s.setString(1, room); s.setLong(2, r.getVersion()); s.setString(3, r.getEditorId()); s.setString(4, r.getEditorName());
            s.setString(5, r.getCreatedAt().toString()); s.setString(6, r.getSourceCode());
            if (r.getPreviousVersion() == null) s.setNull(7, Types.BIGINT); else s.setLong(7, r.getPreviousVersion());
            s.setString(8, r.getChanges()); s.executeUpdate();
        }
    }
    private void ensurePerson3Schema() throws SQLException {
        try (Statement s = db.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS assignments (
                  assignment_id INTEGER PRIMARY KEY AUTOINCREMENT,
                  room_id TEXT NOT NULL,
                  title TEXT NOT NULL,
                  statement TEXT NOT NULL,
                  max_score INTEGER NOT NULL,
                  tests TEXT NOT NULL DEFAULT '',
                  status TEXT NOT NULL CHECK(status IN ('OPEN','CLOSED')),
                  created_by TEXT NOT NULL,
                  created_at TEXT NOT NULL
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS submissions (
                  submission_id INTEGER PRIMARY KEY AUTOINCREMENT,
                  assignment_id INTEGER NOT NULL REFERENCES assignments(assignment_id),
                  room_id TEXT NOT NULL,
                  user_id TEXT NOT NULL,
                  display_name TEXT NOT NULL,
                  code TEXT NOT NULL,
                  submitted_at TEXT NOT NULL,
                  status TEXT NOT NULL,
                  score INTEGER,
                  max_score INTEGER NOT NULL,
                  details TEXT
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS exams (
                  exam_id INTEGER PRIMARY KEY AUTOINCREMENT,
                  room_id TEXT NOT NULL,
                  title TEXT NOT NULL,
                  starts_at TEXT NOT NULL,
                  ends_at TEXT NOT NULL,
                  status TEXT NOT NULL CHECK(status IN ('RUNNING','ENDED')),
                  created_by TEXT NOT NULL
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_assignments_room ON assignments(room_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_submissions_room ON submissions(room_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_exams_room ON exams(room_id)");
            s.execute("""
                CREATE TABLE IF NOT EXISTS assignment_catalog (
                  catalog_id INTEGER PRIMARY KEY AUTOINCREMENT,
                  topic TEXT NOT NULL,
                  code TEXT NOT NULL UNIQUE,
                  title TEXT NOT NULL,
                  statement TEXT NOT NULL,
                  max_score INTEGER NOT NULL,
                  tests TEXT NOT NULL
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_catalog_topic ON assignment_catalog(topic)");
        }
        Set<String> assignCols = new HashSet<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(assignments)")) {
            while (r.next()) assignCols.add(r.getString("name"));
        }
        if (!assignCols.contains("topic")) {
            try (Statement s = db.createStatement()) {
                s.execute("ALTER TABLE assignments ADD COLUMN topic TEXT NOT NULL DEFAULT ''");
            }
        }
    }

    private void seedCatalog() throws SQLException {
        int count;
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM assignment_catalog")) {
            r.next();
            count = r.getInt(1);
        }
        if (count == 100) return;
        transaction(() -> {
            try (Statement del = db.createStatement()) {
                del.execute("DELETE FROM assignment_catalog");
            }
            try (PreparedStatement s = db.prepareStatement(
                    "INSERT INTO assignment_catalog(topic,code,title,statement,max_score,tests) VALUES(?,?,?,?,?,?)")) {
                for (var d : studyfix.person3.CatalogBank.build()) {
                    s.setString(1, d.topic());
                    s.setString(2, d.code());
                    s.setString(3, d.title());
                    s.setString(4, d.statement());
                    s.setInt(5, d.maxScore());
                    s.setString(6, d.tests());
                    s.addBatch();
                }
                s.executeBatch();
            }
            return null;
        });
    }

    synchronized List<studyfix.person3.Models.CatalogItem> listCatalog(String topic) throws SQLException {
        requireAvailable();
        List<studyfix.person3.Models.CatalogItem> out = new ArrayList<>();
        boolean all = topic == null || topic.isBlank() || topic.equalsIgnoreCase("ALL");
        String sql = all
                ? "SELECT * FROM assignment_catalog ORDER BY topic, catalog_id"
                : "SELECT * FROM assignment_catalog WHERE topic=? ORDER BY catalog_id";
        try (PreparedStatement s = db.prepareStatement(sql)) {
            if (!all) s.setString(1, topic.toUpperCase(Locale.ROOT));
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(readCatalog(r));
            }
        }
        return out;
    }

    synchronized studyfix.person3.Models.CatalogItem getCatalog(long id) throws SQLException {
        requireAvailable();
        try (PreparedStatement s = db.prepareStatement("SELECT * FROM assignment_catalog WHERE catalog_id=?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return readCatalog(r);
            }
        }
    }

    private static studyfix.person3.Models.CatalogItem readCatalog(ResultSet r) throws SQLException {
        String tests = r.getString("tests");
        int n = 0;
        if (tests != null && !tests.isBlank()) n = tests.split(";;", -1).length;
        return new studyfix.person3.Models.CatalogItem(
                r.getLong("catalog_id"),
                r.getString("topic"),
                r.getString("code"),
                r.getString("title"),
                r.getString("statement"),
                r.getInt("max_score"),
                tests,
                n);
    }

    synchronized studyfix.person3.Models.Assignment createAssignment(
            String roomId, String title, String statement, int maxScore, String tests, String createdBy)
            throws SQLException {
        return createAssignment(roomId, title, statement, maxScore, tests, createdBy, "");
    }

    synchronized studyfix.person3.Models.Assignment createAssignment(
            String roomId, String title, String statement, int maxScore, String tests, String createdBy, String topic)
            throws SQLException {
        return transaction(() -> {
            Instant now = Instant.now();
            try (PreparedStatement s = db.prepareStatement(
                    "INSERT INTO assignments(room_id,title,statement,max_score,tests,status,created_by,created_at,topic) VALUES(?,?,?,?,?,'OPEN',?,?,?)")) {
                s.setString(1, roomId);
                s.setString(2, title);
                s.setString(3, statement);
                s.setInt(4, maxScore);
                s.setString(5, tests == null ? "" : tests);
                s.setString(6, createdBy);
                s.setString(7, now.toString());
                s.setString(8, topic == null ? "" : topic);
                s.executeUpdate();
            }
            long id;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
                r.next();
                id = r.getLong(1);
            }
            return new studyfix.person3.Models.Assignment(
                    id, roomId, title, statement, maxScore, tests == null ? "" : tests,
                    "OPEN", createdBy, now, topic == null ? "" : topic);
        });
    }

    synchronized studyfix.person3.Models.Assignment getAssignment(long id) throws SQLException {
        requireAvailable();
        try (PreparedStatement s = db.prepareStatement("SELECT * FROM assignments WHERE assignment_id=?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return readAssignment(r);
            }
        }
    }

    synchronized List<studyfix.person3.Models.Assignment> listAssignments(String roomId) throws SQLException {
        requireAvailable();
        List<studyfix.person3.Models.Assignment> out = new ArrayList<>();
        try (PreparedStatement s = db.prepareStatement(
                "SELECT * FROM assignments WHERE room_id=? ORDER BY assignment_id")) {
            s.setString(1, roomId);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(readAssignment(r));
            }
        }
        return out;
    }

    synchronized void closeAssignment(long id) throws SQLException {
        transaction(() -> {
            try (PreparedStatement s = db.prepareStatement(
                    "UPDATE assignments SET status='CLOSED' WHERE assignment_id=?")) {
                s.setLong(1, id);
                if (s.executeUpdate() != 1) throw new SQLException("Không đóng được bài tập");
            }
            return null;
        });
    }

    synchronized void closeOpenAssignments(String roomId) throws SQLException {
        transaction(() -> {
            try (PreparedStatement s = db.prepareStatement(
                    "UPDATE assignments SET status='CLOSED' WHERE room_id=? AND status='OPEN'")) {
                s.setString(1, roomId);
                s.executeUpdate();
            }
            return null;
        });
    }

    synchronized studyfix.person3.Models.Submission createSubmission(
            long assignmentId, String roomId, String userId, String displayName, String code, int maxScore)
            throws SQLException {
        return transaction(() -> {
            Instant now = Instant.now();
            try (PreparedStatement s = db.prepareStatement(
                    "INSERT INTO submissions(assignment_id,room_id,user_id,display_name,code,submitted_at,status,score,max_score,details) VALUES(?,?,?,?,?,?,'PENDING',NULL,?,'')")) {
                s.setLong(1, assignmentId);
                s.setString(2, roomId);
                s.setString(3, userId);
                s.setString(4, displayName);
                s.setString(5, code);
                s.setString(6, now.toString());
                s.setInt(7, maxScore);
                s.executeUpdate();
            }
            long id;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
                r.next();
                id = r.getLong(1);
            }
            return new studyfix.person3.Models.Submission(
                    id, assignmentId, roomId, userId, displayName, code, now, "PENDING", null, maxScore, "");
        });
    }

    synchronized studyfix.person3.Models.Submission getSubmission(long id) throws SQLException {
        requireAvailable();
        try (PreparedStatement s = db.prepareStatement("SELECT * FROM submissions WHERE submission_id=?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return readSubmission(r);
            }
        }
    }

    synchronized List<studyfix.person3.Models.Submission> listSubmissions(String roomId, Long assignmentId)
            throws SQLException {
        requireAvailable();
        List<studyfix.person3.Models.Submission> out = new ArrayList<>();
        String sql = assignmentId == null
                ? "SELECT * FROM submissions WHERE room_id=? ORDER BY submission_id DESC"
                : "SELECT * FROM submissions WHERE room_id=? AND assignment_id=? ORDER BY submission_id DESC";
        try (PreparedStatement s = db.prepareStatement(sql)) {
            s.setString(1, roomId);
            if (assignmentId != null) s.setLong(2, assignmentId);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(readSubmission(r));
            }
        }
        return out;
    }

    synchronized void completeGrade(long submissionId, int score, String details) throws SQLException {
        transaction(() -> {
            try (PreparedStatement s = db.prepareStatement(
                    "UPDATE submissions SET status='GRADED', score=?, details=? WHERE submission_id=?")) {
                s.setInt(1, score);
                s.setString(2, details == null ? "" : details);
                s.setLong(3, submissionId);
                if (s.executeUpdate() != 1) throw new SQLException("Không cập nhật điểm");
            }
            return null;
        });
    }

    synchronized studyfix.person3.Models.Exam createExam(
            String roomId, String title, Instant startsAt, Instant endsAt, String createdBy)
            throws SQLException {
        return transaction(() -> {
            try (PreparedStatement s = db.prepareStatement(
                    "INSERT INTO exams(room_id,title,starts_at,ends_at,status,created_by) VALUES(?,?,?,?,'RUNNING',?)")) {
                s.setString(1, roomId);
                s.setString(2, title);
                s.setString(3, startsAt.toString());
                s.setString(4, endsAt.toString());
                s.setString(5, createdBy);
                s.executeUpdate();
            }
            long id;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
                r.next();
                id = r.getLong(1);
            }
            return new studyfix.person3.Models.Exam(id, roomId, title, startsAt, endsAt, "RUNNING", createdBy);
        });
    }

    synchronized studyfix.person3.Models.Exam activeExam(String roomId) throws SQLException {
        requireAvailable();
        try (PreparedStatement s = db.prepareStatement(
                "SELECT * FROM exams WHERE room_id=? AND status='RUNNING' ORDER BY exam_id DESC LIMIT 1")) {
            s.setString(1, roomId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return readExam(r);
            }
        }
    }

    synchronized studyfix.person3.Models.Exam latestExam(String roomId) throws SQLException {
        requireAvailable();
        try (PreparedStatement s = db.prepareStatement(
                "SELECT * FROM exams WHERE room_id=? ORDER BY exam_id DESC LIMIT 1")) {
            s.setString(1, roomId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return readExam(r);
            }
        }
    }

    synchronized void endExam(long examId) throws SQLException {
        transaction(() -> {
            try (PreparedStatement s = db.prepareStatement(
                    "UPDATE exams SET status='ENDED' WHERE exam_id=?")) {
                s.setLong(1, examId);
                s.executeUpdate();
            }
            return null;
        });
    }

    /** Best score per user per assignment, then sum — ranking for a room. */
    synchronized List<studyfix.person3.Models.RankEntry> ranking(String roomId) throws SQLException {
        requireAvailable();
        Map<String, int[]> totals = new LinkedHashMap<>(); // userId -> [scoreSum, subCount]
        Map<String, String> names = new HashMap<>();
        Map<String, Integer> best = new HashMap<>(); // userId|assignId -> best score
        try (PreparedStatement s = db.prepareStatement(
                "SELECT user_id, display_name, assignment_id, score FROM submissions "
                        + "WHERE room_id=? AND status='GRADED' AND score IS NOT NULL")) {
            s.setString(1, roomId);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    String uid = r.getString("user_id");
                    names.put(uid, r.getString("display_name"));
                    String key = uid + "|" + r.getLong("assignment_id");
                    int score = r.getInt("score");
                    best.merge(key, score, Math::max);
                    totals.computeIfAbsent(uid, k -> new int[2])[1]++;
                }
            }
        }
        for (Map.Entry<String, Integer> e : best.entrySet()) {
            String uid = e.getKey().substring(0, e.getKey().indexOf('|'));
            totals.computeIfAbsent(uid, k -> new int[2])[0] += e.getValue();
        }
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        List<studyfix.person3.Models.RankEntry> out = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, int[]> e : sorted) {
            out.add(new studyfix.person3.Models.RankEntry(
                    rank++, e.getKey(), names.getOrDefault(e.getKey(), e.getKey()),
                    e.getValue()[0], e.getValue()[1]));
        }
        return out;
    }

    private static studyfix.person3.Models.Assignment readAssignment(ResultSet r) throws SQLException {
        return new studyfix.person3.Models.Assignment(
                r.getLong("assignment_id"),
                r.getString("room_id"),
                r.getString("title"),
                r.getString("statement"),
                r.getInt("max_score"),
                r.getString("tests"),
                r.getString("status"),
                r.getString("created_by"),
                Instant.parse(r.getString("created_at")),
                columnOrEmpty(r, "topic"));
    }

    private static String columnOrEmpty(ResultSet r, String column) throws SQLException {
        try {
            String v = r.getString(column);
            return v == null ? "" : v;
        } catch (SQLException e) {
            return "";
        }
    }

    private static studyfix.person3.Models.Submission readSubmission(ResultSet r) throws SQLException {
        int score = r.getInt("score");
        Integer scoreObj = r.wasNull() ? null : score;
        return new studyfix.person3.Models.Submission(
                r.getLong("submission_id"),
                r.getLong("assignment_id"),
                r.getString("room_id"),
                r.getString("user_id"),
                r.getString("display_name"),
                r.getString("code"),
                Instant.parse(r.getString("submitted_at")),
                r.getString("status"),
                scoreObj,
                r.getInt("max_score"),
                r.getString("details"));
    }

    private static studyfix.person3.Models.Exam readExam(ResultSet r) throws SQLException {
        return new studyfix.person3.Models.Exam(
                r.getLong("exam_id"),
                r.getString("room_id"),
                r.getString("title"),
                Instant.parse(r.getString("starts_at")),
                Instant.parse(r.getString("ends_at")),
                r.getString("status"),
                r.getString("created_by"));
    }

    @Override public synchronized void close() throws SQLException { db.close(); }
}
