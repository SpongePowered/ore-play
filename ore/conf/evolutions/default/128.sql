# --- !Ups

DROP VIEW v_logged_actions;
DROP MATERIALIZED VIEW home_projects;

ALTER TABLE api_keys
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    DROP CONSTRAINT api_keys_owner_id_fkey,
    ADD CONSTRAINT api_keys_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users ON DELETE CASCADE;

ALTER TABLE api_sessions
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires TYPE TIMESTAMPTZ USING expires AT TIME ZONE 'UTC',
    DROP CONSTRAINT api_sessions_user_id_fkey,
    ADD CONSTRAINT api_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users ON DELETE CASCADE;

--We alter logged_actions here just to get correct timestamp when we partition it later
ALTER TABLE logged_actions
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE notifications
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    DROP CONSTRAINT notifications_origin_id_fkey,
    ADD CONSTRAINT notifications_origin_id_fkey FOREIGN KEY (origin_id) REFERENCES users ON DELETE SET NULL;

ALTER TABLE organizations
    RENAME COLUMN user_id TO owner_id;

ALTER TABLE organizations
    ADD COLUMN user_id BIGINT REFERENCES users ON DELETE CASCADE;

UPDATE organizations o
SET user_id = u.id
FROM users u
WHERE o.name = u.name;

ALTER TABLE organizations
    ADD CONSTRAINT organizations_name_fkey FOREIGN KEY (name) REFERENCES users (name) ON UPDATE CASCADE,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE organizations
    RENAME CONSTRAINT organizations_username_key TO organizations_name_key;

ALTER TABLE project_api_keys
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE project_channels
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE project_flags
    ALTER COLUMN comment TYPE TEXT,
    ADD CONSTRAINT project_flags_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users ON DELETE SET NULL,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN resolved_at TYPE TIMESTAMPTZ USING resolved_at AT TIME ZONE 'UTC';

UPDATE project_pages p
SET parent_id = NULL
WHERE p.parent_id IS NOT NULL
  AND NOT EXISTS(SELECT * FROM project_pages pp WHERE pp.id = p.parent_id);

ALTER TABLE project_pages
    ADD CONSTRAINT project_pages_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES project_pages ON DELETE SET NULL,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

DELETE
FROM project_version_download_warnings pvdw
WHERE pvdw.download_id IS NOT NULL
  AND NOT EXISTS(SELECT * FROM project_version_downloads pvd WHERE pvd.id = pvdw.download_id);

ALTER TABLE project_version_download_warnings
    ADD CONSTRAINT project_version_download_warnings_download_id_fkey FOREIGN KEY (download_id) REFERENCES project_version_downloads ON DELETE CASCADE,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN expiration TYPE TIMESTAMPTZ USING expiration AT TIME ZONE 'UTC';

DELETE
FROM project_version_downloads pvd
WHERE NOT EXISTS(SELECT * FROM project_versions pv WHERE pv.id = pvd.version_id);

ALTER TABLE project_version_downloads
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ADD CONSTRAINT project_version_downloads_version_id_fkey FOREIGN KEY (version_id) REFERENCES project_versions ON DELETE CASCADE,
    ADD CONSTRAINT project_version_downloads_user_id_fkey FOREIGN KEY (user_id) REFERENCES users ON DELETE CASCADE;

ALTER TABLE project_version_reviews
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN ended_at TYPE TIMESTAMPTZ USING ended_at AT TIME ZONE 'UTC';

UPDATE project_version_unsafe_downloads
SET user_id = NULL
WHERE user_id = -1;

ALTER TABLE project_version_unsafe_downloads
    ADD CONSTRAINT project_version_unsafe_downloads_fkey FOREIGN KEY (user_id) REFERENCES users ON DELETE CASCADE,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN user_id DROP DEFAULT;

ALTER TABLE project_version_visibility_changes
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN resolved_at TYPE TIMESTAMPTZ USING resolved_at AT TIME ZONE 'UTC',
    DROP CONSTRAINT project_version_visibility_changes_created_by_fkey,
    ADD CONSTRAINT project_version_visibility_changes_created_by_fkey FOREIGN KEY (created_by) REFERENCES users ON DELETE CASCADE,
    DROP CONSTRAINT project_version_visibility_changes_version_id_fkey,
    ADD CONSTRAINT project_version_visibility_changes_version_id_fkey FOREIGN KEY (version_id) REFERENCES project_versions ON DELETE CASCADE,
    DROP CONSTRAINT project_version_visibility_changes_resolved_by_fkey,
    ADD CONSTRAINT project_version_visibility_changes_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users ON DELETE CASCADE;

ALTER TABLE project_versions
    ALTER COLUMN author_id DROP NOT NULL,
    DROP CONSTRAINT versions_project_id_fkey,
    ADD CONSTRAINT versions_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects ON DELETE CASCADE;

UPDATE project_versions pv
SET author_id = NULL
WHERE pv.author_id = -1;

