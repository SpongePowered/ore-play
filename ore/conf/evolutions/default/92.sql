# --- !Ups

ALTER TABLE user_sessions DROP CONSTRAINT sessions_username_fkey;
ALTER TABLE user_sessions ADD CONSTRAINT sessions_username_fkey FOREIGN KEY (username) REFERENCES users (name) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE projects ADD CONSTRAINT projects_owner_name_fkey FOREIGN KEY (owner_name) REFERENCES users (name) ON UPDATE CASCADE;

# --- !Downs

ALTER TABLE projects DROP CONSTRAINT projects_owner_name_fkey;
