# --- !Ups
ALTER TABLE notifications ALTER COLUMN origin_id DROP NOT NULL;

# --- !Downs
DELETE FROM notifications WHERE origin_id IS NULL;
ALTER TABLE notifications ALTER COLUMN origin_id SET NOT NULL;
