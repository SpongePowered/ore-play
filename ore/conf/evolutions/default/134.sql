# --- !Ups

DROP MATERIALIZED VIEW home_projects;

CREATE TYPE STABILITY AS ENUM ('stable', 'beta', 'alpha', 'bleeding', 'unsupported', 'broken');
CREATE TYPE RELEASE_TYPE AS ENUM ('major_update', 'minor_update', 'patches', 'hotfix');

CREATE FUNCTION stability_from_channel(name TEXT) RETURNS STABILITY
    LANGUAGE plpgsql
    IMMUTABLE RETURNS NULL ON NULL INPUT AS
$$
BEGIN
    CASE lower($1)
        WHEN 'beta' THEN RETURN 'beta';;
        WHEN 'alpha' THEN RETURN 'alpha';;
        WHEN 'bleeding' THEN RETURN 'bleeding';;
        WHEN 'snapshot' THEN RETURN 'bleeding';;
        WHEN 'snapshots' THEN RETURN 'bleeding';;
        WHEN 'prerelease' THEN RETURN 'beta';;
        WHEN 'pre' THEN RETURN 'beta';;
        WHEN 'outofdate' THEN RETURN 'unsupported';;
        WHEN 'old' THEN RETURN 'unsupported';;
        WHEN 'workinprogress' THEN RETURN 'beta';;
        WHEN 'devbuild' THEN RETURN 'alpha';;
        WHEN 'development' THEN RETURN 'alpha';;
        WHEN 'spongebleeding' THEN RETURN 'bleeding';;
        ELSE
        END CASE;;

    RETURN 'stable';;
END;;
$$;

ALTER TABLE project_versions
    ADD COLUMN uses_mixin               BOOLEAN,
    ADD COLUMN stability                STABILITY,
    ADD COLUMN release_type             RELEASE_TYPE,
    ADD COLUMN legacy_channel_name      TEXT,
    ADD COLUMN legacy_channel_color     INT,
    ADD COLUMN dependency_ids           TEXT[] NOT NULL DEFAULT ARRAY []::TEXT[],
    ADD COLUMN dependency_versions      TEXT[] NOT NULL DEFAULT ARRAY []::TEXT[],
    ADD COLUMN platforms                TEXT[] NOT NULL DEFAULT ARRAY []::TEXT[],
    ADD COLUMN platform_versions        TEXT[] NOT NULL DEFAULT ARRAY []::TEXT[],
    ADD COLUMN platform_coarse_versions TEXT[] NOT NULL DEFAULT ARRAY []::TEXT[];

-- noinspection SqlWithoutWhere
UPDATE project_versions pv
SET dependency_ids       = (SELECT coalesce(array_agg(split_part(dep_id, ':', 1)), ARRAY []::TEXT[])
                            FROM unnest(dependencies) AS dep_id),
    dependency_versions  = (SELECT coalesce(array_agg(split_part(dep_id, ':', 2)), ARRAY []::TEXT[])
                            FROM unnest(dependencies) AS dep_id),
    uses_mixin           = EXISTS(
            SELECT * FROM project_version_tags pvt WHERE pvt.version_id = pv.id AND pvt.name = 'mixin'),
    stability            = (SELECT stability_from_channel(pc.name)
                            FROM project_channels pc
                            WHERE pc.id = pv.channel_id),
    legacy_channel_name  = (SELECT pc.name FROM project_channels pc WHERE pc.id = pv.channel_id),
    legacy_channel_color = (SELECT pc.color FROM project_channels pc WHERE pc.id = pv.channel_id);

CREATE FUNCTION platform_coarse_version_from_dependency(pluginid TEXT, version TEXT) RETURNS TEXT
    LANGUAGE plpgsql
    IMMUTABLE AS
$$
BEGIN
    CASE $1
        WHEN 'spongeapi' THEN RETURN substring($2 FROM
                                               '^\[?(\d+)\.\d+(?:\.\d+)?(?:-SNAPSHOT)?(?:-[a-z0-9]{7,9})?(?:,(?:\d+\.\d+(?:\.\d+)?)?\))?$');;
        WHEN 'spongeforge' THEN RETURN substring($2 FROM
                                                 '^\d+\.\d+\.\d+-\d+-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$');;
        WHEN 'spongevanilla' THEN RETURN substring($2 FROM
                                                   '^\d+\.\d+\.\d+-(\d+)\.\d+\.\d+(?:(?:-BETA-\d+)|(?:-RC\d+))?$');;
        WHEN 'forge' THEN RETURN substring($2 FROM '^\d+\.(\d+)\.\d+(?:\.\d+)?$')::INT;;
        WHEN 'lantern' THEN RETURN NULL;; --TODO Change this once Lantern changes to SpongeVanilla's format
        ELSE
        END CASE;;

    RETURN $2;;
