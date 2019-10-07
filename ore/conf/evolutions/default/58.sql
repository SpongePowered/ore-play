# --- !Ups

drop table if exists teams;
alter table channels rename to project_channels;
alter table flags rename to project_flags;
alter table pages rename to project_pages;
alter table sessions rename to user_sessions;
alter table version_downloads rename to project_version_downloads;
alter table versions rename to project_versions;

# --- !Downs

alter table project_channels rename to channels;
alter table project_flags rename to flags;
alter table project_pages rename to pages;
alter table user_sessions rename to sessions;
alter table project_version_downloads rename to version_downloads;
alter table project_versions rename to versions;
