# --- !Ups
ALTER TABlE project_pages DROP CONSTRAINT IF EXISTS pages_project_id_name_key;
ALTER TABlE project_pages DROP CONSTRAINT IF EXISTS pages_project_id_slug_key;

# --- !Downs
DELETE FROM project_pages WHERE (project_id, slug) IN
                                (SELECT project_id, slug FROM project_pages GROUP BY project_id, slug HAVING COUNT(*) > 1);

DELETE FROM project_pages WHERE (project_id, name) IN
                                (SELECT project_id, name FROM project_pages GROUP BY project_id, name HAVING COUNT(*) > 1);

ALTER TABlE project_pages ADD CONSTRAINT pages_project_id_name_key UNIQUE(project_id, name);
ALTER TABlE project_pages ADD CONSTRAINT pages_project_id_slug_key UNIQUE(project_id, slug);
