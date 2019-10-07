# --- !Ups

ALTER TABLE project_version_reviews
  ADD COLUMN comment_json JSONB NOT NULL DEFAULT '{}'::JSONB;
UPDATE project_version_reviews
  SET comment_json = CASE WHEN comment = '' THEN '{}'::JSONB ELSE comment::JSONB END;
ALTER TABLE project_version_reviews
  DROP COLUMN comment;
ALTER TABLE project_version_reviews
  RENAME COLUMN comment_json TO comment;

ALTER TABLE projects
  ADD COLUMN notes_json JSONB NOT NULL DEFAULT '{}'::JSONB;
UPDATE projects
  SET notes_json = CASE WHEN notes = '' THEN '{}'::JSONB ELSE notes::JSONB END;
ALTER TABLE projects
  DROP COLUMN notes;
ALTER TABLE projects
  RENAME COLUMN notes_json TO notes;

# --- !Downs

ALTER TABLE project_version_reviews
  ADD COLUMN comment_not_json TEXT NOT NULL DEFAULT '';
UPDATE project_version_reviews
  SET comment_not_json = CASE WHEN comment = '{}'::JSONB THEN '' ELSE comment::TEXT END;
ALTER TABLE project_version_reviews
  DROP COLUMN comment;
ALTER TABLE project_version_reviews
  RENAME COLUMN comment_not_json TO comment;

ALTER TABLE projects
  ADD COLUMN notes_not_json TEXT NOT NULL DEFAULT '';
UPDATE projects
  SET notes_not_json = CASE WHEN notes = '{}'::JSONB THEN '' ELSE notes::TEXT END;
ALTER TABLE projects
  DROP COLUMN notes;
ALTER TABLE projects
  RENAME COLUMN notes_not_json TO notes;
