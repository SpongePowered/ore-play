# --- !Ups

CREATE TYPE JOB_STATE AS ENUM ('not_started', 'started', 'done', 'fatal_failure');
CREATE TABLE jobs
(
    id                    BIGSERIAL PRIMARY KEY,
    created_at            TIMESTAMPTZ NOT NULL,
    last_updated          TIMESTAMPTZ,
    retry_at              TIMESTAMPTZ,
    last_error            TEXT,
    last_error_descriptor TEXT,
    state                 JOB_STATE   NOT NULL,
    job_type              TEXT        NOT NULL,
    job_properties        HSTORE      NOT NULL
);

ALTER TABLE projects
    DROP COLUMN is_topic_dirty;
ALTER TABLE project_versions
    DROP COLUMN is_post_dirty;


# --- !Downs

ALTER TABLE project_versions
    ADD is_post_dirty BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE project_versions
    ALTER COLUMN is_post_dirty DROP DEFAULT;

ALTER TABLE projects
    ADD is_topic_dirty BOOLEAN DEFAULT FALSE NOT NULL;

DROP TABLE jobs;
DROP TYPE JOB_STATE;