# --- !Ups

alter table users add column avatar_url text;

# --- !Downs

alter table users drop column avatar_url;
