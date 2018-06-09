# --- !Ups

ALTER TABLE users ADD COLUMN language VARCHAR(16);

ALTER TABLE notifications ADD COLUMN messageArgs VARCHAR(255)[];
UPDATE notifications SET messageArgs = ARRAY[message];

ALTER TABLE notifications DROP COLUMN message;

# --- !Downs

ALTER TABLE users DROP COLUMN language;

ALTER TABLE notifications ADD COLUMN message VARCHAR(255);
UPDATE notifications SET message = messageArgs[0];

ALTER TABLE notifications DROP COLUMN messageArgs;