ALTER TABLE project_versions
    ADD CONSTRAINT project_versions_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES users ON DELETE SET NULL,
    ADD CONSTRAINT project_versions_author_id_fkey FOREIGN KEY (author_id) REFERENCES users ON DELETE SET NULL,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN approved_at TYPE TIMESTAMPTZ USING approved_at AT TIME ZONE 'UTC';

ALTER TABLE project_views
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ADD CONSTRAINT project_views_user_id_fkey FOREIGN KEY (user_id) REFERENCES users ON DELETE SET NULL;

ALTER TABLE project_visibility_changes
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN resolved_at TYPE TIMESTAMPTZ USING resolved_at AT TIME ZONE 'UTC',
    DROP CONSTRAINT project_visibility_changes_created_by_fkey,
    ADD CONSTRAINT project_visibility_changes_created_by_fkey FOREIGN KEY (created_by) REFERENCES users ON DELETE CASCADE,
    DROP CONSTRAINT project_visibility_changes_resolved_by_fkey,
    ADD CONSTRAINT project_visibility_changes_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users ON DELETE CASCADE;

DELETE
FROM project_watchers pw
WHERE NOT EXISTS(SELECT * FROM projects p WHERE p.id = pw.project_id);

ALTER TABLE project_watchers
    ADD CONSTRAINT project_watchers_pkey PRIMARY KEY (project_id, user_id),
    ADD CONSTRAINT project_watchers_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects ON DELETE CASCADE,
    ADD CONSTRAINT project_watchers_user_id_fkey FOREIGN KEY (user_id) REFERENCES users ON DELETE CASCADE;

ALTER TABLE user_organization_roles
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE user_project_roles
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE user_sessions
    ADD COLUMN user_id BIGINT REFERENCES users ON DELETE CASCADE;

UPDATE user_sessions us
SET user_id = u.id
FROM users u
WHERE us.username = u.name;

ALTER TABLE user_sessions
    ALTER COLUMN user_id SET NOT NULL,
    DROP COLUMN username,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN expiration TYPE TIMESTAMPTZ USING expiration AT TIME ZONE 'UTC';

ALTER TABLE user_sign_ons
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN join_date TYPE TIMESTAMPTZ USING join_date AT TIME ZONE 'UTC';

UPDATE projects
SET recommended_version_id = NULL
WHERE recommended_version_id = -1;

ALTER TABLE projects
    DROP COLUMN stars,
    DROP COLUMN last_updated,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ADD COLUMN homepage VARCHAR(255),
    ADD COLUMN issues VARCHAR(255),
    ADD COLUMN source VARCHAR(255),
    ADD COLUMN support VARCHAR(255),
    ADD COLUMN license_name VARCHAR(255),
    ADD COLUMN license_url VARCHAR(255),
    ADD COLUMN forum_sync BOOLEAN DEFAULT TRUE NOT NULL,
    DROP CONSTRAINT projects_owner_id_fkey,
    ADD CONSTRAINT projects_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users ON DELETE CASCADE,
    ALTER COLUMN recommended_version_id DROP DEFAULT,
    ADD CONSTRAINT projects_recommended_version_id_fkey FOREIGN KEY (recommended_version_id) REFERENCES project_versions ON DELETE SET NULL;

CREATE FUNCTION update_project_name_trigger() RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    UPDATE projects p SET name = u.name FROM users u WHERE p.id = new.id AND u.id = new.owner_id;;
END;;
$$;

CREATE TRIGGER project_owner_name_updater
    AFTER UPDATE OF owner_id
    ON projects
    FOR EACH ROW
    WHEN (old.owner_id != new.owner_id)
EXECUTE FUNCTION update_project_name_trigger();

UPDATE projects p
SET homepage     = ps.homepage,
    issues       = ps.issues,
    source       = ps.source,
    support      = ps.support,
    license_name = ps.license_name,
    license_url  = ps.license_url,
    forum_sync   = ps.forum_sync
FROM project_settings ps
WHERE ps.project_id = p.id;

DROP TABLE project_settings;

CREATE TYPE LOGGED_ACTION_TYPE AS ENUM (
    'project_visibility_change',
    'project_renamed',
    'project_flagged',
    'project_settings_changed',
    'project_member_removed',
    'project_icon_changed',
    'project_page_edited',
    'project_flag_resolved',
    'version_deleted',
    'version_uploaded', --No recommended version changed as we're phasing them out
    'version_description_changed',
    'version_review_state_changed',
    'user_tagline_changed'
    );

CREATE FUNCTION logged_action_type_from_int(id int) RETURNS LOGGED_ACTION_TYPE
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
    LANGUAGE plpgsql AS
$$
BEGIN
    CASE id
        WHEN 0 THEN RETURN 'project_visibility_change';;
        WHEN 2 THEN RETURN 'project_renamed';;
        WHEN 3 THEN RETURN 'project_flagged';;
        WHEN 4 THEN RETURN 'project_settings_changed';;
        WHEN 5 THEN RETURN 'project_member_removed';;
        WHEN 6 THEN RETURN 'project_icon_changed';;
        WHEN 7 THEN RETURN 'project_page_edited';;
        WHEN 13 THEN RETURN 'project_flag_resolved';;
        WHEN 8 THEN RETURN 'version_deleted';;
        WHEN 9 THEN RETURN 'version_uploaded';;
        WHEN 12 THEN RETURN 'version_description_changed';;
        WHEN 17 THEN RETURN 'version_review_state_changed';;
        WHEN 14 THEN RETURN 'user_tagline_changed';;
        ELSE
        END CASE;;

    RETURN NULL;;
