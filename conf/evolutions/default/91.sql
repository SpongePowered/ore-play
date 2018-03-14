# --- !Ups
ALTER TABLE project_settings ADD COLUMN forum_sync BOOLEAN default TRUE;

# --- !Downs
ALTER TABLE project_settings DROP COLUMN forum_sync;
