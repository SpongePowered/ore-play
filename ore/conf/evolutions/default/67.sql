# --- !Ups

alter table project_version_unsafe_downloads alter column user_id drop not null;

# --- !Downs

alter table project_version_unsafe_downloads alter column user_id set not null;
