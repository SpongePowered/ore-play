# --- !Ups

ALTER TABLE project_flags ADD COLUMN resolved_at timestamp;
ALTER TABLE project_flags ADD COLUMN resolved_by int;

UPDATE project_flags
  SET resolved_at = created_at
  WHERE is_resolved=true;
UPDATE project_flags
  SET resolved_by = -1
  WHERE is_resolved=true;
UPDATE project_flags
  SET comment = ''
  WHERE comment IS NULL;

# --- !Downs

ALTER TABLE project_flags DROP COLUMN resolved_at;
ALTER TABLE project_flags DROP COLUMN resolved_by;
