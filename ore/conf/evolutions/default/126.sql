# --- !Ups

CREATE INDEX project_version_tags_name_data_idx ON project_version_tags (name, data);

DROP MATERIALIZED VIEW home_projects;

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
           max(lv.created_at)      AS last_updated,
           to_jsonb(
                   ARRAY(SELECT jsonb_build_object('version_string', tags.version_string, 'tag_name', tags.tag_name,
                                                  'tag_version', tags.tag_version, 'tag_color', tags.tag_color)
                         FROM tags
                         WHERE tags.project_id = p.id
                         LIMIT 5)) as promoted_versions,
           setweight(to_tsvector('english', p.name) ||
                     to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')) ||
                     to_tsvector('english', p.plugin_id), 'A') ||
           setweight(to_tsvector('english', p.description), 'B') ||
           setweight(to_tsvector('english', array_to_string(p.keywords, ' ')), 'C') ||
           setweight(to_tsvector('english', p.owner_name) ||
                     to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                     'D')          AS search_words
    FROM projects p
             LEFT JOIN project_versions lv ON p.id = lv.project_id
    GROUP BY p.id
    ORDER BY p.downloads DESC;

CREATE INDEX home_projects_downloads_idx ON home_projects (downloads);
CREATE INDEX home_projects_stars_idx ON home_projects (stars);
CREATE INDEX home_projects_views_idx ON home_projects (views);
CREATE INDEX home_projects_created_at_idx ON home_projects (extract(EPOCH from created_at));
CREATE INDEX home_projects_last_updated_idx ON home_projects (extract(EPOCH from last_updated));
CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);

# --- !Downs

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
       rv.channel_id      as recommended_version_channel_id,
       rv.version_string,
       rvt.name           AS tag_name,
       rvt.data           AS tag_data,
       rvt.color          AS tag_color,
       setweight(to_tsvector('english', p.name) ||
                 to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')) ||
                 to_tsvector('english', p.plugin_id), 'A') ||
       setweight(to_tsvector('english', p.description), 'B') ||
       setweight(to_tsvector('english', array_to_string(p.keywords, ' ')), 'C') ||
       setweight(to_tsvector('english', p.owner_name) ||
                 to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                 'D')     AS search_words
FROM projects p
         LEFT JOIN project_versions lv ON p.id = lv.project_id
         LEFT JOIN project_versions rv ON p.recommended_version_id = rv.id
         LEFT JOIN project_version_tags rvt ON rv.id = rvt.version_id
GROUP BY p.id, rv.id, rvt.id;

CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);

DROP INDEX project_version_tags_name_data_idx;