# --- !Ups

alter table projects alter column recommended_version_id set default -1;
alter table projects alter column topic_id set default -1;
alter table projects alter column post_id set default -1;

alter table projects drop constraint projects_owner_id_fkey;
alter table projects add CONSTRAINT projects_owner_id_fkey FOREIGN KEY (owner_id) references users on delete restrict;

alter table projects add column is_topic_dirty boolean default false;

create table projects_deleted (
  id                      bigserial primary key not null,
  created_at              timestamp not null,
  name                    varchar(255) not null,
  slug                    varchar(255) not null,
  owner_name              varchar(255) not null,
  homepage                text,
  recommended_version_id  bigint default -1,
  category                int not null,
  views                   bigint not null,
  downloads               bigint not null,
  stars                   bigint not null,
  issues                  text,
  source                  text,
  description             varchar(255),
  owner_id                bigint REFERENCES users on delete restrict,
  topic_id                bigint default -1,
  post_id                 bigint default -1,
  is_topic_dirty          boolean default false,
  is_visible              boolean default true,
  last_updated            timestamp not null default now()
);

# --- !Downs

alter table projects alter column recommended_version_id drop default;
alter table projects alter column topic_id drop default;
alter table projects alter column post_id drop default;

alter table projects drop CONSTRAINT  projects_owner_id_fkey;
alter table projects add CONSTRAINT projects_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users on delete cascade;

alter table projects drop column is_topic_dirty;

drop table projects_deleted;
