# --- !Ups

alter table project_views add column created_at timestamp;
alter table version_downloads add column created_at timestamp;

# --- !Downs

alter table project_views drop column created_at;
alter table version_downloads add column created_at;
