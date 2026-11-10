-- Reference migration. Applied automatically by ExamBankRepository on startup.
-- Requires existing StudyFix learning schema; preserves all previous data.
PRAGMA foreign_keys=ON;
BEGIN IMMEDIATE;
CREATE TABLE IF NOT EXISTS p3_exam_bank(id TEXT PRIMARY KEY,owner_id TEXT NOT NULL,code TEXT NOT NULL UNIQUE,created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS p3_exam_versions(id TEXT PRIMARY KEY,bank_id TEXT NOT NULL REFERENCES p3_exam_bank(id),version_no INTEGER NOT NULL,revision INTEGER NOT NULL DEFAULT 1,status TEXT NOT NULL CHECK(status IN ('DRAFT','APPROVED','ARCHIVED')),document_json TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(bank_id,version_no));
CREATE TABLE IF NOT EXISTS p3_exam_version_items(version_id TEXT NOT NULL REFERENCES p3_exam_versions(id),ordinal_no INTEGER NOT NULL,exercise_id TEXT NOT NULL REFERENCES p3_exercises(id),points REAL NOT NULL CHECK(points>0),PRIMARY KEY(version_id,ordinal_no),UNIQUE(version_id,exercise_id));
CREATE TABLE IF NOT EXISTS p3_exam_history(id INTEGER PRIMARY KEY AUTOINCREMENT,version_id TEXT NOT NULL REFERENCES p3_exam_versions(id),actor_id TEXT NOT NULL,actor_name TEXT NOT NULL,action TEXT NOT NULL,created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS p3_exam_contests(contest_id TEXT PRIMARY KEY REFERENCES p3_contests(id),version_id TEXT NOT NULL REFERENCES p3_exam_versions(id),config_json TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS p3_exam_owner ON p3_exam_bank(owner_id);
CREATE INDEX IF NOT EXISTS p3_exam_history_version ON p3_exam_history(version_id,id);
COMMIT;
