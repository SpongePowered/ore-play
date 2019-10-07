# --- !Ups

DROP TABLE project_log_entries;

# --- !Downs

CREATE TABLE project_log_entries (
    id              BIGSERIAL                                         NOT NULL
        CONSTRAINT project_log_entries_pkey PRIMARY KEY,
    created_at      TIMESTAMP                                         NOT NULL,
    tag             VARCHAR(255) DEFAULT 'default'::CHARACTER VARYING NOT NULL,
    message         TEXT                                              NOT NULL,
    occurrences     INTEGER      DEFAULT 1                            NOT NULL,
    last_occurrence TIMESTAMP                                         NOT NULL,
    project_id      BIGINT                                            NOT NULL
        CONSTRAINT project_log_entries_project_id_fkey REFERENCES projects ON DELETE CASCADE
);
