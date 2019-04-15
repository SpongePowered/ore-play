# --- !Ups

ALTER TABLE users ADD COLUMN roles int[] NOT NULL DEFAULT '{}';

# --- !Downs

ALTER TABLE users DROP COLUMN roles;
