-- SQLite management schema, separate from core/code and learning history.
-- No passwords or default accounts are inserted by this migration.
CREATE TABLE IF NOT EXISTS schema_migrations(version INT PRIMARY KEY, applied_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS runtime_binding(binding_key VARCHAR(64) PRIMARY KEY,binding_value VARCHAR(64) NOT NULL);
CREATE TABLE IF NOT EXISTS users(
 user_id VARCHAR(36) PRIMARY KEY, username VARCHAR(64) NOT NULL UNIQUE,
 student_code VARCHAR(64) UNIQUE, display_name VARCHAR(200) NOT NULL,
 password_hash VARCHAR(256) NOT NULL, role TEXT CHECK(role IN ('TEACHER','STUDENT')) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at BIGINT NOT NULL,created_by VARCHAR(36) NULL
);
CREATE TABLE IF NOT EXISTS classes(
 class_id VARCHAR(64) PRIMARY KEY, room_code VARCHAR(80) NOT NULL,
 name VARCHAR(200) NOT NULL, owner_id VARCHAR(36) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,closed BOOLEAN NOT NULL DEFAULT FALSE,created_at BIGINT NOT NULL,
 CONSTRAINT classes_owner_fk FOREIGN KEY(owner_id) REFERENCES users(user_id)
);
CREATE TABLE IF NOT EXISTS class_members(
 class_id VARCHAR(64) NOT NULL,user_id VARCHAR(36) NOT NULL,
 membership_status TEXT CHECK(membership_status IN ('ENROLLED','REMOVED','PENDING','REJECTED')) NOT NULL DEFAULT 'ENROLLED',
 enrolled_at BIGINT NOT NULL,PRIMARY KEY(class_id,user_id),
 CONSTRAINT members_class_fk FOREIGN KEY(class_id) REFERENCES classes(class_id),
 CONSTRAINT members_user_fk FOREIGN KEY(user_id) REFERENCES users(user_id)
);
CREATE TABLE IF NOT EXISTS sessions(
 connection_id VARCHAR(36) PRIMARY KEY,session_id VARCHAR(36) NOT NULL UNIQUE,
 user_id VARCHAR(36) NOT NULL,ip VARCHAR(45) NOT NULL,login_time BIGINT NOT NULL,
 last_activity BIGINT NOT NULL,status TEXT CHECK(status IN ('ONLINE','OFFLINE')) NOT NULL,
 offline_time BIGINT NULL,reason VARCHAR(40) NOT NULL DEFAULT '',
 CONSTRAINT sessions_user_fk FOREIGN KEY(user_id) REFERENCES users(user_id)
);
CREATE TABLE IF NOT EXISTS attendance(
 class_id VARCHAR(64) NOT NULL,connection_id VARCHAR(36) NOT NULL,
 user_id VARCHAR(36) NOT NULL,joined_at BIGINT NOT NULL,left_at BIGINT NULL,visits_json JSON NULL,ip_snapshot VARCHAR(45) NOT NULL DEFAULT '',activity_snapshot BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(class_id,connection_id),
 CONSTRAINT attendance_class_fk FOREIGN KEY(class_id) REFERENCES classes(class_id),
 CONSTRAINT attendance_session_fk FOREIGN KEY(connection_id) REFERENCES sessions(connection_id),
 CONSTRAINT attendance_user_fk FOREIGN KEY(user_id) REFERENCES users(user_id)
);
CREATE TABLE IF NOT EXISTS class_admissions(
 request_id VARCHAR(36) PRIMARY KEY,class_id VARCHAR(64) NOT NULL,
 user_id VARCHAR(36) NOT NULL,session_id VARCHAR(36) NOT NULL,
 status TEXT CHECK(status IN ('PENDING','APPROVED','ENTERED','REJECTED','LEFT','CANCELLED')) NOT NULL,
 requested_at BIGINT NOT NULL,UNIQUE(class_id,session_id),
 CONSTRAINT admission_class_fk FOREIGN KEY(class_id) REFERENCES classes(class_id),
 CONSTRAINT admission_user_fk FOREIGN KEY(user_id) REFERENCES users(user_id),
 CONSTRAINT admission_session_fk FOREIGN KEY(session_id) REFERENCES sessions(session_id)
);
INSERT OR IGNORE INTO schema_migrations(version,applied_at) VALUES(100,CAST(strftime('%s','now') AS INTEGER)*1000);

CREATE INDEX IF NOT EXISTS sessions_user_activity ON sessions(user_id,last_activity);
CREATE INDEX IF NOT EXISTS attendance_class_user ON attendance(class_id,user_id);
