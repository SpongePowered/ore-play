# --- !Ups

alter table project_flags add column comment varchar(255);

# --- !Downs

alter table project_flags drop column comment;
