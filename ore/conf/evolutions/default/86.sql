# --- !Ups
ALTER TABLE projects ADD COLUMN visibility INT NOT NUll DEFAULT 1;
UPDATE projects SET visibility = 5 WHERE is_visible = false;
ALTER TABLE projects DROP COLUMN is_visible;

# --- !Downs
ALTER TABLE projects ADD COLUMN is_visible BOOLEAN default TRUE;
UPDATE projects SET is_visible = false WHERE visibility != 1;
ALTER TABLE projects DROP COLUMN visibility;
