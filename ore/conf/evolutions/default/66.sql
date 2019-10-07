# --- !Ups

alter table project_version_unsafe_downloads add column download_type int not null default 0;
alter table project_version_unsafe_downloads alter column download_type drop default;

# --- !Downs

alter table project_version_unsafe_downloads drop column download_type;
