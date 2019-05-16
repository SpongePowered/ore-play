# --- !Ups

alter table project_version_download_warnings add column is_confirmed boolean not null default false;

# --- !Downs

alter table project_version_download_warnings drop column is_confirmed;
