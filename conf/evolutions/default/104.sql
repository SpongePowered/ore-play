# --- !Ups
DROP TABLE projects_deleted;
CREATE INDEX user_session_token_idx ON user_sessions (token);
CREATE INDEX page_slug_idx on project_pages (lower(slug));
CREATE INDEX page_parent_id_idx on project_pages (parent_id);

# --- !Downs

CREATE TABLE projects_deleted (
  id                      bigserial PRIMARY KEY NOT NULL,
  created_at              TIMESTAMP NOT NULL,
  name                    VARCHAR(255) NOT NULL,
  slug                    VARCHAR(255) NOT NULL,
  owner_name              VARCHAR(255) NOT NULL,
  homepage                text,
  recommended_version_id  bigint DEFAULT -1,
  category                INT NOT NULL,
  views                   bigint NOT NULL,
  downloads               bigint NOT NULL,
  stars                   bigint NOT NULL,
  issues                  text,
  source                  text,
  description             VARCHAR(255),
  owner_id                bigint REFERENCES users ON DELETE RESTRICT,
  topic_id                bigint DEFAULT -1,
  post_id                 bigint DEFAULT -1,
  is_topic_dirty          boolean DEFAULT FALSE,
  is_visible              boolean DEFAULT TRUE,
  last_updated            TIMESTAMP NOT NULL DEFAULT now()
);

DROP INDEX IF EXISTS user_session_token_idx;
DROP INDEX IF EXISTS page_slug_idx;
DROP INDEX IF EXISTS page_parent_id_idx;
