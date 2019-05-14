# --- !Ups

alter table projects drop column authors;

# --- !Downs

alter table projects add column authors varchar(255)[] not null default '{}';
alter table projects alter column authors drop not null;
