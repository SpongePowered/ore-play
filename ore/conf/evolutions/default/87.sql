# --- !Ups
CREATE TABLE project_visibility_changes (
  id                  SERIAL PRIMARY KEY,
  created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
  created_by          INT       NOT NULL REFERENCES users,
  project_id          INT       NOT NULL REFERENCES projects,
  comment             TEXT,
  resolved_at         TIMESTAMP,
  resolved_by         INT       REFERENCES users,
  visibility          INT
);

# --- !Downs
DROP TABLE project_visibility_changes;
