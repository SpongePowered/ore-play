# --- !Ups

alter table user_project_roles add column is_visible boolean not null default true;
alter table user_organization_roles add column is_visible boolean not null default true;

# --- !Downs

alter table user_project_roles drop column is_visible;
alter table user_organization_roles drop column is_visible;
