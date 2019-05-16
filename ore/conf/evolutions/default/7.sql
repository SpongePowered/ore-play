# --- !Ups

ALTER TABLE projects ADD COLUMN description varchar(255);
UPDATE projects SET description = 'Hello, world!';

# --- !Downs

ALTER TABLE projects DROP COLUMN description;
UPDATE projects SET description = NULL;
