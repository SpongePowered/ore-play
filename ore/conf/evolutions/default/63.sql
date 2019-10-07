# --- !Ups

create table sign_ons(
  id            bigserial     not null primary key,
  created_at    timestamp     not null,
  nonce         varchar(255)  not null unique,
  is_completed  boolean       not null default false
);

# --- !Downs

drop table sign_ons;
