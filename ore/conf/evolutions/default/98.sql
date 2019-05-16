# --- !Ups

DELETE FROM logged_actions WHERE action_context = 2;

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
   p.slug             as p_slug,
   p.owner_name       as p_owner_name,
   pv.id              as pv_id,
   pv.version_string  as pv_version_string,
   pp.id              as pp_id,
   pp.name            as pp_name,
   pp.slug            as pp_slug,
   s.id               as s_id,
   s.name             as s_name,
   CASE
       WHEN (a.action_context = 0) THEN a.action_context_id         -- Project
       WHEN (a.action_context = 1) THEN COALESCE(pv.project_id, -1) -- Version
       WHEN (a.action_context = 2) THEN a.action_context_id         -- ProjectPage
       ELSE -1 -- Return -1 to allow filtering
   END as filter_project,
   CASE
       WHEN (a.action_context = 1) THEN COALESCE(pv.id, a.action_context_id) -- Version (possible deleted)
       ELSE -1 -- Return -1 to allow filtering correctly
   END as filter_version,
   CASE
       WHEN (a.action_context = 2) THEN COALESCE(pp.id, -1)
       ELSE -1
   END as filter_page,
   CASE
	   WHEN (a.action_context = 3) THEN a.action_context_id		-- User
	   WHEN (a.action_context = 4) THEN a.action_context_id		-- Organization
	   ELSE -1
   END as filter_subject,
   a.action as filter_action
FROM logged_actions a
LEFT OUTER JOIN users u ON a.user_id = u.id
LEFT OUTER JOIN projects p ON
   CASE
       WHEN a.action_context = 0 AND a.action_context_id = p.id THEN 1 -- Join on action
       WHEN a.action_context = 1 AND (SELECT project_id FROM project_versions pvin WHERE pvin.id = a.action_context_id) = p.id THEN 1 -- Query for projectId from Version
       WHEN a.action_context = 2 AND (SELECT project_id FROM project_pages ppin WHERE ppin.id = a.action_context_id) = p.id THEN 1 -- Query for projectId from Page
       ELSE 0
   END = 1
LEFT OUTER JOIN project_versions pv ON (a.action_context = 1 AND a.action_context_id = pv.id)
LEFT OUTER JOIN project_pages pp ON (a.action_context = 2 AND a.action_context_id = pp.id)
LEFT OUTER JOIN users s ON
	CASE
		WHEN a.action_context = 3 AND a.action_context_id = s.id THEN 1
		WHEN a.action_context = 4 AND a.action_context_id = s.id THEN 1
		ELSE 0
	END = 1
;
# --- !Downs

DROP VIEW v_logged_actions;

CREATE VIEW v_logged_actions AS
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
   p.slug             as p_slug,
   p.owner_name       as p_owner_name,
   pv.id              as pv_id,
   pv.version_string  as pv_version_string,
   pp.id              as pp_id,
   pp.name            as pp_name,
   pp.slug            as pp_slug,
   CASE
       WHEN (a.action_context = 0) THEN a.action_context_id         -- Project
       WHEN (a.action_context = 1) THEN COALESCE(pv.project_id, -1) -- Version
       WHEN (a.action_context = 2) THEN a.action_context_id         -- ProjectPage
       ELSE -1 -- Return -1 to allow filtering
   END as filter_project,
   CASE
       WHEN (a.action_context = 1) THEN COALESCE(pv.id, a.action_context_id) -- Version (possible deleted)
       ELSE -1 -- Return -1 to allow filtering correctly
   END as filter_version,
   CASE
       WHEN (a.action_context = 2) THEN COALESCE(pp.id, -1)
       ELSE -1
   END as filter_page
FROM logged_actions a
LEFT OUTER JOIN users u ON a.user_id = u.id
LEFT OUTER JOIN projects p ON
   CASE
       WHEN a.action_context = 0 AND a.action_context_id = p.id THEN 1 -- Join on action
       WHEN a.action_context = 1 AND (SELECT project_id FROM project_versions pvin WHERE pvin.id = a.action_context_id) = p.id THEN 1 -- Query for projectId from Version
       WHEN a.action_context = 2 AND (SELECT project_id FROM project_pages ppin WHERE ppin.id = a.action_context_id) = p.id THEN 1 -- Query for projectId from Page
       ELSE 0
   END = 1
LEFT OUTER JOIN project_versions pv ON (a.action_context = 1 AND a.action_context_id = pv.id)
LEFT OUTER JOIN project_pages pp ON (a.action_context = 2 AND a.action_context_id = pp.id)
;