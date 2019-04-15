# --- !Ups

ALTER TABLE users ADD COLUMN language VARCHAR(16);

ALTER TABLE notifications ADD COLUMN message_args VARCHAR(255)[];
UPDATE notifications SET message_args = ARRAY[message];

ALTER TABLE notifications DROP COLUMN message;

# --- !Downs

ALTER TABLE users DROP COLUMN language;

ALTER TABLE notifications ADD COLUMN message VARCHAR(255);
UPDATE notifications SET message = message_args[0];

ALTER TABLE notifications DROP COLUMN message_args;
