# --- !Ups

ALTER TABLE project_pages ALTER COLUMN contents DROP NOT NULL;

# --- !Downs

UPDATE project_pages SET contents = '' WHERE contents IS NULL;

ALTER TABLE project_pages ALTER COLUMN contents SET NOT NULL;
