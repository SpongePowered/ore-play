# --- !Ups

ALTER TABLE project_version_reviews
  ADD COLUMN comment_json JSONB;
UPDATE project_version_reviews
SET comment_json = CASE WHEN comment = '' THEN '{}'::JSONB ELSE comment :: JSONB END;
ALTER TABLE project_version_reviews
  DROP COLUMN comment,
  RENAME COLUMN comment_json TO comment;

ALTER TABLE projects
  ADD COLUMN notes_json JSONB;
UPDATE projects
SET notes_json = CASE WHEN notes = '' THEN '{}'::JSONB ELSE comment :: JSONB END;
ALTER TABLE projects
  DROP COLUMN notes,
  RENAME COLUMN notes_json TO notes;

# --- !Downs

ALTER TABLE project_version_reviews
  ADD COLUMN comment_not_json TEXT;
UPDATE project_version_reviews
SET comment_not_json = CASE WHEN comment = '{}'::JSONB THEN '' ELSE comment :: TEXT END;
ALTER TABLE project_version_reviews
  DROP COLUMN comment,
  RENAME COLUMN comment_not_json TO comment;

ALTER TABLE projects
  ADD COLUMN notes_not_json TEXT;
UPDATE projects
SET notes_not_json = CASE WHEN notes = '{}'::JSONB THEN '' ELSE comment :: TEXT END;
ALTER TABLE projects
  DROP COLUMN notes,
  RENAME COLUMN notes_not_json TO notes;