END;;
$$;

DELETE
FROM logged_actions
WHERE logged_action_type_from_int(action) IS NULL;

CREATE TABLE logged_actions_project
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ        NOT NULL,
    user_id    BIGINT             REFERENCES users ON DELETE SET NULL,
    address    INET               NOT NULL,
    action     LOGGED_ACTION_TYPE NOT NULL,
    project_id BIGINT             REFERENCES projects ON DELETE SET NULL,
    new_state  TEXT               NOT NULL,
    old_state  TEXT               NOT NULL
);

DELETE
FROM logged_actions a
WHERE action_context = 0
  AND NOT EXISTS(SELECT * FROM projects p WHERE p.id = a.action_context_id);

INSERT INTO logged_actions_project (created_at, user_id, address, action, project_id, new_state, old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_from_int(a.action),
       a.action_context_id,
       a.new_state,
       a.old_state
FROM logged_actions a
WHERE a.action_context = 0;

CREATE TABLE logged_actions_version
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ        NOT NULL,
    user_id    BIGINT             REFERENCES users ON DELETE SET NULL,
    address    INET               NOT NULL,
    action     LOGGED_ACTION_TYPE NOT NULL,
    project_id BIGINT             REFERENCES projects ON DELETE SET NULL,
    version_id BIGINT             REFERENCES project_versions ON DELETE SET NULL,
    new_state  TEXT               NOT NULL,
    old_state  TEXT               NOT NULL
);

INSERT INTO logged_actions_version (created_at, user_id, address, action, project_id, version_id, new_state, old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_from_int(a.action),
       pv.project_id,
       a.action_context_id,
       a.new_state,
       a.old_state
FROM logged_actions a
         JOIN project_versions pv ON a.action_context_id = pv.id
WHERE a.action_context = 1
  AND a.action != 11; --We don't care about recommended version changes

CREATE TABLE logged_actions_page
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ        NOT NULL,
    user_id    BIGINT             REFERENCES users ON DELETE SET NULL,
    address    INET               NOT NULL,
    action     LOGGED_ACTION_TYPE NOT NULL,
    project_id BIGINT             REFERENCES projects ON DELETE SET NULL,
    page_id    BIGINT             REFERENCES project_pages ON DELETE SET NULL,
    new_state  TEXT               NOT NULL,
    old_state  TEXT               NOT NULL
);

INSERT INTO logged_actions_page (created_at, user_id, address, action, project_id, page_id, new_state, old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_from_int(a.action),
       pp.project_id,
       a.action_context_id,
       a.new_state,
       a.old_state
FROM logged_actions a
         JOIN project_pages pp ON a.action_context_id = pp.id
WHERE a.action_context = 2;

CREATE TABLE logged_actions_user
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ        NOT NULL,
    user_id    BIGINT             REFERENCES users ON DELETE SET NULL,
    address    INET               NOT NULL,
    action     LOGGED_ACTION_TYPE NOT NULL,
    subject_id BIGINT             REFERENCES users ON DELETE SET NULL,
    new_state  TEXT               NOT NULL,
    old_state  TEXT               NOT NULL
);

