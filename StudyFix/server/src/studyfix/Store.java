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
            transaction(() -> { ClassroomRepository.migrate(db); return null; });
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
            persistRevision(room, expected, revision);
            return null;
        });
    }
    // Called only inside classroomTransaction, with the same connection and transaction.
    void persistRevision(String room, long expected, CodeRevision revision) throws SQLException {
            try (PreparedStatement s = db.prepareStatement("UPDATE rooms SET code=?,version=? WHERE room_id=? AND version=?")) {
                s.setString(1, revision.getSourceCode()); s.setLong(2, revision.getVersion()); s.setString(3, room); s.setLong(4, expected);
                if (s.executeUpdate() != 1) throw new SQLException("Version database không khớp");
            }
            insertRevision(room, revision);
    }
    @FunctionalInterface interface ClassroomWork<T> { T execute(Connection connection) throws SQLException; }
    synchronized <T> T classroomTransaction(ClassroomWork<T> work) throws SQLException {
        return transaction(() -> work.execute(db));
    }
    synchronized String classroomPhase(String room) throws SQLException {
        requireAvailable();
        try (PreparedStatement s = db.prepareStatement("SELECT phase FROM classroom_sessions WHERE room_id=?")) {
            s.setString(1, room);
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }
    private void insertRevision(String room, CodeRevision r) throws SQLException {
        try (PreparedStatement s = db.prepareStatement("INSERT INTO revisions(room_id,version,editor_id,editor_name,created_at,code,previous_version,changes) VALUES(?,?,?,?,?,?,?,?)")) {
            s.setString(1, room); s.setLong(2, r.getVersion()); s.setString(3, r.getEditorId()); s.setString(4, r.getEditorName());
            s.setString(5, r.getCreatedAt().toString()); s.setString(6, r.getSourceCode());
            if (r.getPreviousVersion() == null) s.setNull(7, Types.BIGINT); else s.setLong(7, r.getPreviousVersion());
            s.setString(8, r.getChanges()); s.executeUpdate();
        }
    }
    @Override public synchronized void close() throws SQLException { db.close(); }
}
