# --- !Ups

alter table projects add column last_updated timestamp;

# --- !Downs

alter table projects drop column last_updated;
