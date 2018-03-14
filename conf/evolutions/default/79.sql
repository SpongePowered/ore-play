# --- !Ups

alter table project_versions add column author_id bigint default -1;

# --- !Downs

alter table project_versions drop column author_id;
