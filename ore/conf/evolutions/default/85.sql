# --- !Ups

CREATE OR REPLACE FUNCTION delete_old_project_version_downloads()
  RETURNS trigger AS
$$
BEGIN
  DELETE FROM project_version_downloads WHERE created_at < current_date - interval '30' day;;
  RETURN NEW;;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER clean_old_project_version_downloads AFTER INSERT
  ON project_version_downloads
  FOR EACH STATEMENT
EXECUTE PROCEDURE delete_old_project_version_downloads();


CREATE OR REPLACE FUNCTION delete_old_project_version_download_warnings()
  RETURNS trigger AS
$$
BEGIN
  DELETE FROM project_version_download_warnings WHERE created_at < current_date - interval '30' day;;
  RETURN NEW;;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER clean_old_project_version_download_warnings AFTER INSERT
  ON project_version_download_warnings
  FOR EACH STATEMENT
EXECUTE PROCEDURE delete_old_project_version_download_warnings();


CREATE OR REPLACE FUNCTION delete_old_project_version_unsafe_downloads()
  RETURNS trigger AS
$$
BEGIN
  DELETE FROM project_version_unsafe_downloads WHERE created_at < current_date - interval '30' day;;
  RETURN NEW;;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER clean_old_project_version_unsafe_downloads AFTER INSERT
  ON project_version_unsafe_downloads
  FOR EACH STATEMENT
EXECUTE PROCEDURE delete_old_project_version_unsafe_downloads();

# --- !Downs

DROP TRIGGER clean_old_project_version_downloads ON project_version_downloads;
DROP FUNCTION delete_old_project_version_downloads();

DROP TRIGGER clean_old_project_version_download_warnings ON project_version_download_warnings;
DROP FUNCTION delete_old_project_version_download_warnings();

DROP TRIGGER clean_old_project_version_unsafe_downloads ON project_version_unsafe_downloads;
DROP FUNCTION delete_old_project_version_unsafe_downloads();
