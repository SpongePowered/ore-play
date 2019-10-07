# --- !Ups

alter table projects add column is_visible boolean not null default true;

# -- !Downs

alter table projects drop column is_visible;
