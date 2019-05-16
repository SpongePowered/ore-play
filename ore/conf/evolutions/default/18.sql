# --- !Ups

alter table projects add column topic_id bigint;

# --- !Downs

alter table projects drop column topic_id;
