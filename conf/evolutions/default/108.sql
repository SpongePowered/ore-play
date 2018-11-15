# --- !Ups
ALTER TABLE project_version_download_warnings
  DROP COLUMN download_id;

DROP TABLE project_version_unsafe_downloads;
DROP ROUTINE delete_old_project_version_unsafe_downloads();

# --- !Downs

CREATE TABLE project_version_unsafe_downloads
(
  id            BIGSERIAL NOT NULL
    CONSTRAINT project_version_unsafe_downloads_pkey
    PRIMARY KEY,
  created_at    TIMESTAMP NOT NULL,
  user_id       BIGINT DEFAULT '-1' :: INTEGER,
  address       INET      NOT NULL,
  download_type INTEGER   NOT NULL
);

CREATE FUNCTION delete_old_project_version_unsafe_downloads()
  RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  DELETE FROM project_version_unsafe_downloads WHERE created_at < current_date - INTERVAL '30' DAY;
  RETURN new;
END
$$;

CREATE TRIGGER clean_old_project_version_unsafe_downloads
  AFTER INSERT
  ON project_version_unsafe_downloads
EXECUTE PROCEDURE delete_old_project_version_unsafe_downloads();

ALTER TABLE project_version_download_warnings
  ADD COLUMN download_id BIGINT;
