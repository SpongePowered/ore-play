# --- !Ups

alter table users add column is_locked boolean default false;

# --- !Downs

alter table users drop column is_locked;
