# --- !Ups

alter table user_project_roles add column is_accepted boolean not null default false;

# --- !Downs

alter table user_project_roles drop column is_accepted;
