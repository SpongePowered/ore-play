# --- !Ups

drop table notifications;

create table notifications (
  id                  bigserial     not null primary key,
  created_at          timestamp     not null,
  user_id             bigint        not null references users on delete cascade,
  notification_type   int           not null,
  message             varchar(255)  not null,
  action              varchar(255)  not null,
  read                boolean       not null default false
);
