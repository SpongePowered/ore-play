# --- !Ups

ALTER TABLE projects ADD COLUMN notes TEXT NOT NULL DEFAULT '';

# --- !Downs

ALTER TABLE projects DROP COLUMN notes;