END;;
$$;

-- We run this separately so we can use dependency_ids and versions
-- noinspection SqlWithoutWhere
UPDATE project_versions pv
SET platforms                = ARRAY(SELECT dep_id
                                     FROM unnest(pv.dependency_ids) AS dep_id
                                     WHERE dep_id IN ('spongeapi', 'spongeforge', 'spongevanilla', 'forge', 'lantern')),
    platform_versions        = ARRAY(SELECT dep_ver
                                     FROM unnest(pv.dependency_ids, pv.dependency_versions) AS dep(dep_id, dep_ver)
                                     WHERE dep_id IN ('spongeapi', 'spongeforge', 'spongevanilla', 'forge', 'lantern')),
    platform_coarse_versions = ARRAY(
            SELECT platform_coarse_version_from_dependency(dep_id, dep_ver)
            FROM unnest(pv.dependency_ids, pv.dependency_versions) AS dep(dep_id, dep_ver)
            WHERE dep_id IN ('spongeapi', 'spongeforge', 'spongevanilla', 'forge', 'lantern'));

DROP FUNCTION platform_coarse_version_from_dependency;

ALTER TABLE project_versions
    ALTER COLUMN uses_mixin SET NOT NULL,
    ALTER COLUMN stability SET NOT NULL,
    ALTER COLUMN dependency_ids DROP DEFAULT,
    ALTER COLUMN dependency_versions DROP DEFAULT,
    ALTER COLUMN platforms DROP DEFAULT,
    ALTER COLUMN platform_versions DROP DEFAULT,
    ALTER COLUMN platform_coarse_versions DROP DEFAULT,
    DROP COLUMN channel_id,
    DROP COLUMN dependencies;

DROP FUNCTION stability_from_channel;

DROP TABLE project_channels;
DROP TABLE project_version_tags;

ALTER TABLE projects
    DROP COLUMN recommended_version_id;


CREATE MATERIALIZED VIEW home_projects AS
WITH promoted AS (
    SELECT sq.project_id,
           sq.version_string,
           sq.platforms,
           sq.platform_versions,
           sq.platform_coarse_versions,
           sq.stability,
           sq.release_type
    FROM (SELECT sq.project_id,
                 sq.version_string,
                 sq.created_at,
                 sq.platforms,
                 sq.platform_versions,
                 sq.platform_coarse_versions,
                 sq.stability,
                 sq.release_type,
                 sq.platform_version,
                 row_number()
                 OVER (PARTITION BY sq.project_id, platform, platform_coarse_version ORDER BY sq.created_at) AS row_num
          FROM (SELECT pv.project_id,
                       pv.version_string,
                       pv.created_at,
                       pv.platforms,
                       pv.platform_versions,
                       pv.platform_coarse_versions,
                       pv.stability,
                       pv.release_type,
                       unnest(pv.platforms)                AS platform,
                       unnest(pv.platform_versions)        AS platform_version,
                       unnest(pv.platform_coarse_versions) AS platform_coarse_version
                FROM project_versions pv
                WHERE pv.visibility = 1) sq
          WHERE sq.platform_coarse_version IS NOT NULL) sq
    WHERE sq.row_num = 1
    ORDER BY sq.platform_version DESC)
