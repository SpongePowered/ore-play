# --- !Ups

alter table users add unique (name);
alter table users add unique (email);

create table sessions (
  id bigserial not null primary key,
  created_at timestamp not null,
  expiration timestamp not null,
  username varchar(255) not null references users(name) on delete cascade,
  token varchar(255) not null
);

# --- !Downs

drop table sessions;
