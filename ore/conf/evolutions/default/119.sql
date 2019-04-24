# --- !Ups
ALTER TABLE notifications ALTER COLUMN origin_id DROP NOT NULL;

# --- !Downs
ALTER TABLE notifications ALTER COLUMN origin_id SET NOT NULL;
