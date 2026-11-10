-- Additive migration. Existing Builder/OJ/Question Bank schema must be initialized first.
-- Runtime applies these CREATE statements idempotently from BuilderWorkflowRepository.
CREATE TABLE IF NOT EXISTS p3_builder_workflow (
 draft_id TEXT PRIMARY KEY REFERENCES p3_builder_drafts(id),
 state TEXT NOT NULL DEFAULT 'DRAFT', reviewer_id TEXT NOT NULL DEFAULT '',
 review_hash TEXT NOT NULL DEFAULT '', approved_hash TEXT NOT NULL DEFAULT '',
 actor_id TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS p3_builder_jobs (
 id TEXT PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES p3_builder_drafts(id),
 content_hash TEXT NOT NULL, status TEXT NOT NULL, progress TEXT NOT NULL,
 result_json TEXT NOT NULL DEFAULT '{}', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS p3_builder_jobs_draft ON p3_builder_jobs(draft_id,created_at);
CREATE TABLE IF NOT EXISTS p3_builder_publications (
 draft_id TEXT NOT NULL REFERENCES p3_builder_drafts(id), content_hash TEXT NOT NULL,
 exercise_id TEXT NOT NULL UNIQUE REFERENCES p3_exercises(id), snapshot_json TEXT NOT NULL,
 created_at INTEGER NOT NULL, PRIMARY KEY(draft_id,content_hash)
);
CREATE TABLE IF NOT EXISTS p3_builder_public_assets (
 asset_id TEXT NOT NULL REFERENCES p3_builder_assets(id),
 exercise_id TEXT NOT NULL REFERENCES p3_exercises(id), PRIMARY KEY(asset_id,exercise_id)
);
