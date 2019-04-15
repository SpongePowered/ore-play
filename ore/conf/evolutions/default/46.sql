# --- !Ups

create table organizations (
  id           BIGSERIAL          NOT NULL PRIMARY KEY,
  created_at   TIMESTAMP          NOT NULL,
  username     VARCHAR(20)        UNIQUE NOT NULL,
  password     VARCHAR(100)       NOT NULL,
  owner_id     BIGINT             NOT NULL REFERENCES users ON DELETE CASCADE
);

create table organization_members (
  user_id           bigint not null references users on delete cascade,
  organization_id   bigint not null references organizations on delete cascade,
  primary key (user_id, organization_id)
);

create table user_organization_roles (
  id                bigserial   not null primary key,
  created_at        timestamp   not null,
  user_id           bigint      not null references users on delete cascade,
  role_type         int         not null,
  organization_id   bigint      not null references organizations on delete cascade,
  is_accepted       boolean     not null default false
);

# --- !Downs

drop table organization_members;
drop table user_organization_roles;
drop table organizations;