SELECT p.id,
       p.owner_name                      AS owner_name,
       array_agg(DISTINCT pm.user_id)    AS project_members,
       p.slug,
       p.visibility,
       coalesce(pva.views, 0)            AS views,
       coalesce(pda.downloads, 0)        AS downloads,
       coalesce(pvr.recent_views, 0)     AS recent_views,
       coalesce(pdr.recent_downloads, 0) AS recent_downloads,
       coalesce(ps.stars, 0)             AS stars,
       coalesce(pw.watchers, 0)          AS watchers,
       p.category,
       p.description,
       p.name,
       p.plugin_id,
       p.created_at,
       max(lv.created_at)                AS last_updated,
       to_jsonb(
               ARRAY(SELECT DISTINCT
                   ON (promoted.version_string) jsonb_build_object(
                                                        'version_string', promoted.version_string,
                                                        'platforms', promoted.platforms,
                                                        'platform_versions', promoted.platform_versions,
                                                        'platform_coarse_versions', promoted.platform_coarse_versions,
                                                        'stability', promoted.stability,
                                                        'release_type', promoted.release_type)
                     FROM promoted
                     WHERE promoted.project_id = p.id
                     LIMIT 5))           AS promoted_versions,
       setweight(to_tsvector('english', p.name) ||
                 to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')) ||
                 to_tsvector('english', p.plugin_id), 'A') ||
       setweight(to_tsvector('english', p.description), 'B') ||
       setweight(to_tsvector('english', array_to_string(p.keywords, ' ')), 'C') ||
       setweight(to_tsvector('english', p.owner_name) ||
                 to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                 'D')                    AS search_words
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
         LEFT JOIN (SELECT pv.project_id, sum(pv.views) AS views FROM project_views pv GROUP BY pv.project_id) pva
                   ON p.id = pva.project_id
         LEFT JOIN (SELECT pv.project_id, sum(pv.downloads) AS downloads
                    FROM project_versions_downloads pv
                    GROUP BY pv.project_id) pda ON p.id = pda.project_id
         LEFT JOIN (SELECT pv.project_id, sum(pv.views) AS recent_views
                    FROM project_views pv
                    WHERE pv.day BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE
                    GROUP BY pv.project_id) pvr
                   ON p.id = pvr.project_id
         LEFT JOIN (SELECT pv.project_id, sum(pv.downloads) AS recent_downloads
                    FROM project_versions_downloads pv
                    WHERE pv.day BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE
                    GROUP BY pv.project_id) pdr ON p.id = pdr.project_id
GROUP BY p.id, ps.stars, pw.watchers, pva.views, pda.downloads, pvr.recent_views, pdr.recent_downloads;

# --- !Downs

DROP MATERIALIZED VIEW home_projects;

ALTER TABLE projects
    ADD COLUMN recommended_version_id BIGINT REFERENCES project_versions ON DELETE SET NULL;

CREATE INDEX projects_recommended_version_id ON projects (recommended_version_id);

CREATE TABLE project_channels
(
    id              BIGSERIAL             NOT NULL PRIMARY KEY,
    created_at      TIMESTAMPTZ           NOT NULL,
    name            VARCHAR(255)          NOT NULL,
    color           INTEGER               NOT NULL,
    project_id      BIGINT                NOT NULL REFERENCES projects ON DELETE CASCADE,
    is_non_reviewed BOOLEAN DEFAULT FALSE NOT NULL,
    UNIQUE (project_id, color),
    UNIQUE (project_id, name)
);

ALTER TABLE project_versions
    ADD COLUMN channel_id BIGINT REFERENCES project_channels;

INSERT INTO project_channels (created_at, name, color, project_id)
SELECT pv.created_at, pv.legacy_channel_name, pv.legacy_channel_color, pv.project_id
FROM project_versions pv
WHERE pv.legacy_channel_name IS NOT NULL
  AND pv.legacy_channel_color IS NOT NULL
ORDER BY pv.created_at
ON CONFLICT DO NOTHING;

INSERT INTO project_channels (created_at, name, color, project_id)
SELECT p.created_at, 'Release', 8, p.id
FROM projects p
ON CONFLICT DO NOTHING;

UPDATE project_versions pv
SET channel_id = pc.id
FROM project_channels pc
WHERE pc.project_id = pv.project_id
  AND pv.legacy_channel_name = pc.name;

UPDATE project_versions pv
SET channel_id = pc.id
FROM project_channels pc
WHERE pc.project_id = pv.project_id
  AND pv.channel_id IS NULL
  AND pc.name = 'Release';

ALTER TABLE project_versions
    ALTER COLUMN channel_id SET NOT NULL;

CREATE TABLE project_version_tags
(
    id         BIGSERIAL    NOT NULL PRIMARY KEY,
    version_id BIGINT       NOT NULL REFERENCES project_versions ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    data       VARCHAR(255),
    color      INTEGER      NOT NULL
);

CREATE INDEX projects_versions_tags_version_id
    ON project_version_tags (version_id);

CREATE INDEX project_version_tags_name_data_idx
    ON project_version_tags (name, data);

INSERT INTO project_version_tags (version_id, name, data, color)
SELECT pv.id,
       CASE pv.dep_id
           WHEN 'spongeapi' THEN 'Sponge'
           WHEN 'spongeforge' THEN 'SpongeForge'
           WHEN 'spongevanilla' THEN 'SpongeVanilla'
           WHEN 'sponge' THEN 'SpongeCommon'
           WHEN 'lantern' THEN 'Lantern'
           WHEN 'forge' THEN 'Forge'
           END,
       dep_version,
       CASE pv.dep_id
           WHEN 'spongeapi' THEN 1
           WHEN 'spongeforge' THEN 4
           WHEN 'spongevanilla' THEN 5
           WHEN 'sponge' THEN 6
           WHEN 'lantern' THEN 7
           WHEN 'forge' THEN 2
           END
