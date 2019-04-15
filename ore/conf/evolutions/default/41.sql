# --- !Ups

alter table notifications add column origin_id bigint not null default -1 references users on delete cascade;
alter table notifications alter column origin_id drop default;

# --- !Downs

alter table notifications drop column origin_id;
