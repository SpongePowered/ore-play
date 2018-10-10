# --- !Ups
ALTER TABLE project_version_download_warnings
  ALTER COLUMN download_id DROP NOT NULL,
  ALTER COLUMN download_id DROP DEFAULT;

UPDATE project_version_download_warnings SET download_id = NULL WHERE download_id = -1;

# --- !Downs
UPDATE project_version_download_warnings SET download_id = -1 WHERE download_id IS NULL;

ALTER TABLE project_version_download_warnings
  ALTER COLUMN download_id SET DEFAULT -1,
  ALTER COLUMN download_id SET NOT NULL;
