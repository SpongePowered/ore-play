# --- !Ups

create table project_members (
  project_id  bigint not null references projects on delete cascade,
  user_id     bigint not null references users on delete cascade,
  primary key (project_id, user_id)
);

# --- !Downs

drop table project_members;
