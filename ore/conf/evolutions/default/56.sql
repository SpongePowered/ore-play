# --- !Ups

alter table organizations drop column password;

# --- !Downs

alter table organizations add column password varchar(255) not null default '';
alter table organizations alter column password drop default;
