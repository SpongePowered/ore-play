# --- !Ups

create table project_watchers (
  project_id  bigint not null,
  user_id     bigint not null,
  unique (project_id, user_id)
);

# --- !Downs

drop table project_watchers;
