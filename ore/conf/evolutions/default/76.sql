# --- !Ups

alter table project_channels add column is_non_reviewed boolean not null default false;

# --- !Downs

alter table project_channels drop column is_non_reviewed;
