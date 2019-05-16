# --- !Ups

create table flags (
  id          serial    not null primary key,
  createdAt   timestamp not null,
  project_id  bigint    not null references projects on delete cascade,
  user_id     bigint    not null references users on delete cascade,
  reason      int       not null,
  is_resolved boolean   not null default false
);

# --- !Downs

drop table flags;
