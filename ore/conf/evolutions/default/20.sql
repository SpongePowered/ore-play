# --- !Ups

alter table versions add column hash varchar(32) not null default '';
alter table versions alter column hash drop default;

# --- !Downs

alter table versions drop column hash;
