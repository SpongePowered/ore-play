# --- !Ups

ALTER TABLE public.project_version_download_warnings DROP CONSTRAINT IF EXISTS project_version_download_warnings_address_key;
ALTER TABLE public.project_version_download_warnings ADD CONSTRAINT project_version_download_warnings_address_key UNIQUE (address, version_id);


# --- !Downs
DELETE FROM project_version_download_warnings WHERE address IN (SELECT w.address FROM project_version_download_warnings w GROUP BY w.address HAVING COUNT(w.address) > 1);
ALTER TABLE public.project_version_download_warnings DROP CONSTRAINT IF EXISTS project_version_download_warnings_address_key;
ALTER TABLE public.project_version_download_warnings ADD CONSTRAINT project_version_download_warnings_address_key UNIQUE (address);
