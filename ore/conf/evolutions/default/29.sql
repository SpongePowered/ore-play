# --- !Ups

alter table project_views add column address inet;
alter table version_downloads add column address inet;

# --- !Downs

alter table project_views drop column address;
alter table version_downloads drop column address;
