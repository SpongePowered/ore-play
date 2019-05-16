# --- !Ups
CREATE TABLE project_version_visibility_changes (
  id                  SERIAL PRIMARY KEY,
  created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
  created_by          INT       NOT NULL REFERENCES users,
  version_id          INT       NOT NULL REFERENCES project_versions,
  comment             TEXT,
  resolved_at         TIMESTAMP,
  resolved_by         INT       REFERENCES users,
  visibility          INT
);
ALTER TABLE project_versions ADD COLUMN visibility INT NOT NUll DEFAULT 1;

# --- !Downs
DROP TABLE project_version_visibility_changes;
ALTER TABLE project_versions DROP COLUMN visibility;
