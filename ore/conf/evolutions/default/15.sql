# --- !Ups

alter table users alter column email drop not null;

# --- !Downs

alter table users alter column email set not null;
