# --- !Ups

DROP VIEW v_logged_actions;

CREATE VIEW v_logged_actions
   AS
SELECT
   a.id,
   a.created_at,
   a.user_id,
   a.address,
   a.action,
   a.action_context,
   a.action_context_id,
   a.new_state,
   a.old_state,
   u.id               as u_id,
   u.name             as u_name,
   p.id               as p_id,
   p.plugin_id        as p_plugin_id,
   p.name             as p_name,
   p.slug             as p_slug,
   p.owner_name       as p_owner_name,
   pv.id              as pv_id,
   pv.version_string  as pv_version_string,
   pv.project_id      as pv_project_id,
   pv.file_name       as pv_file_name,
   CASE
       WHEN (a.action_context = 0) THEN a.action_context_id         -- Project
       WHEN (a.action_context = 1) THEN coalesce(pv.project_id, -1) -- Version
       WHEN (a.action_context = 2) THEN a.action_context_id         -- ProjectPage
       ELSE -1 -- Return -1 to allow filtering
   END as filter_project,
   CASE
       WHEN (a.action_context = 1) THEN COALESCE(pv.id, a.action_context_id) -- Version (possible deleted)
       ELSE -1 -- Return -1 to allow filtering correctly
   END as filter_version
FROM logged_actions a
LEFT OUTER JOIN users u ON a.user_id = u.id
LEFT OUTER JOIN projects p ON
   CASE
       WHEN a.action_context IN (0, 2) AND a.action_context_id = p.id THEN 1 -- Join on action
       WHEN a.action_context IN (1) AND (SELECT project_id FROM project_versions pvin WHERE pvin.id = a.action_context_id) = p.id THEN 1 -- Query for projectId from Version
   ELSE 0
   END = 1
LEFT OUTER JOIN project_versions pv ON
   CASE
       WHEN a.action_context IN (1) AND a.action_context_id = pv.id THEN 1
       ELSE 0
   END = 1
;
# --- !Downs

DROP VIEW v_logged_actions;

CREATE VIEW v_logged_actions AS
  SELECT id, created_at, user_id, address, action, action_context, action_context_id, new_state, old_state,
    CASE WHEN action_context = 0 THEN action_context_id
    WHEN action_context = 1 THEN (SELECT project_id FROM project_versions WHERE ID = action_context_id)
    WHEN action_context = 2 THEN (SELECT project_id FROM project_pages WHERE ID = action_context_id)
    ELSE NULL
    END as project_id,
    CASE WHEN action_context = 1 THEN action_context_id
    ELSE NULL
    END as version_id,
    CASE WHEN action_context = 2 THEN action_context_id
    ELSE NULL
    END as page_id
  FROM logged_actions
;