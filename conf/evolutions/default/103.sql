# --- !Ups
ALTER TABLE users DROP COLUMN avatar_url;
# --- !Downs
ALTER TABLE users ADD COLUMN avatar_url text;
