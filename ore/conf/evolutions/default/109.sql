# --- !Ups

ALTER TABLE project_versions
  DROP COLUMN assets;

# --- !Downs

ALTER TABLE project_versions
  ADD COLUMN assets VARCHAR(510);
