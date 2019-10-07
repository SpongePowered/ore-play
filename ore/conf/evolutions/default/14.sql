# --- !Ups

alter table versions alter column file_size set not null;

# --- !Downs

alter table versions alter column file_size drop not null;
