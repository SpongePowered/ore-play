# --- !Ups

alter table project_versions add column approved_at timestamp;

# --- !Downs

alter table project_versions drop column approved_at;
