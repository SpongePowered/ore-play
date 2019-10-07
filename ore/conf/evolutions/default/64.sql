# --- !Ups

create table project_version_download_warnings(
  id bigserial not null primary key,
  created_at timestamp not null,
  expiration timestamp not null,
  token varchar(255) not null,
  version_id bigint not null references project_versions,
  download_id bigint not null default -1
);

create table project_version_unsafe_downloads(
  id bigserial not null primary key,
  created_at timestamp not null,
  user_id bigint not null default -1,
  address inet not null
);

# --- !Downs

drop table project_version_unsafe_downloads;
drop table project_version_download_warnings;
