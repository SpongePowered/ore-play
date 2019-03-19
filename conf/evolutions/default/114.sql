# --- !Ups
ALTER TABLE project_versions
  ADD COLUMN create_forum_post BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN post_id INT,
  ADD COLUMN is_post_dirty BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE project_versions
  ALTER COLUMN create_forum_post DROP DEFAULT,
  ALTER COLUMN is_post_dirty DROP DEFAULT;

# --- !Downs

ALTER TABLE project_versions
  DROP COLUMN create_forum_post,
  DROP COLUMN post_id,
  DROP COLUMN is_post_dirty;