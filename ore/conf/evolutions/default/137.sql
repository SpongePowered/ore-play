# --- !Ups

CREATE TYPE SECURITY_LOG_EVENT_TYPE AS ENUM ('login', 'create_api_key', 'delete_api_key');

CREATE TABLE security_log_events
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ        NOT NULL,
    user_id    BIGINT             NOT NULL REFERENCES users,
    ip_address INET               NOT NULL,
    user_agent TEXT,
    location   TEXT,
    event      SECURITY_LOG_EVENT_TYPE NOT NULL,
    extra_data JSONB
);


# --- !Downs

DROP TABLE security_log_events;
DROP TYPE SECURITY_LOG_EVENT_TYPE;

