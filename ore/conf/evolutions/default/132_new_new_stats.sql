# --- !Ups

ALTER TABLE project_versions_downloads_individual
    DROP CONSTRAINT project_versions_downloads_individual_version_id_address_key,
    DROP CONSTRAINT project_versions_downloads_individual_version_id_cookie_key,
    DROP CONSTRAINT project_versions_downloads_individual_version_id_user_id_key,
    ALTER COLUMN processed DROP DEFAULT,
    ALTER COLUMN processed TYPE INT USING processed::INT,
    ALTER COLUMN processed SET DEFAULT 0;

ALTER TABLE project_views_individual
    DROP CONSTRAINT project_views_individual_project_id_address_key,
    DROP CONSTRAINT project_views_individual_project_id_cookie_key,
    DROP CONSTRAINT project_views_individual_project_id_user_id_key,
    ALTER COLUMN processed DROP DEFAULT,
    ALTER COLUMN processed TYPE INT USING processed::INT,
    ALTER COLUMN processed SET DEFAULT 0;

DROP FUNCTION update_project_versions_downloads;
DROP FUNCTION update_project_views;
DROP FUNCTION add_project_view;
DROP FUNCTION add_version_download;

# --- !Downs

DELETE
FROM project_views_individual a
    USING project_views_individual b
WHERE a.id > b.id
  AND a.project_id = b.project_id
  AND (a.address = b.address OR a.cookie = b.cookie OR a.user_id = b.user_id);

DELETE
FROM project_versions_downloads_individual a
    USING project_versions_downloads_individual b
WHERE a.id > b.id
  AND a.project_id = b.project_id
  AND (a.address = b.address OR a.cookie = b.cookie OR a.user_id = b.user_id);

ALTER TABLE project_versions_downloads_individual
    ADD CONSTRAINT project_versions_downloads_individual_version_id_address_key UNIQUE (version_id, address),
    ADD CONSTRAINT project_versions_downloads_individual_version_id_cookie_key UNIQUE (version_id, cookie),
    ADD CONSTRAINT project_versions_downloads_individual_version_id_user_id_key UNIQUE (version_id, user_id),
    ALTER COLUMN processed DROP DEFAULT,
    ALTER COLUMN processed TYPE BOOLEAN USING processed::BOOLEAN,
    ALTER COLUMN processed SET DEFAULT FALSE;

ALTER TABLE project_views_individual
    ADD CONSTRAINT project_views_individual_project_id_address_key UNIQUE (project_id, address),
    ADD CONSTRAINT project_views_individual_project_id_cookie_key UNIQUE (project_id, cookie),
    ADD CONSTRAINT project_views_individual_project_id_user_id_key UNIQUE (project_id, user_id),
    ALTER COLUMN processed DROP DEFAULT,
    ALTER COLUMN processed TYPE BOOLEAN USING processed::BOOLEAN,
    ALTER COLUMN processed SET DEFAULT FALSE;

CREATE OR REPLACE FUNCTION update_project_versions_downloads() RETURNS VOID
    LANGUAGE plpgsql AS
$$
DECLARE
    process_limit CONSTANT TIMESTAMPTZ := date_trunc('day', now());;
BEGIN

    INSERT INTO project_versions_downloads AS pvd (day, project_id, version_id, downloads)
    SELECT date_trunc('day', s.created_at), s.project_id, s.version_id, count(*)
    FROM project_versions_downloads_individual s
    WHERE NOT s.processed
      AND date_trunc('day', s.created_at) < process_limit
    GROUP BY date_trunc('day', s.created_at), s.project_id, s.version_id
    ON CONFLICT (day, version_id) DO UPDATE SET downloads = excluded.downloads + pvd.downloads;;

    UPDATE project_versions_downloads_individual s
    SET processed = TRUE
    WHERE processed = FALSE
      AND date_trunc('day', s.created_at) < process_limit;;

    DELETE
    FROM project_versions_downloads_individual s
    WHERE s.processed
      AND s.created_at <= now() - INTERVAL '30 days';;

    RETURN;;
END;;
$$;

CREATE OR REPLACE FUNCTION update_project_views() RETURNS VOID
    LANGUAGE plpgsql AS
$$
DECLARE
    process_limit CONSTANT TIMESTAMPTZ := date_trunc('day', now());;
BEGIN

    INSERT INTO project_views AS pv (day, project_id, views)
    SELECT date_trunc('day', s.created_at), s.project_id, count(*)
    FROM project_views_individual s
    WHERE NOT s.processed
      AND date_trunc('day', s.created_at) < process_limit
    GROUP BY date_trunc('day', s.created_at), s.project_id
    ON CONFLICT (day, project_id) DO UPDATE SET views = excluded.views + pv.views;;

    UPDATE project_views_individual s
    SET processed = TRUE
    WHERE processed = FALSE
      AND date_trunc('day', s.created_at) < process_limit;;

    DELETE
    FROM project_views_individual s
    WHERE s.processed
      AND s.created_at <= now() - INTERVAL '30 days';;

    RETURN;;
END;;
$$;

--https://stackoverflow.com/questions/1109061/insert-on-duplicate-update-in-postgresql
CREATE FUNCTION add_version_download(project_id BIGINT, version_id BIGINT, address INET, cookie VARCHAR(36),
                                     user_id BIGINT) RETURNS VARCHAR(36)
    LANGUAGE plpgsql AS
$$
DECLARE
    return_cookie VARCHAR(36);;
BEGIN
    LOOP
        UPDATE project_versions_downloads_individual pvdi
        SET address = $3,
            cookie  = $4,
            user_id = COALESCE($5, pvdi.user_id)
        WHERE pvdi.project_id = $1
          AND pvdi.version_id = $2
          AND (pvdi.address = $3 OR pvdi.cookie = $4 OR pvdi.user_id = $5)
        RETURNING pvdi.cookie INTO return_cookie;;

        IF found THEN
            RETURN return_cookie;;
        END IF;;

        BEGIN
            INSERT INTO project_versions_downloads_individual (created_at, project_id, version_id, address, cookie, user_id)
            VALUES (now(), $1, $2, $3, $4, $5);;
            RETURN $4;;
        EXCEPTION
            WHEN UNIQUE_VIOLATION THEN
            --Go to start of loop
        END;;
    END LOOP;;
END;;
$$;

CREATE FUNCTION add_project_view(project_id BIGINT, address INET, cookie VARCHAR(36), user_id BIGINT) RETURNS VARCHAR(36)
    LANGUAGE plpgsql AS
$$
DECLARE
    return_cookie VARCHAR(36);;
BEGIN
    LOOP
        UPDATE project_views_individual pvi
        SET address = $2,
            cookie  = $3,
            user_id = COALESCE($4, pvi.user_id)
        WHERE pvi.project_id = $1
          AND (pvi.address = $2 OR pvi.cookie = $3 OR pvi.user_id = $4)
        RETURNING pvi.cookie INTO return_cookie;;

        IF found THEN
            RETURN return_cookie;;
        END IF;;

        BEGIN
            INSERT INTO project_views_individual (created_at, project_id, address, cookie, user_id)
            VALUES (now(), $1, $2, $3, $4);;
            RETURN $3;;
        EXCEPTION
            WHEN UNIQUE_VIOLATION THEN
            --Go to start of loop
        END;;
    END LOOP;;
END;;
$$;