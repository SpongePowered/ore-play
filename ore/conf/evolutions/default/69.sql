# --- !Ups

alter table project_version_download_warnings drop constraint project_version_download_warnings_version_id_fkey;
alter table project_version_download_warnings
  add foreign key (version_id) references project_versions on delete cascade;

# --- !Downs
