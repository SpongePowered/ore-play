# --- !Ups

ALTER TABLE user_organization_roles
  ADD CONSTRAINT user_organization_roles_user_id_role_type_id_organization_id_key
    UNIQUE (user_id, role_type, organization_id);

CREATE OR REPLACE VIEW global_trust AS
SELECT gr.user_id, coalesce(max(r.trust), 0) AS trust
FROM user_global_roles gr
       JOIN roles r ON gr.role_id = r.id
GROUP BY gr.user_id;

CREATE OR REPLACE VIEW project_trust AS
SELECT pm.project_id, pm.user_id, coalesce(max(r.trust), 0) AS trust
FROM project_members pm
       JOIN user_project_roles rp ON pm.project_id = rp.project_id AND pm.user_id = rp.user_id
       JOIN roles r ON rp.role_type = r.name
GROUP BY pm.project_id, pm.user_id;

CREATE OR REPLACE VIEW organization_trust AS
SELECT om.organization_id, om.user_id, coalesce(max(r.trust), 0) AS trust
FROM organization_members om
       JOIN user_organization_roles ro
            ON om.organization_id = ro.organization_id AND om.user_id = ro.user_id
       JOIN roles r ON ro.role_type = r.name
GROUP BY om.organization_id, om.user_id;

CREATE MATERIALIZED VIEW home_projects AS
SELECT p.owner_name,
       p.owner_id,
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
       p.last_updated,
       v.version_string,
       pvt.name                                                                                              AS tag_name,
       pvt.data                                                                                              AS tag_data,
       pvt.color                                                                                             AS tag_color,
       setweight(to_tsvector('english', p.name), 'A') ||
       setweight(to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')), 'A') ||
       setweight(to_tsvector('english', p.plugin_id), 'A') ||
       setweight(to_tsvector('english', p.description), 'B') ||
       setweight(
           to_tsvector('english', string_agg(concat('tag:', pvt2.name, nullif('-' || pvt2.data, '-')), ' ')), 'C'
         ) ||
       setweight(to_tsvector('english', p.owner_name), 'D') ||
       setweight(to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')), 'D') AS search_words
FROM projects p
       LEFT JOIN project_versions v ON p.recommended_version_id = v.id
       LEFT JOIN project_version_tags pvt ON v.id = pvt.version_id
       LEFT JOIN project_version_tags pvt2 ON v.id = pvt2.version_id
       JOIN users u ON p.owner_id = u.id
GROUP BY p.id, v.id, pvt.id;

CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);

# --- !Downs

DROP MATERIALIZED VIEW home_projects;
DROP VIEW organization_trust;
DROP VIEW project_trust;
DROP VIEW global_trust;

ALTER TABLE user_organization_roles
  DROP CONSTRAINT user_organization_roles_user_id_role_type_id_organization_id_key;