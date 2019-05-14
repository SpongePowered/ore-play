# --- !Ups

truncate table project_version_unsafe_downloads;
truncate table project_version_download_warnings;

alter table project_version_download_warnings add column address inet not null unique;

# --- !Downs

alter table project_version_download_warnings drop column address;
