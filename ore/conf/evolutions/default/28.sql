# --- !Ups

alter table projects drop column is_reviewed;
alter table versions add column is_reviewed boolean not null default false;

# --- !Downs

alter table versions drop column is_reviewed;
alter table projects add column is_reviewed boolean not null default false;
