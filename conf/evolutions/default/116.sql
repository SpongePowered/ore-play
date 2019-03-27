# --- !Ups
CREATE TABLE api_keys
(
  id                  BIGSERIAL PRIMARY KEY NOT NULL,
  created_at          TIMESTAMP             NOT NULL,
  owner_id            BIGINT                NOT NULL REFERENCES users,
  token               VARCHAR(255)          NOT NULL UNIQUE,
  raw_key_permissions BIT(64)               NOT NULL,
  is_ui_key           BOOLEAN               NOT NULL
);

# --- !Downs

DROP TABLE api_keys;
