# --- !Ups

CREATE TABLE project_version_reviews (
  id          SERIAL        PRIMARY KEY,
  version_id  INT           NOT NULL REFERENCES project_versions ON DELETE CASCADE,
  user_id     INT           NOT NULL REFERENCES users ON DELETE CASCADE,
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  ended_at    TIMESTAMP,
  comment     TEXT
);

# --- !Downs

DROP TABLE project_version_reviews;
