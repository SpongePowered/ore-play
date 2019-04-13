# --- !Ups

ALTER TABLE project_views
  DROP CONSTRAINT IF EXISTS project_views_project_id_address_key;

# --- !Downs

ALTER TABLE project_views
  ADD CONSTRAINT project_views_project_id_address_key UNIQUE (project_id, address);
