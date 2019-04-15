# --- !Ups

drop table project_views;
create table project_views (
  id          serial        not null primary key,
  created_at  timestamp     ,
  project_id  bigint        not null,
  address     inet          not null,
  cookie      varchar(36)   not null,
  user_id     bigint        ,
  unique (project_id, address),
  unique (project_id, cookie),
  unique (project_id, user_id)
);

drop table version_downloads;
create table version_downloads (
  id          serial        not null primary key,
  created_at  timestamp     ,
  version_id  bigint        not null,
  address     inet          not null,
  cookie      varchar(36)   not null,
  user_id     bigint        ,
  unique (version_id, address),
  unique (version_id, cookie),
  unique (version_id, user_id)
);

# --- !Downs

