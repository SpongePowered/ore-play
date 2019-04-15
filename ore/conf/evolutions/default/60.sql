# --- !Ups

alter table project_settings drop column category;
alter table project_settings drop column description;

alter table projects add column category int not null default 0;
alter table projects alter column category drop default;
alter table projects add column description varchar(255);

# --- !Downs

alter table projects drop column description;
alter table projects drop column category;

alter table project_settings add column description varchar(255);
alter table project_settings add column category int not null default 0;
alter table project_settings alter column category drop default;
