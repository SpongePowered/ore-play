# --- !Ups

alter table projects rename column category_id to category;
alter table channels rename column color_id to color;
alter table user_project_roles rename column role_type_id to role_type;

# --- !Downs

alter table projects rename column category to category_id;
alter table channels rename column color to color_id;
alter table user_project_roles rename column role_type to role_type_id;
