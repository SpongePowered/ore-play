# --- !Ups

alter table users rename column roles to global_roles;
alter table users rename column external_id to id;

create table user_project_roles (
  id            serial      NOT NULL PRIMARY KEY,
  created_at    timestamp   NOT NULL,
  user_id       bigint      NOT NULL REFERENCES users ON DELETE CASCADE,
  role_type_id  int         NOT NULL,
  project_id    bigint      REFERENCES projects ON DELETE CASCADE,
  UNIQUE (user_id, role_type_id, project_id)
);

# --- !Downs

drop table user_project_roles;
alter table users rename column global_roles to roles;
