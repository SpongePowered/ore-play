# --- !Ups

ALTER TABLE project_log_entries
    ADD COLUMN project_id BIGINT REFERENCES projects ON DELETE CASCADE;

UPDATE project_log_entries ple
SET project_id = pl.project_id
FROM project_logs pl
WHERE ple.log_id = pl.id
  AND ple.project_id IS NULL;

ALTER TABLE project_log_entries
    ALTER COLUMN project_id SET NOT NULL;

ALTER TABLE project_log_entries
    DROP COLUMN log_id;

DROP TABLE project_logs;

# --- !Downs

CREATE TABLE project_logs
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    project_id BIGINT    NOT NULL REFERENCES projects ON DELETE CASCADE
);

ALTER TABLE project_log_entries
    ADD COLUMN log_id BIGINT REFERENCES project_logs ON DELETE CASCADE;

INSERT INTO project_logs (created_at, project_id)
SELECT now(), ple.project_id
FROM project_log_entries ple;

UPDATE project_log_entries ple
SET log_id = pl.id
FROM project_logs pl
WHERE ple.project_id = pl.project_id;

ALTER TABLE project_log_entries
    ALTER COLUMN log_id SET NOT NULL,
    DROP COLUMN project_id;
