# --- !Ups

CREATE INDEX IF NOT EXISTS projects_recommended_version_id ON projects (recommended_version_id);
CREATE INDEX IF NOT EXISTS projects_owner_id ON projects (owner_id);
CREATE INDEX IF NOT EXISTS projects_versions_tags_version_id ON project_version_tags (version_id);

DROP MATERIALIZED VIEW home_projects;

CREATE MATERIALIZED VIEW home_projects AS
SELECT p.id,
       p.owner_name,
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
       max(lv.created_at) AS last_updated,
       p.recommended_version_id,
       rv.version_string,
       rvt.name           AS tag_name,
       rvt.data           AS tag_data,
       rvt.color          AS tag_color,
       setweight(to_tsvector('english', p.name) ||
                 to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')) ||
                 to_tsvector('english', p.plugin_id), 'A') ||
       setweight(to_tsvector('english', p.description), 'B') ||
       setweight(to_tsvector('english', p.owner_name) ||
                 to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                 'D')     AS search_words
FROM projects p
       LEFT JOIN project_versions lv ON p.id = lv.project_id
       LEFT JOIN project_versions rv ON p.recommended_version_id = rv.id
       LEFT JOIN project_version_tags rvt ON rv.id = rvt.version_id
GROUP BY p.id, rv.id, rvt.id;

# --- !Downs

DROP MATERIALIZED VIEW home_projects;

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
       pvt.name       AS tag_name,
       pvt.data       AS tag_data,
       pvt.color      AS tag_color,
       setweight(to_tsvector('english', p.name), 'A') ||
       setweight(to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')), 'A') ||
       setweight(to_tsvector('english', p.plugin_id), 'A') ||
       setweight(to_tsvector('english', p.description), 'B') ||
       setweight(
           to_tsvector('english', string_agg(concat('tag:', pvt2.name, nullif('-' || pvt2.data, '-')), ' ')), 'C'
         ) ||
       setweight(to_tsvector('english', p.owner_name), 'D') ||
       setweight(to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                 'D') AS search_words
FROM projects p
       LEFT JOIN project_versions v ON p.recommended_version_id = v.id
       LEFT JOIN project_version_tags pvt ON v.id = pvt.version_id
       LEFT JOIN project_version_tags pvt2 ON v.id = pvt2.version_id
       JOIN users u ON p.owner_id = u.id
GROUP BY p.id, v.id, pvt.id;

CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);

DROP INDEX IF EXISTS projects_recommended_version_id;
DROP INDEX IF EXISTS projects_owner_id;
DROP INDEX IF EXISTS projects_versions_tags_version_id;
