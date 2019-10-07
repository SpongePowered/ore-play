# --- !Ups

CREATE OR REPLACE FUNCTION delete_old_project_views()
  RETURNS trigger AS
$$
BEGIN
  DELETE FROM project_views WHERE created_at < current_date - interval '30' day;;
  RETURN NEW;;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER clean_old_project_views AFTER INSERT
  ON project_views
  FOR EACH STATEMENT
EXECUTE PROCEDURE delete_old_project_views();

# --- !Downs

DROP TRIGGER clean_old_project_views ON project_views;
DROP FUNCTION delete_old_project_views();
