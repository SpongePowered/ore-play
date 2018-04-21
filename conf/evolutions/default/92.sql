# --- !Ups
ALTER TABLE project_visibility_changes RENAME COLUMN visibility TO state;
ALTER TABLE project_visibility_changes RENAME TO project_state_changes;

ALTER TABLE projects RENAME COLUMN visibility TO state;

# --- !Downs
ALTER TABLE project_state_changes RENAME COLUMN state TO visibility;
ALTER TABLE project_state_changes RENAME TO project_visibility_changes;

ALTER TABLE projects RENAME COLUMN state TO visibility;
