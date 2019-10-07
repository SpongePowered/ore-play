# --- !Ups

alter table projects add column owner_id bigint NOT NULL DEFAULT 0 REFERENCES users ON DELETE CASCADE;
alter table projects alter column owner_id drop default;

update users set roles = '{}';

# --- !Downs

alter table projects drop column owner_id;
