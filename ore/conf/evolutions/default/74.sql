# --- !Ups

alter table project_pages add column parent_id bigint not null default -1;

# --- !Downs

alter table project_pages drop column parent_id;