FROM (SELECT pv.id, unnest(pv.dependency_ids) AS dep_id, unnest(pv.dependency_versions) AS dep_version
      FROM project_versions pv) pv
WHERE dep_id IN ('spongeapi', 'spongeforge', 'spongevanilla', 'sponge', 'lantern', 'forge');

INSERT INTO project_version_tags (version_id, name, color)
SELECT pv.id, 'Mixin', 8
FROM project_versions pv
WHERE pv.uses_mixin;

INSERT INTO project_version_tags (version_id, name, color)
SELECT pv.id, 'Unstable', 3
FROM project_versions pv
WHERE pv.stability = 'alpha';

ALTER TABLE project_versions
    DROP COLUMN uses_mixin,
    DROP COLUMN stability,
    DROP COLUMN release_type,
    DROP COLUMN legacy_channel_name,
    DROP COLUMN legacy_channel_color,
    DROP COLUMN platforms,
    DROP COLUMN platform_versions,
    DROP COLUMN platform_coarse_versions,
    ADD COLUMN dependencies TEXT[];

DROP TYPE STABILITY;
DROP TYPE RELEASE_TYPE;

-- noinspection SqlWithoutWhere
UPDATE project_versions
SET dependencies = ARRAY(SELECT dep_id || ':' || dep_version
                         FROM unnest(dependency_ids, dependency_versions) AS dep(dep_id, dep_version));

ALTER TABLE project_versions
    DROP COLUMN dependency_ids,
    DROP COLUMN dependency_versions;

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
       p.owner_name                      AS owner_name,
       array_agg(DISTINCT pm.user_id)    AS project_members,
       p.slug,
       p.visibility,
       coalesce(pva.views, 0)            AS views,
       coalesce(pda.downloads, 0)        AS downloads,
       coalesce(pvr.recent_views, 0)     AS recent_views,
       coalesce(pdr.recent_downloads, 0) AS recent_downloads,
       coalesce(ps.stars, 0)             AS stars,
       coalesce(pw.watchers, 0)          AS watchers,
       p.category,
       p.description,
       p.name,
       p.plugin_id,
       p.created_at,
       max(lv.created_at)                AS last_updated,
       to_jsonb(
               ARRAY(SELECT jsonb_build_object('version_string', tags.version_string, 'tag_name',
                                               tags.tag_name,
                                               'tag_version', tags.tag_version, 'tag_color',
                                               tags.tag_color)
                     FROM tags
                     WHERE tags.project_id = p.id
                     LIMIT 5))           AS promoted_versions,
       setweight(to_tsvector('english', p.name) ||
                 to_tsvector('english', regexp_replace(p.name, '([a-z])([A-Z]+)', '\1_\2', 'g')) ||
                 to_tsvector('english', p.plugin_id), 'A') ||
       setweight(to_tsvector('english', p.description), 'B') ||
       setweight(to_tsvector('english', array_to_string(p.keywords, ' ')), 'C') ||
       setweight(to_tsvector('english', p.owner_name) ||
                 to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                 'D')                    AS search_words
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
         LEFT JOIN (SELECT pv.project_id, sum(pv.views) AS views FROM project_views pv GROUP BY pv.project_id) pva
                   ON p.id = pva.project_id
         LEFT JOIN (SELECT pv.project_id, sum(pv.downloads) AS downloads
                    FROM project_versions_downloads pv
                    GROUP BY pv.project_id) pda ON p.id = pda.project_id
         LEFT JOIN (SELECT pv.project_id, sum(pv.views) AS recent_views
                    FROM project_views pv
                    WHERE pv.day BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE
                    GROUP BY pv.project_id) pvr
                   ON p.id = pvr.project_id
         LEFT JOIN (SELECT pv.project_id, sum(pv.downloads) AS recent_downloads
                    FROM project_versions_downloads pv
                    WHERE pv.day BETWEEN CURRENT_DATE - INTERVAL '30 days' AND CURRENT_DATE
                    GROUP BY pv.project_id) pdr ON p.id = pdr.project_id
GROUP BY p.id, ps.stars, pw.watchers, pva.views, pda.downloads, pvr.recent_views, pdr.recent_downloads;
