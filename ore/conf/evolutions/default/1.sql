# --- !Ups

CREATE TABLE users (
  external_id  bigint        NOT NULL PRIMARY KEY,
  created_at   timestamp     NOT NULL,
  name         varchar(255)  NOT NULL,
  username     varchar(255)  NOT NULL,
  email        varchar(255)  NOT NULL
);

CREATE TABLE projects (
  id                      serial          NOT NULL PRIMARY KEY,
  created_at              timestamp       NOT NULL,
  plugin_id               varchar(255)    NOT NULL UNIQUE,
  name                    varchar(255)    NOT NULL,
  slug                    varchar(255)    NOT NULL,
  owner_name              varchar(255)    NOT NULL,
  authors                 varchar(255)[]  NOT NULL,
  homepage                text            ,
  recommended_version_id  bigint          ,
  category_id             int             NOT NULL CHECK (category_id >= 0),
  views                   bigint          NOT NULL CHECK (views >= 0),
  downloads               bigint          NOT NULL CHECK (downloads >= 0),
  stars                   bigint          NOT NULL CHECK (stars >= 0),
  UNIQUE (owner_name, name),
  UNIQUE (owner_name, slug)
);

CREATE TABLE project_views (
  id          serial        NOT NULL PRIMARY KEY,
  cookie      varchar(50)   ,
  user_id     bigint        REFERENCES users ON DELETE CASCADE,
  project_id  bigint        NOT NULL REFERENCES projects ON DELETE CASCADE
);

CREATE TABLE project_stars (
  user_id     bigint    NOT NULL REFERENCES users ON DELETE CASCADE,
  project_id  bigint    NOT NULL REFERENCES projects ON DELETE CASCADE,
  PRIMARY KEY (user_id, project_id)
);

CREATE TABLE pages (
  id            serial        NOT NULL PRIMARY KEY,
  created_at    timestamp     NOT NULL,
  project_id    bigint        NOT NULL REFERENCES projects ON DELETE CASCADE,
  name          varchar(255)  NOT NULL,
  slug          varchar(255)  NOT NULL,
  contents      text          NOT NULL DEFAULT '',
  is_deletable  boolean       NOT NULL DEFAULT true,
  UNIQUE (project_id, name),
  UNIQUE (project_id, slug)
);

CREATE TABLE channels (
  id          serial        NOT NULL PRIMARY KEY,
  created_at  timestamp     NOT NULL,
  name        varchar(255)  NOT NULL,
  color_id    int           NOT NULL,
  project_id  bigint        NOT NULL REFERENCES projects ON DELETE CASCADE,
  UNIQUE (project_id, name),
  UNIQUE (project_id, color_id)
);

CREATE TABLE versions (
  id              serial          NOT NULL PRIMARY KEY,
  created_at      timestamp       NOT NULL,
  version_string  varchar(255)    NOT NULL,
  dependencies    varchar(255)[]  NOT NULL,
  description     text            ,
  assets          varchar(510)    ,
  downloads       bigint          NOT NULL CHECK (downloads >= 0),
  project_id      bigint          NOT NULL REFERENCES projects ON DELETE CASCADE,
  channel_id      bigint          NOT NULL REFERENCES channels ON DELETE CASCADE,
  UNIQUE (channel_id, version_string)
);

CREATE TABLE version_downloads (
  id          serial        NOT NULL PRIMARY KEY,
  cookie      varchar(50)   ,
  user_id     bigint        REFERENCES users ON DELETE CASCADE,
  version_id  bigint        NOT NULL REFERENCES versions ON DELETE CASCADE
);

CREATE TABLE teams (
  id          serial        NOT NULL PRIMARY KEY,
  created_at  timestamp     NOT NULL,
  name        varchar(255)  NOT NULL
);

# --- !Downs

DROP TABLE projects;
DROP TABLE project_views;
DROP TABLE project_stars;
DROP TABLE channels;
DROP TABLE versions;
DROP TABLE version_downloads;
DROP TABLE users;
DROP TABLE teams;
