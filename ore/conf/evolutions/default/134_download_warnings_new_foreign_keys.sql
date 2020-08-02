# --- !Ups
TRUNCATE project_version_download_warnings;
ALTER TABLE project_version_download_warnings
    DROP CONSTRAINT project_version_download_warnings_download_id_fkey,
    ADD CONSTRAINT project_version_download_warnings_download_id_fkey FOREIGN KEY (download_id) REFERENCES project_version_unsafe_downloads ON DELETE CASCADE;

# --- !Downs
TRUNCATE project_version_download_warnings;
ALTER TABLE project_version_download_warnings
    DROP CONSTRAINT project_version_download_warnings_download_id_fkey,
    ADD CONSTRAINT project_version_download_warnings_download_id_fkey FOREIGN KEY (download_id) REFERENCES project_versions_downloads_individual ON DELETE CASCADE;
