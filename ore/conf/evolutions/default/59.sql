# --- !Ups

alter table projects drop column homepage;
alter table projects drop column category;
alter table projects drop column issues;
alter table projects drop column source;
alter table projects drop column description;

create table project_settings(
  id            bigserial     not null primary key,
  created_at    timestamp     not null,
  project_id    bigint        unique not null references projects on delete cascade,
  homepage      varchar(255),
  category      int           not null,
  description   varchar(255),
  issues        varchar(255),
  source        varchar(255),
  license_name  varchar(255),
  license_url   varchar(255)
);

# --- !Downs

alter table projects drop column settings_id;
drop table project_settings;

alter table projects add column homepage varchar(255);
alter table projects add column category int not null default 0;
alter table projects alter column category drop default;
alter table projects add column issues varchar(255);
alter table projects add column source varchar(255);
alter table projects add column description varchar(255);