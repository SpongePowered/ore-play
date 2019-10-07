# --- !Ups

alter table projects add column is_reviewed boolean not null default false;

# --- !Downs

alter table projects drop column is_reviewed;
