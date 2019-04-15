# --- !Ups

alter table project_versions drop column mcversion;

# --- !Downs

alter table project_versions add column mcversion varchar(255) default null;
