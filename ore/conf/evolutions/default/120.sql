# --- !Ups

ALTER TABLE project_api_keys DROP COLUMN key_type;

# --- !Downs

ALTER TABLE project_api_keys ADD COLUMN key_type INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE project_api_keys ALTER COLUMN key_type DROP DEFAULT;