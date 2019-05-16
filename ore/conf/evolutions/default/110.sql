# --- !Ups

ALTER TABLE project_versions
  ADD COLUMN review_state INT NOT NULL DEFAULT 0;
UPDATE project_versions
SET review_state = 2
WHERE is_non_reviewed = TRUE;

UPDATE project_versions
SET review_state = 1
WHERE is_reviewed = TRUE;

ALTER TABLE project_versions
  DROP COLUMN is_reviewed,
  DROP COLUMN is_non_reviewed;

--There's no way to go down again when applying this evolution.
UPDATE logged_actions SET action = 17 WHERE action = 10;

# --- !Downs

ALTER TABLE project_versions
  ADD COLUMN is_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN is_non_reviewed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE project_versions
SET is_reviewed = TRUE
WHERE review_state = 1;
UPDATE project_versions
SET is_non_reviewed = TRUE
WHERE review_state = 2;

ALTER TABLE project_versions
  DROP COLUMN review_state;
