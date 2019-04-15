# --- !Ups

alter table versions add column mcversion varchar(255) default null;

# --- !Downs

alter table versions drop column mcversion;
