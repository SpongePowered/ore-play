# --- !Ups

alter table project_versions add column reviewer_id bigint default -1;

# --- !Downs

alter table project_versions drop column reviewer_id;
