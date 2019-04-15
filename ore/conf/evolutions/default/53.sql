# --- !Ups

alter table users add column last_pgp_pub_key_update timestamp;

# --- !Downs

alter table users drop column last_pgp_pub_key_update;
