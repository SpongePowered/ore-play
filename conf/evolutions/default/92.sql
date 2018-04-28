# --- !Ups

ALTER TABLE user_sessions DROP CONSTRAINT sessions_username_fkey;
ALTER TABLE user_sessions ADD CONSTRAINT sessions_username_fkey FOREIGN KEY (username) REFERENCES users (name) ON DELETE CASCADE ON UPDATE CASCADE;

CREATE OR REPLACE FUNCTION cascade_username_update()
  RETURNS trigger AS
$$
BEGIN
  UPDATE projects SET owner_name=NEW.name WHERE owner_id=NEW.id;;
  RETURN NEW;;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER cascade_username_update_user AFTER UPDATE OF name
  ON users
  FOR EACH ROW
EXECUTE PROCEDURE cascade_username_update();


# --- !Downs

DROP TRIGGER cascade_username_update_user ON users;
DROP FUNCTION cascade_username_update();