INSERT INTO logged_actions_user (created_at, user_id, address, action, subject_id, new_state, old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_from_int(a.action),
       a.action_context_id,
       a.new_state,
       a.old_state
FROM logged_actions a
WHERE a.action_context = 3;

CREATE TABLE logged_actions_organization
(
    id              BIGSERIAL PRIMARY KEY,
    created_at      TIMESTAMPTZ        NOT NULL,
    user_id         BIGINT             REFERENCES users ON DELETE SET NULL,
    address         INET               NOT NULL,
    action          LOGGED_ACTION_TYPE NOT NULL,
    organization_id BIGINT             REFERENCES organizations ON DELETE SET NULL,
    new_state       TEXT               NOT NULL,
    old_state       TEXT               NOT NULL
);

INSERT INTO logged_actions_organization (created_at, user_id, address, action, organization_id, new_state, old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_from_int(a.action),
       a.action_context_id,
       a.new_state,
       a.old_state
FROM logged_actions a
WHERE a.action_context = 4;

DROP TABLE logged_actions;

CREATE VIEW v_logged_actions AS
    SELECT a.id,
           a.created_at,
           a.user_id,
           u.name             as user_name,
           a.address,
           a.action,
           0                  AS context_type,
           a.new_state,
           a.old_state,
           p.id               AS p_id,
           p.plugin_id        AS p_plugin_id,
           p.slug             AS p_slug,
           ou.name            AS p_owner_name,
           NULL::BIGINT       AS pv_id,
           NULL::VARCHAR(255) AS pv_version_string,
           NULL::BIGINT       AS pp_id,
           NULL::VARCHAR(255) AS pp_name,
           NULL::VARCHAR(255) AS pp_slug,
           NULL::BIGINT       AS s_id,
           NULL::VARCHAR(255) AS s_name
    FROM logged_actions_project a
             LEFT JOIN users u ON a.user_id = u.id
             LEFT JOIN projects p ON a.project_id = p.id
             LEFT JOIN users ou ON p.owner_id = ou.id
    UNION ALL
    SELECT a.id,
           a.created_at,
           a.user_id,
           u.name,
           a.address,
           a.action,
           1,
           a.new_state,
           a.old_state,
           p.id,
           p.plugin_id,
           p.slug,
           ou.name AS p_owner_name,
           pv.id,
           pv.version_string,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL
    FROM logged_actions_version a
             LEFT JOIN users u ON a.user_id = u.id
             LEFT JOIN project_versions pv ON a.version_id = pv.id
             LEFT JOIN projects p ON a.project_id = p.id
             LEFT JOIN users ou ON p.owner_id = ou.id
    UNION ALL
    SELECT a.id,
           a.created_at,
           a.user_id,
           u.name,
           a.address,
           a.action,
           2,
           a.new_state,
           a.old_state,
           p.id,
           p.plugin_id,
           p.slug,
           ou.name AS p_owner_name,
           NULL,
           NULL,
           pp.id,
           pp.name,
           pp.slug,
           NULL,
           NULL
    FROM logged_actions_page a
             LEFT JOIN users u ON a.user_id = u.id
             LEFT JOIN project_pages pp ON a.page_id = pp.id
             LEFT JOIN projects p ON a.project_id = p.id
             LEFT JOIN users ou ON p.owner_id = ou.id
    UNION ALL
    SELECT a.id,
           a.created_at,
           a.user_id,
           u.name,
           a.address,
           a.action,
           3,
           a.new_state,
           a.old_state,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           s.id,
           s.name
    FROM logged_actions_user a
             LEFT JOIN users u ON a.user_id = u.id
             LEFT JOIN users s ON a.subject_id = s.id
    UNION ALL
    SELECT a.id,
           a.created_at,
           a.user_id,
           u.name,
           a.address,
           a.action,
           4,
           a.new_state,
           a.old_state,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           NULL,
           s.id,
           s.name
    FROM logged_actions_organization a
             LEFT JOIN organizations o ON a.organization_id = o.id
             LEFT JOIN users u ON a.user_id = u.id
             LEFT JOIN users s ON o.user_id = s.id;

CREATE MATERIALIZED VIEW home_projects AS
    WITH tags AS (
        SELECT sq.project_id, sq.version_string, sq.tag_name, sq.tag_version, sq.tag_color
        FROM (SELECT pv.project_id,
                     pv.version_string,
                     pvt.name                                                                            AS tag_name,
                     pvt.data                                                                            AS tag_version,
                     pvt.platform_version,
                     pvt.color                                                                           AS tag_color,
                     row_number()
                     OVER (PARTITION BY pv.project_id, pvt.platform_version ORDER BY pv.created_at DESC) AS row_num
              FROM project_versions pv
                       JOIN (
                  SELECT pvti.version_id,
                         pvti.name,
                         pvti.data,
                         --TODO, use a STORED column in Postgres 12
                         CASE
                             WHEN pvti.name = 'Sponge'
                                 THEN substring(pvti.data FROM
                                                '^\[?(\d+)\.\d+(?:\.\d+)?(?:-SNAPSHOT)?(?:-[a-z0-9]{7,9})?(?:,(?:\d+\.\d+(?:\.\d+)?)?\))?$')
                             WHEN pvti.name = 'SpongeForge'
                                 THEN substring(pvti.data FROM
                                                '^\d+\.\d+\.\d+-\d+-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$')
                             WHEN pvti.name = 'SpongeVanilla'
                                 THEN substring(pvti.data FROM
                                                '^\d+\.\d+\.\d+-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$')
                             WHEN pvti.name = 'Forge'
                                 THEN substring(pvti.data FROM '^\d+\.(\d+)\.\d+(?:\.\d+)?$')
                             WHEN pvti.name = 'Lantern'
                                 THEN NULL --TODO Change this once Lantern changes to SpongeVanilla's format
                             ELSE NULL
                             END AS platform_version,
                         pvti.color
                  FROM project_version_tags pvti
                  WHERE pvti.name IN ('Sponge', 'SpongeForge', 'SpongeVanilla', 'Forge', 'Lantern')
                    AND pvti.data IS NOT NULL
              ) pvt ON pv.id = pvt.version_id
              WHERE pv.visibility = 1
                AND pvt.name IN
                    ('Sponge', 'SpongeForge', 'SpongeVanilla', 'Forge', 'Lantern')
                AND pvt.platform_version IS NOT NULL) sq
        WHERE sq.row_num = 1
        ORDER BY sq.platform_version DESC)
    SELECT p.id,
           p.owner_name                   AS owner_name,
           array_agg(DISTINCT pm.user_id) AS project_members,
           p.slug,
           p.visibility,
           p.views,
           p.downloads,
           coalesce(ps.stars, 0)          AS stars,
           coalesce(pw.watchers, 0)       AS watchers,
           p.category,
           p.description,
           p.name,
           p.plugin_id,
           p.created_at,
           max(lv.created_at)             AS last_updated,
           to_jsonb(
                   ARRAY(SELECT jsonb_build_object('version_string', tags.version_string, 'tag_name',
                                                   tags.tag_name,
                                                   'tag_version', tags.tag_version, 'tag_color',
                                                   tags.tag_color)
                         FROM tags
                         WHERE tags.project_id = p.id
                         LIMIT 5))        AS promoted_versions,
           setweight(to_tsvector('english', p.name) ||
                     to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')) ||
                     to_tsvector('english', p.plugin_id), 'A') ||
           setweight(to_tsvector('english', p.description), 'B') ||
           setweight(to_tsvector('english', array_to_string(p.keywords, ' ')), 'C') ||
           setweight(to_tsvector('english', p.owner_name) ||
                     to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                     'D')                 AS search_words
    FROM projects p
             LEFT JOIN project_versions lv ON p.id = lv.project_id
             JOIN project_members_all pm ON p.id = pm.id
             LEFT JOIN (SELECT p.id, COUNT(*) AS stars
                        FROM projects p
                                 LEFT JOIN project_stars ps ON p.id = ps.project_id
                        GROUP BY p.id) ps ON p.id = ps.id
             LEFT JOIN (SELECT p.id, COUNT(*) AS watchers
                        FROM projects p
                                 LEFT JOIN project_watchers pw ON p.id = pw.project_id
                        GROUP BY p.id) pw ON p.id = pw.id
    GROUP BY p.id, ps.stars, pw.watchers;

CREATE INDEX home_projects_downloads_idx ON home_projects (downloads);
CREATE INDEX home_projects_stars_idx ON home_projects (stars);
CREATE INDEX home_projects_views_idx ON home_projects (views);
CREATE INDEX home_projects_created_at_idx ON home_projects (created_at);
CREATE INDEX home_projects_last_updated_idx ON home_projects (last_updated);
CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);

# --- !Downs

DROP VIEW v_logged_actions;
DROP MATERIALIZED VIEW home_projects;

ALTER TABLE api_keys
    ALTER COLUMN created_at TYPE TIMESTAMP,
    DROP CONSTRAINT api_keys_owner_id_fkey,
    ADD CONSTRAINT api_keys_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users;

ALTER TABLE api_sessions
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN expires TYPE TIMESTAMP,
    DROP CONSTRAINT api_sessions_user_id_fkey,
    ADD CONSTRAINT api_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users;

ALTER TABLE notifications
    ALTER COLUMN created_at TYPE TIMESTAMP,
    DROP CONSTRAINT notifications_origin_id_fkey,
    ADD CONSTRAINT notifications_origin_id_fkey FOREIGN KEY (origin_id) REFERENCES users ON DELETE CASCADE;

ALTER TABLE organizations
    RENAME CONSTRAINT organizations_name_key TO organizations_username_key;

ALTER TABLE organizations
    ALTER COLUMN created_at TYPE TIMESTAMP,
    DROP COLUMN user_id,
    DROP CONSTRAINT organizations_name_fkey;

ALTER TABLE organizations
    RENAME COLUMN owner_id TO user_id;

ALTER TABLE project_api_keys
    ALTER COLUMN created_at TYPE TIMESTAMP;

ALTER TABLE project_channels
    ALTER COLUMN created_at TYPE TIMESTAMP;

ALTER TABLE project_flags
    ALTER COLUMN comment TYPE VARCHAR(255),
    DROP CONSTRAINT project_flags_resolved_by_fkey,
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN resolved_at TYPE TIMESTAMP;

ALTER TABLE project_pages
    DROP CONSTRAINT project_pages_parent_id_fkey,
    ALTER COLUMN created_at TYPE TIMESTAMP;

ALTER TABLE project_version_download_warnings
    DROP CONSTRAINT project_version_download_warnings_download_id_fkey,
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN expiration TYPE TIMESTAMP;

ALTER TABLE project_version_downloads
    ALTER COLUMN created_at TYPE TIMESTAMP,
    DROP CONSTRAINT project_version_downloads_version_id_fkey,
    DROP CONSTRAINT project_version_downloads_user_id_fkey;

ALTER TABLE project_version_reviews
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN ended_at TYPE TIMESTAMP;

ALTER TABLE project_version_unsafe_downloads
    DROP CONSTRAINT project_version_unsafe_downloads_fkey,
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN user_id SET DEFAULT -1::INTEGER;

ALTER TABLE project_version_visibility_changes
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN resolved_at TYPE TIMESTAMP,
    DROP CONSTRAINT project_version_visibility_changes_created_by_fkey,
    ADD CONSTRAINT project_version_visibility_changes_created_by_fkey FOREIGN KEY (created_by) REFERENCES users,
    DROP CONSTRAINT project_version_visibility_changes_version_id_fkey,
    ADD CONSTRAINT project_version_visibility_changes_version_id_fkey FOREIGN KEY (version_id) REFERENCES project_versions,
    DROP CONSTRAINT project_version_visibility_changes_resolved_by_fkey,
    ADD CONSTRAINT project_version_visibility_changes_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users;

UPDATE project_version_unsafe_downloads
SET user_id = -1
WHERE user_id IS NULL;

ALTER TABLE project_versions
    DROP CONSTRAINT project_versions_reviewer_id_fkey,
    DROP CONSTRAINT project_versions_author_id_fkey,
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN approved_at TYPE TIMESTAMP;

UPDATE project_versions pv
SET author_id = -1
WHERE pv.author_id IS NULL;

ALTER TABLE project_versions
    ALTER COLUMN author_id SET NOT NULL,
    DROP CONSTRAINT versions_project_id_fkey,
    ADD CONSTRAINT versions_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects;

ALTER TABLE project_views
    ALTER COLUMN created_at TYPE TIMESTAMP,
    DROP CONSTRAINT project_views_user_id_fkey;

ALTER TABLE project_visibility_changes
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN resolved_at TYPE TIMESTAMP,
    DROP CONSTRAINT project_visibility_changes_created_by_fkey,
    ADD CONSTRAINT project_visibility_changes_created_by_fkey FOREIGN KEY (created_by) REFERENCES users,
    DROP CONSTRAINT project_visibility_changes_resolved_by_fkey,
    ADD CONSTRAINT project_visibility_changes_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users;

ALTER TABLE project_watchers
    DROP CONSTRAINT project_watchers_pkey,
    DROP CONSTRAINT project_watchers_project_id_fkey,
    DROP CONSTRAINT project_watchers_user_id_fkey;

ALTER TABLE user_organization_roles
    ALTER COLUMN created_at TYPE TIMESTAMP;

ALTER TABLE user_project_roles
    ALTER COLUMN created_at TYPE TIMESTAMP;

ALTER TABLE user_sessions
    ADD COLUMN username VARCHAR(255) REFERENCES users (name) ON UPDATE CASCADE ON DELETE CASCADE;

UPDATE user_sessions us
SET username = u.name
FROM users u
WHERE us.user_id = u.id;

ALTER TABLE user_sessions
    ALTER COLUMN username SET NOT NULL,
    DROP COLUMN user_id,
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN expiration TYPE TIMESTAMP;

ALTER TABLE user_sign_ons
    ALTER COLUMN created_at TYPE TIMESTAMP;

ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMP,
    ALTER COLUMN join_date TYPE TIMESTAMP;

CREATE TABLE project_settings
(
    id           BIGSERIAL PRIMARY KEY,
    created_at   TIMESTAMP            NOT NULL,
    project_id   BIGINT               NOT NULL UNIQUE
        REFERENCES projects
            ON DELETE CASCADE,
    homepage     VARCHAR(255),
    issues       VARCHAR(255),
    source       VARCHAR(255),
    license_name VARCHAR(255),
    license_url  VARCHAR(255),
    forum_sync   BOOLEAN DEFAULT TRUE NOT NULL,
    support      VARCHAR(255)
);

INSERT INTO project_settings (created_at, project_id, homepage, issues, source, license_name, license_url, forum_sync,
                              support)
SELECT p.created_at,
       p.id,
       p.homepage,
       p.issues,
       p.source,
       p.license_name,
       p.license_url,
       p.forum_sync,
       p.support
FROM projects p;

ALTER TABLE projects
    ADD COLUMN stars BIGINT,
    ADD COLUMN last_updated TIMESTAMP NOT NULL DEFAULT now(),
    ALTER COLUMN created_at TYPE TIMESTAMP,
    DROP COLUMN homepage,
    DROP COLUMN issues,
    DROP COLUMN source,
    DROP COLUMN support,
    DROP COLUMN license_name,
    DROP COLUMN license_url,
    DROP COLUMN forum_sync,
    DROP CONSTRAINT projects_owner_id_fkey,
    ADD CONSTRAINT projects_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users,
    ALTER COLUMN recommended_version_id SET DEFAULT -1,
    DROP CONSTRAINT projects_recommended_version_id_fkey;

UPDATE projects p
SET stars      = coalesce(ps.stars, 0),
    owner_name = u.name
FROM users u,
     (SELECT pp.id, COUNT(*) AS stars
      FROM projects pp
               LEFT JOIN project_stars ps ON pp.id = ps.project_id
      GROUP BY pp.id) ps
WHERE p.owner_id = u.id
  AND p.id = ps.id;

ALTER TABLE projects
    ALTER COLUMN owner_name SET NOT NULL,
    ALTER COLUMN stars SET NOT NULL;

DROP TRIGGER project_owner_name_updater ON projects;
DROP FUNCTION update_project_name_trigger;

DROP FUNCTION logged_action_type_from_int;

CREATE FUNCTION logged_action_type_to_int(id LOGGED_ACTION_TYPE) RETURNS INT
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
    LANGUAGE plpgsql AS
$$
BEGIN
    CASE id
        WHEN 'project_visibility_change' THEN RETURN 0;;
        WHEN 'project_renamed' THEN RETURN 2;;
        WHEN 'project_flagged' THEN RETURN 3;;
        WHEN 'project_settings_changed' THEN RETURN 4;;
        WHEN 'project_member_removed' THEN RETURN 5;;
        WHEN 'project_icon_changed' THEN RETURN 6;;
        WHEN 'project_page_edited' THEN RETURN 7;;
        WHEN 'project_flag_resolved' THEN RETURN 13;;
        WHEN 'version_deleted' THEN RETURN 8;;
        WHEN 'version_uploaded' THEN RETURN 9;;
        WHEN 'version_description_changed' THEN RETURN 12;;
        WHEN 'version_review_state_changed' THEN RETURN 17;;
        WHEN 'user_tagline_changed' THEN RETURN 14;;
        ELSE
        END CASE;;

    RETURN NULL;;
END;;
$$;

CREATE TABLE logged_actions
(
    id                BIGSERIAL PRIMARY KEY,
    created_at        TIMESTAMP DEFAULT now() NOT NULL,
    user_id           BIGINT                  NOT NULL REFERENCES users,
    address           INET                    NOT NULL,
    action            INTEGER                 NOT NULL,
    action_context    INTEGER                 NOT NULL,
    action_context_id BIGINT                  NOT NULL,
    new_state         TEXT                    NOT NULL,
    old_state         TEXT                    NOT NULL
);

CREATE INDEX i_logged_actions
    ON logged_actions (action_context, action_context_id);

INSERT INTO logged_actions (created_at, user_id, address, action, action_context, action_context_id, new_state,
                            old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_to_int(a.action),
       0,
       a.project_id,
       a.new_state,
       a.old_state
FROM logged_actions_project a WHERE a.project_id IS NOT NULL;

DROP TABLE logged_actions_project;

INSERT INTO logged_actions (created_at, user_id, address, action, action_context, action_context_id, new_state,
                            old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_to_int(a.action),
       1,
       a.version_id,
       a.new_state,
       a.old_state
FROM logged_actions_version a WHERE a.version_id IS NOT NULL;

DROP TABLE logged_actions_version;

INSERT INTO logged_actions (created_at, user_id, address, action, action_context, action_context_id, new_state,
                            old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_to_int(a.action),
       2,
       a.page_id,
       a.new_state,
       a.old_state
FROM logged_actions_page a WHERE a.page_id IS NOT NULL;

DROP TABLE logged_actions_page;

INSERT INTO logged_actions (created_at, user_id, address, action, action_context, action_context_id, new_state,
                            old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_to_int(a.action),
       3,
       a.subject_id,
       a.new_state,
       a.old_state
FROM logged_actions_user a WHERE a.subject_id IS NOT NULL;

DROP TABLE logged_actions_user;

INSERT INTO logged_actions (created_at, user_id, address, action, action_context, action_context_id, new_state,
                            old_state)
SELECT a.created_at,
       a.user_id,
       a.address,
       logged_action_type_to_int(a.action),
       4,
       a.organization_id,
       a.new_state,
       a.old_state
FROM logged_actions_organization a WHERE a.organization_id IS NOT NULL;

DROP TABLE logged_actions_organization;

DROP FUNCTION logged_action_type_to_int;
DROP TYPE LOGGED_ACTION_TYPE;

CREATE VIEW v_logged_actions
AS
SELECT a.id,
       a.created_at,
       a.user_id,
       a.address,
       a.action,
       a.action_context,
       a.action_context_id,
       a.new_state,
       a.old_state,
       u.id              AS u_id,
       u.name            AS u_name,
       p.id              AS p_id,
       p.plugin_id       AS p_plugin_id,
       p.slug            AS p_slug,
       p.owner_name      AS p_owner_name,
       pv.id             AS pv_id,
       pv.version_string AS pv_version_string,
       pp.id             AS pp_id,
       pp.name           AS pp_name,
       pp.slug           AS pp_slug,
       s.id              AS s_id,
       s.name            AS s_name,
       CASE
           WHEN (a.action_context = 0) THEN a.action_context_id -- Project
           WHEN (a.action_context = 1) THEN COALESCE(pv.project_id, -1) -- Version
           WHEN (a.action_context = 2) THEN COALESCE(pp.project_id, -1) -- ProjectPage
           ELSE -1 -- Return -1 to allow filtering
           END           AS filter_project,
       CASE
           WHEN (a.action_context = 1) THEN COALESCE(pv.id, a.action_context_id) -- Version (possible deleted)
           ELSE -1 -- Return -1 to allow filtering correctly
           END           AS filter_version,
       CASE
           WHEN (a.action_context = 2) THEN COALESCE(pp.id, -1)
           ELSE -1
           END           AS filter_page,
       CASE
           WHEN (a.action_context = 3) THEN a.action_context_id -- User
           WHEN (a.action_context = 4) THEN a.action_context_id -- Organization
           ELSE -1
           END           AS filter_subject,
       a.action          AS filter_action
FROM logged_actions a
         LEFT OUTER JOIN users u ON a.user_id = u.id
         LEFT OUTER JOIN projects p ON
        CASE
            WHEN a.action_context = 0 AND a.action_context_id = p.id THEN 1 -- Join on action
            WHEN a.action_context = 1 AND
                 (SELECT project_id FROM project_versions pvin WHERE pvin.id = a.action_context_id) = p.id
                THEN 1 -- Query for projectId from Version
            WHEN a.action_context = 2 AND
                 (SELECT project_id FROM project_pages ppin WHERE ppin.id = a.action_context_id) = p.id
                THEN 1 -- Query for projectId from Page
            ELSE 0
            END = 1
         LEFT OUTER JOIN project_versions pv ON (a.action_context = 1 AND a.action_context_id = pv.id)
         LEFT OUTER JOIN project_pages pp ON (a.action_context = 2 AND a.action_context_id = pp.id)
         LEFT OUTER JOIN users s ON
        CASE
            WHEN a.action_context = 3 AND a.action_context_id = s.id THEN 1
            WHEN a.action_context = 4 AND a.action_context_id = s.id THEN 1
            ELSE 0
            END = 1;

CREATE MATERIALIZED VIEW home_projects AS
    WITH tags AS (
        SELECT sq.project_id, sq.version_string, sq.tag_name, sq.tag_version, sq.tag_color
        FROM (SELECT pv.project_id,
                     pv.version_string,
                     pvt.name                                                                            AS tag_name,
                     pvt.data                                                                            AS tag_version,
                     pvt.platform_version,
                     pvt.color                                                                           AS tag_color,
                     row_number()
                     OVER (PARTITION BY pv.project_id, pvt.platform_version ORDER BY pv.created_at DESC) AS row_num
              FROM project_versions pv
                       JOIN (
                  SELECT pvti.version_id,
                         pvti.name,
                         pvti.data,
                         --TODO, use a STORED column in Postgres 12
                         CASE
                             WHEN pvti.name = 'Sponge'
                                 THEN substring(pvti.data FROM
                                                '^\[?(\d+)\.\d+(?:\.\d+)?(?:-SNAPSHOT)?(?:-[a-z0-9]{7,9})?(?:,(?:\d+\.\d+(?:\.\d+)?)?\))?$')
                             WHEN pvti.name = 'SpongeForge'
                                 THEN substring(pvti.data FROM
                                                '^\d+\.\d+\.\d+-\d+-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$')
                             WHEN pvti.name = 'SpongeVanilla'
                                 THEN substring(pvti.data FROM
                                                '^\d+\.\d+\.\d+-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$')
                             WHEN pvti.name = 'Forge'
                                 THEN substring(pvti.data FROM '^\d+\.(\d+)\.\d+(?:\.\d+)?$')
                             WHEN pvti.name = 'Lantern'
                                 THEN NULL --TODO Change this once Lantern changes to SpongeVanilla's format
                             ELSE NULL
                             END AS platform_version,
                         pvti.color
                  FROM project_version_tags pvti
                  WHERE pvti.name IN ('Sponge', 'SpongeForge', 'SpongeVanilla', 'Forge', 'Lantern')
                    AND pvti.data IS NOT NULL
              ) pvt ON pv.id = pvt.version_id
              WHERE pv.visibility = 1
                AND pvt.name IN
                    ('Sponge', 'SpongeForge', 'SpongeVanilla', 'Forge', 'Lantern')
                AND pvt.platform_version IS NOT NULL) sq
        WHERE sq.row_num = 1
        ORDER BY sq.platform_version DESC)
    SELECT p.id,
           p.owner_name,
           array_agg(DISTINCT pm.user_id) AS project_members,
           p.slug,
           p.visibility,
           p.views,
           p.downloads,
           p.stars,
           p.category,
           p.description,
           p.name,
           p.plugin_id,
           p.created_at,
           max(lv.created_at)             AS last_updated,
           to_jsonb(
                   ARRAY(SELECT jsonb_build_object('version_string', tags.version_string, 'tag_name',
                                                   tags.tag_name,
                                                   'tag_version', tags.tag_version, 'tag_color',
                                                   tags.tag_color)
                         FROM tags
                         WHERE tags.project_id = p.id
                         LIMIT 5))        AS promoted_versions,
           setweight(to_tsvector('english', p.name) ||
                     to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')) ||
                     to_tsvector('english', p.plugin_id), 'A') ||
           setweight(to_tsvector('english', p.description), 'B') ||
           setweight(to_tsvector('english', array_to_string(p.keywords, ' ')), 'C') ||
           setweight(to_tsvector('english', p.owner_name) ||
                     to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                     'D')                 AS search_words
    FROM projects p
             LEFT JOIN project_versions lv ON p.id = lv.project_id
             JOIN project_members_all pm ON p.id = pm.id
    GROUP BY p.id;

CREATE INDEX home_projects_downloads_idx ON home_projects (downloads);
CREATE INDEX home_projects_stars_idx ON home_projects (stars);
CREATE INDEX home_projects_views_idx ON home_projects (views);
CREATE INDEX home_projects_created_at_idx ON home_projects (extract(EPOCH FROM created_at));
CREATE INDEX home_projects_last_updated_idx ON home_projects (extract(EPOCH FROM last_updated));
CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);
