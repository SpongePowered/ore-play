# --- !Ups

ALTER TABLE users ADD COLUMN tagline varchar(255);

# --- !Downs

ALTER TABLE users DROP COLUMN tagline;
