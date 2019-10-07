# --- !Ups

alter table versions add column file_name varchar(255) not null default '';
alter table versions alter column file_name drop default;

# --- !Downs

alter table versions drop column file_name;
