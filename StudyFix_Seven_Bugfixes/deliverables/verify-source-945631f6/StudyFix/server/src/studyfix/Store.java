package studyfix;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Dùng một kết nối JDBC với các thao tác chạy tuần tự. Chỉ cập nhật RAM sau khi commit thành công. */
final class Store implements AutoCloseable {
    record StoredRoom(String id, String owner, String code, long version, List<CodeRevision> history, String publicCode) { }
    private static final Logger LOG = Logger.getLogger(Store.class.getName());
    private final String databaseUrl;
    private Connection db;
    private SQLException unavailable;
    private final MySqlManagement management;
    Store(Path path) throws SQLException, IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        databaseUrl = "jdbc:sqlite:" + path.toAbsolutePath();
        db = openConnection();
        try {
            ManagementConfig managementConfig=ManagementConfig.environment();
            management=managementConfig==null?null:new MySqlManagement(managementConfig);
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS users (user_id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('TEACHER','STUDENT')))");
                s.execute("CREATE TABLE IF NOT EXISTS rooms (room_id INTEGER PRIMARY KEY AUTOINCREMENT, owner_id TEXT NOT NULL REFERENCES users(user_id), code TEXT NOT NULL, version INTEGER NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS revisions (room_id INTEGER NOT NULL REFERENCES rooms(room_id), version INTEGER NOT NULL, editor_id TEXT NOT NULL, editor_name TEXT NOT NULL, created_at TEXT NOT NULL, code TEXT NOT NULL, PRIMARY KEY(room_id,version))");
            }
            if(management==null){
                boolean bound=false;
                try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='management_metadata'")){bound=r.next();}
                if(bound)try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT 1 FROM management_metadata WHERE name='core_id'")){
                    if(r.next())throw new SQLException("Database lõi đã liên kết MySQL. Cần khôi phục biến môi trường MySQL; không dùng bản sao tài khoản SQLite để xác thực dự phòng.");
                }
            }
            migrateHistory();
            migrateRoomCodes();
            transaction(() -> { ClassroomRepository.migrate(db); return null; });
            transaction(() -> { PresenceRepository.migrate(db); return null; });
            if(management!=null){
                String coreId=transaction(()->{
                    try(Statement p=db.createStatement()){p.execute("CREATE TABLE IF NOT EXISTS management_metadata(name TEXT PRIMARY KEY,value TEXT NOT NULL)");}
                    try(PreparedStatement p=db.prepareStatement("INSERT OR IGNORE INTO management_metadata(name,value) VALUES('core_id',?)")){p.setString(1,UUID.randomUUID().toString());p.executeUpdate();}
                    try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT value FROM management_metadata WHERE name='core_id'")){r.next();return r.getString(1);}
                });
                management.bindCore(coreId);
                if("true".equalsIgnoreCase(System.getenv("STUDYFIX_MYSQL_IMPORT_SQLITE")))importManagementAccounts();
                management.bootstrap();
                if(management.accounts().stream().noneMatch(a->a.identity().role()==Identity.Role.TEACHER))throw new SQLException("MySQL chưa có Teacher. Cần import tài khoản cũ hoặc đặt STUDYFIX_BOOTSTRAP_TEACHER và STUDYFIX_BOOTSTRAP_PASSWORD.");
                for(MySqlManagement.Account account:management.accounts())mirrorAccount(account);
                try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT room_id,public_code,owner_id,active FROM rooms")){
                    while(r.next())management.syncClass(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4));
                }
                if("true".equalsIgnoreCase(System.getenv("STUDYFIX_MYSQL_IMPORT_SQLITE")))importManagementPresence();
            } else {
                String fixtures=System.getProperty("studyfix.test.accounts","");
                if(!fixtures.isBlank())for(String line:Files.readAllLines(Path.of(fixtures),java.nio.charset.StandardCharsets.UTF_8)){
                    if(line.isBlank()||line.startsWith("#"))continue;String[] fields=line.split("\\t",-1);
                    if(fields.length!=4)throw new IllegalArgumentException("Test account fixture cần 4 cột TSV.");seed(fields[0],fields[1],fields[2],Identity.Role.valueOf(fields[3]));
                }
                String bootstrapUser=System.getenv("STUDYFIX_BOOTSTRAP_TEACHER"),bootstrapPassword=System.getenv("STUDYFIX_BOOTSTRAP_PASSWORD");
                if(bootstrapUser!=null&&bootstrapPassword!=null){
                    if(!bootstrapUser.matches("[A-Za-z0-9_.-]{1,64}")||bootstrapPassword.length()<12)throw new IllegalArgumentException("Bootstrap cần tên hợp lệ và mật khẩu ít nhất 12 ký tự.");
                    seed(bootstrapUser,bootstrapPassword,System.getenv().getOrDefault("STUDYFIX_BOOTSTRAP_NAME","Giảng viên quản trị"),Identity.Role.TEACHER);
                }
            }
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

    /** Lỗi dọn tài nguyên sau commit không được làm báo nhầm rằng thao tác ghi đã thất bại. */
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
                // Không bật autocommit nếu rollback bị lỗi vì có thể vô tình lưu phần dữ liệu còn dang dở.
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
            // Kết nối đã đóng sẽ chặn thao tác tiếp theo; dữ liệu đã commit và cập nhật RAM vẫn được phép công bố.
        }
        LOG.log(Level.WARNING, "JDBC cleanup failed; replaced connection if available. Committed writes remain successful.", failure);
    }
    private void requireAvailable() throws SQLException {
        if (unavailable != null) throw new SQLException("Database connection unavailable; restart server to retry", unavailable);
    }
    /** Dùng cột cho phép null để nhận biết bước nâng cấp; thay đổi cấu trúc và điền dữ liệu cũ được commit cùng nhau. */
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
            // Đọc lần lượt các bản chụp đã lưu, không đoán tác giả từ tên hiển thị hay chủ phòng.
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
        try (PreparedStatement s = db.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role,student_code) VALUES(?,?,?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2, username);
            s.setString(3, Passwords.hash(secret)); s.setString(4, name); s.setString(5, role.name()); s.setString(6,role==Identity.Role.STUDENT?username:null); s.executeUpdate();
        } finally { Arrays.fill(secret, '\0'); }
    }
    private void migrateRoomCodes() throws SQLException {
        transaction(() -> {
            Set<String> columns=new HashSet<>();
            try(Statement s=db.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info(rooms)")){while(r.next())columns.add(r.getString("name"));}
            try(Statement s=db.createStatement()) {
                if(!columns.contains("public_code"))s.execute("ALTER TABLE rooms ADD COLUMN public_code TEXT");
                if(!columns.contains("code_key"))s.execute("ALTER TABLE rooms ADD COLUMN code_key TEXT");
                if(!columns.contains("active"))s.execute("ALTER TABLE rooms ADD COLUMN active INTEGER NOT NULL DEFAULT 1");
                s.execute("UPDATE rooms SET public_code=CAST(room_id AS TEXT),code_key=CAST(room_id AS TEXT) WHERE public_code IS NULL");
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS room_active_code ON rooms(code_key) WHERE active=1");
            }return null;
        });
    }
    Identity login(String username, char[] password) throws SQLException {
        try {
            if(management!=null){
                MySqlManagement.Account account=management.authenticate(username,password);
                if(account==null)return null;mirrorAccount(account);return account.identity();
            }
            Identity candidate;
            String hash;
            synchronized (this) {
            requireAvailable();
            try (PreparedStatement s = db.prepareStatement("SELECT * FROM users WHERE username=? OR (role='STUDENT' AND student_code=?) ORDER BY CASE WHEN student_code=? THEN 0 ELSE 1 END LIMIT 1")) {
                s.setString(1, username); s.setString(2,username); s.setString(3,username);
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) return null;
                    hash = r.getString("password_hash");
                    candidate = new Identity(r.getString("user_id"), r.getString("username"), r.getString("display_name"), Identity.Role.valueOf(r.getString("role")));
                }
            }
            }
            // PBKDF2 must not block unrelated room/classroom database operations.
            return Passwords.verify(password, hash) ? candidate : null;
        } finally { Arrays.fill(password, '\0'); }
    }
    MySqlManagement management(){return management;}
    synchronized void mirrorAccount(MySqlManagement.Account account)throws SQLException{
        requireAvailable();
        try(PreparedStatement p=db.prepareStatement("SELECT user_id FROM users WHERE username=?")){
            p.setString(1,account.identity().username());try(ResultSet r=p.executeQuery()){if(r.next()&&!r.getString(1).equals(account.identity().userId()))throw new SQLException("UserId MySQL khác tài khoản SQLite cùng username. Cần import SQLite trước, không tự thay userId.");}
        }
        try(PreparedStatement p=db.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role,student_code) VALUES(?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET username=excluded.username,password_hash=excluded.password_hash,display_name=excluded.display_name,role=excluded.role,student_code=excluded.student_code")){
            p.setString(1,account.identity().userId());p.setString(2,account.identity().username());p.setString(3,account.hash());p.setString(4,account.identity().displayName());p.setString(5,account.identity().role().name());p.setString(6,account.studentCode());p.executeUpdate();
        }
    }
    private void importManagementAccounts()throws SQLException{
        try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT user_id,username,display_name,role,password_hash,student_code FROM users")){
            while(r.next())management.importAccount(new MySqlManagement.Account(new Identity(r.getString(1),r.getString(2),r.getString(3),Identity.Role.valueOf(r.getString(4))),r.getString(5),r.getString(6),true));
        }
    }
    private void importManagementPresence()throws SQLException{
        try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT connection_id,session_id,user_id,ip,login_time,last_activity FROM network_sessions")){
            while(r.next())management.importLegacySession(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getLong(6));
        }
        try(Statement p=db.createStatement();ResultSet r=p.executeQuery("SELECT a.room_id,a.connection_id,n.user_id,a.joined_at,a.left_at,n.last_activity FROM classroom_connections a JOIN network_sessions n ON n.connection_id=a.connection_id")){
            while(r.next()){long ended=r.getLong(5);Long left=r.wasNull()?null:ended;management.importLegacyAttendance(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),left,r.getLong(6));}
        }
    }
    synchronized String userLabel(String userId) throws SQLException {
        requireAvailable();
        try (PreparedStatement s = db.prepareStatement("SELECT display_name,username FROM users WHERE user_id=?")) {
            s.setString(1, userId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getString("display_name") + " (" + r.getString("username") + ")" : userId;
            }
        }
    }
    synchronized List<StoredRoom> loadRooms() throws SQLException {
        requireAvailable();
        List<StoredRoom> result = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM rooms WHERE active=1 ORDER BY room_id")) {
            while (r.next()) {
                String id = r.getString("room_id");
                result.add(new StoredRoom(id, r.getString("owner_id"), r.getString("code"), r.getLong("version"), loadHistory(id),r.getString("public_code")));
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
        return createRoom(owner,code,null);
    }
    synchronized StoredRoom createRoom(Identity owner, String code, String requestedCode) throws SQLException {
        return transaction(() -> {
            String id;
            try (PreparedStatement s = db.prepareStatement("INSERT INTO rooms(owner_id,code,version) VALUES(?,?,0)")) {
                s.setString(1, owner.userId()); s.setString(2, code); s.executeUpdate();
            }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) { r.next(); id = r.getString(1); }
            String publicCode=requestedCode==null?id:RoomCode.normalize(requestedCode);
            // Giữ lệnh /create của client dòng lệnh cũ và tránh trùng mã số do phòng khác đã chọn.
            if(requestedCode==null) {
                try(PreparedStatement s=db.prepareStatement("SELECT 1 FROM rooms WHERE active=1 AND code_key=?")) {
                    s.setString(1,RoomCode.key(publicCode));
                    try(ResultSet r=s.executeQuery()){if(r.next())publicCode=id+"-"+UUID.randomUUID().toString().substring(0,8);}
                }
            }
            try(PreparedStatement s=db.prepareStatement("UPDATE rooms SET public_code=?,code_key=? WHERE room_id=?")) {
                s.setString(1,publicCode);s.setString(2,RoomCode.key(publicCode));s.setString(3,id);s.executeUpdate();
            }
            CodeRevision initial = new CodeRevision(0, "SYSTEM", "Hệ thống", code);
            insertRevision(id, initial);
            return new StoredRoom(id, owner.userId(), code, 0, List.of(initial),publicCode);
        });
    }
    synchronized void expireRoom(String id) throws SQLException {
        transaction(() -> {try(PreparedStatement s=db.prepareStatement("UPDATE rooms SET active=0 WHERE room_id=?")){s.setString(1,id);s.executeUpdate();}return null;});
    }
    synchronized void saveRevision(String room, long expected, CodeRevision revision) throws SQLException {
        transaction(() -> {
            persistRevision(room, expected, revision);
            return null;
        });
    }
    // Chỉ gọi trong classroomTransaction, dùng chung kết nối và giao dịch hiện tại.
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
