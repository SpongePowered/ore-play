# --- !Ups

alter table users add column join_date timestamp;

# --- !Downs

alter table users drop column join_date;
