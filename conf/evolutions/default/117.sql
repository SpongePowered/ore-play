# --- !Ups
CREATE TABLE api_keys
(
    id                  BIGSERIAL PRIMARY KEY NOT NULL,
    created_at          TIMESTAMP             NOT NULL,
    name                VARCHAR(255),
    owner_id            BIGINT                NOT NULL REFERENCES users,
    token               VARCHAR(255)          NOT NULL UNIQUE,
    raw_key_permissions BIT(64)               NOT NULL
);

CREATE TABLE api_sessions
(
    id         BIGSERIAL PRIMARY KEY NOT NULL,
    created_at TIMESTAMP             NOT NULL,
    token      VARCHAR(255)          NOT NULL,
    key_id     BIGINT REFERENCES api_keys,
    user_id    BIGINT REFERENCES users,
    expires    TIMESTAMP             NOT NULL
);

# --- !Downs

DROP TABLE api_sessions;
DROP TABLE api_keys;
