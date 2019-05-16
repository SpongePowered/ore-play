# --- !Ups
ALTER TABLE project_settings ADD COLUMN support VARCHAR(255);

# --- !Downs

ALTER TABLE project_settings DROP COLUMN support;