# --- !Ups
CREATE TABLE api_keys
(
    id                  BIGSERIAL PRIMARY KEY NOT NULL,
    created_at          TIMESTAMP             NOT NULL,
    name                VARCHAR(255)          NOT NULL,
    owner_id            BIGINT                NOT NULL REFERENCES users,
    token_identifier    VARCHAR(255)          NOT NULL UNIQUE,
    token               TEXT                  NOT NULL,
    raw_key_permissions BIT(64)               NOT NULL,
    UNIQUE (owner_id, name)
);

CREATE TABLE api_sessions
(
    id         BIGSERIAL PRIMARY KEY NOT NULL,
    created_at TIMESTAMP             NOT NULL,
    token      VARCHAR(255)          NOT NULL,
    key_id     BIGINT REFERENCES api_keys ON DELETE CASCADE,
    user_id    BIGINT REFERENCES users,
    expires    TIMESTAMP             NOT NULL
);

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
       setweight(to_tsvector('english', p.owner_name) ||
                 to_tsvector('english', regexp_replace(p.owner_name, '([a-z])([A-Z]+)', '\1_\2', 'g')),
                 'D')     AS search_words
FROM projects p
         LEFT JOIN project_versions lv ON p.id = lv.project_id
         LEFT JOIN project_versions rv ON p.recommended_version_id = rv.id
         LEFT JOIN project_version_tags rvt ON rv.id = rvt.version_id
GROUP BY p.id, rv.id, rvt.id;

CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);

# --- !Downs

DROP TABLE api_sessions;
DROP TABLE api_keys;

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

CREATE INDEX home_projects_search_words_idx ON home_projects USING gin (search_words);
