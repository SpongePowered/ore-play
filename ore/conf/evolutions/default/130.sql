# --- !Ups

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

# --- !Downs

CREATE OR REPLACE FUNCTION update_project_versions_downloads() RETURNS VOID
    LANGUAGE plpgsql AS
$$
DECLARE
    process_limit CONSTANT TIMESTAMPTZ := now() - INTERVAL '1 day';;
BEGIN

    INSERT INTO project_versions_downloads (day, project_id, version_id, downloads)
    SELECT date_trunc('day', s.created_at), s.project_id, s.version_id, count(*)
    FROM project_versions_downloads_individual s
    WHERE NOT s.processed
      AND s.created_at <= process_limit
    GROUP BY date_trunc('day', s.created_at), s.project_id, s.version_id;;

    UPDATE project_versions_downloads_individual s
    SET processed = TRUE
    WHERE processed = FALSE
      AND s.created_at <= process_limit;;

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
    process_limit CONSTANT TIMESTAMPTZ := now() - INTERVAL '1 day';;
BEGIN

    INSERT INTO project_views (day, project_id, views)
    SELECT date_trunc('day', s.created_at), s.project_id, count(*)
    FROM project_views_individual s
    WHERE NOT s.processed
      AND s.created_at <= process_limit
    GROUP BY date_trunc('day', s.created_at), s.project_id;;

    UPDATE project_views_individual s
    SET processed = TRUE
    WHERE processed = FALSE
      AND s.created_at <= process_limit;;

    DELETE
    FROM project_views_individual s
    WHERE s.processed
      AND s.created_at <= now() - INTERVAL '30 days';;

    RETURN;;
END;;
$$;