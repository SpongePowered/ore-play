# --- !Ups

alter table sign_ons rename to user_sign_ons;

# --- !Downs

alter table user_sign_ons rename to sign_ons;
