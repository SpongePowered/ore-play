# --- !Ups
ALTER TABLE project_visibility_changes DROP CONSTRAINT project_visibility_changes_project_id_fkey;
ALTER TABLE project_visibility_changes ADD CONSTRAINT project_visibility_changes_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

# --- !Downs
ALTER TABLE project_visibility_changes DROP CONSTRAINT project_visibility_changes_project_id_fkey;
ALTER TABLE project_visibility_changes ADD CONSTRAINT project_visibility_changes_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id);
