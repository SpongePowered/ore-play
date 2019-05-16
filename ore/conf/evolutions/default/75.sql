# --- !Ups

create table project_api_keys(
  id bigserial not null primary key,
  created_at timestamp not null,
  project_id bigint references projects on delete cascade,
  key_type int not null,
  value varchar(255) not null
);

# --- !Downs

drop table project_api_keys;
