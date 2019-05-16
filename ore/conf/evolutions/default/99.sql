# --- !Ups

ALTER TABLE project_versions ADD COLUMN is_non_reviewed BOOLEAN DEFAULT false;

# --- !Downs

ALTER TABLE project_versions DROP COLUMN is_non_reviewed;
