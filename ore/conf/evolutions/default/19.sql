# --- !Ups

alter table projects add column post_id bigint;

# --- !Downs

alter table projects drop column post_id;
