# --- !Ups

alter table project_versions add column signature_file_name varchar(255) not null default '';
alter table project_versions alter column signature_file_name drop default;

# --- !Downs

alter table project_versions drop column signature_file_name;
