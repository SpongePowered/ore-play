# --- !Ups
ALTER TABLE users
    DROP COLUMN pgp_pub_key,
    DROP COLUMN last_pgp_pub_key_update;

ALTER TABLE project_versions
    DROP COLUMN signature_file_name;

UPDATE users u
SET read_prompts = array_remove(u.read_prompts, 1);

DELETE
FROM project_version_unsafe_downloads ud
WHERE ud.download_type = 2;

DELETE FROM logged_actions la WHERE la.action = 15 OR la.action = 16;

# --- !Downs

ALTER TABLE users
    ADD COLUMN pgp_pub_key TEXT,
    ADD COLUMN last_pgp_pub_key_update TIMESTAMP;

ALTER TABLE project_versions
    ADD COLUMN signature_file_name VARCHAR(255) NOT NULL default 'invalid.sig';

ALTER TABLE project_versions
    ALTER COLUMN signature_file_name DROP DEFAULT;