# --- !Ups

ALTER TABLE project_version_tags DROP CONSTRAINT project_version_tags_version_id_fkey;
ALTER TABLE project_version_tags ADD CONSTRAINT project_version_tags_version_id_fkey FOREIGN KEY (version_id) REFERENCES project_versions (id) ON DELETE CASCADE;

# --- !Downs

ALTER TABLE project_version_tags DROP CONSTRAINT project_version_tags_version_id_fkey;
ALTER TABLE project_version_tags ADD CONSTRAINT project_version_tags_version_id_fkey FOREIGN KEY (version_id) REFERENCES project_versions (id);
