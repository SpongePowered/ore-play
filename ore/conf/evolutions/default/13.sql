# --- !Ups

alter table versions add column file_size bigint not null check (file_size > 0) default 1;
alter table versions alter column file_size drop not null;

# --- !Downs

alter table versions drop column file_size;
