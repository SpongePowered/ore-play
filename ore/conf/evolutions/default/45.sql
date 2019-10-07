# --- !Ups

alter table projects drop column last_updated;
alter table projects add column last_updated timestamp not null default now();
