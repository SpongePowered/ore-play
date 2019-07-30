# --- !Ups

ALTER TABLE project_version_tags ALTER COLUMN data DROP NOT NULL;

UPDATE project_version_tags pvt SET data = NULL WHERE pvt.data = '' OR pvt.data = 'null';

# --- !Downs

UPDATE project_version_tags pvt SET data = 'null' WHERE pvt.data = '' OR pvt.data = 'null';

ALTER TABLE project_version_tags ALTER COLUMN data SET NOT NULL;
