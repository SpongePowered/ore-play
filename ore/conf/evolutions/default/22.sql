# --- !Ups

alter table flags rename column createdat to created_at;

# --- !Downs

alter table flags rename column created_at to createdat;