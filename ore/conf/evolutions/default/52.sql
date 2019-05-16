# --- !Ups

update projects set recommended_version_id = -1 where recommended_version_id is null;
update projects set topic_id = -1 where topic_id is null;
update projects set post_id = -1 where post_id is null;

alter table users add column pgp_pub_key text;

# --- !Downs

alter table users drop column pgp_pub_key;
