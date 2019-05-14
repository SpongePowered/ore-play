# --- !Ups

alter table organizations rename owner_id to user_id;

# --- !Downs

alter table organizations rename user_id to owner_id;
