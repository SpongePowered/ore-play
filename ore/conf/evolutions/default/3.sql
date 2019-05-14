# --- !Ups

ALTER TABLE projects ADD COLUMN issues text;
ALTER TABLE projects ADD COLUMN source text;

# --- !Downs

ALTER TABLE projects DROP COLUMN issues;
ALTER TABLE projects DROP COLUMN source;
