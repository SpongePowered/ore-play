# --- !Ups

create table project_logs(
  id          bigserial not null primary key,
  created_at  timestamp not null,
  project_id  bigint    not null references projects on delete cascade
);

create table project_log_entries(
  id              bigserial     not null primary key,
  created_at      timestamp     not null,
  log_id          bigint        not null references project_logs on delete cascade,
  tag             varchar(255)  not null default 'default',
  message         text          not null,
  occurrences     int           not null default 1,
  last_occurrence timestamp     not null
);

# --- !Downs

drop table project_logs;
drop table project_log_entries;
