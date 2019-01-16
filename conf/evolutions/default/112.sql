# --- !Ups

ALTER TABLE project_settings ADD COLUMN github_sync BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_settings ALTER COLUMN github_sync DROP DEFAULT;

# --- !Downs

ALTER TABLE project_settings DROP COLUMN github_sync;
