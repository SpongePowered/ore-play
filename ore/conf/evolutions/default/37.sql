# --- !Ups

create table notifications (
  id                  bigint        not null primary key,
  created_at          timestamp     not null,
  user_id             bigint        not null references users on delete cascade,
  notification_type   int           not null,
  message             varchar(255)  not null,
  action              varchar(255)  not null,
  read                boolean       not null default false
);

create table project_invites (
  id          bigint      not null primary key,
  created_at  timestamp   not null,
  project_id  bigint      not null references projects on delete cascade,
  user_id     bigint      not null references users on delete cascade,
  unique (project_id, user_id)
);

# --- !Downs

drop table notifications;
drop table project_invites;
