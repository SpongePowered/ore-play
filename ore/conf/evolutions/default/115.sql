# --- !Ups

ALTER TABLE roles
    ADD COLUMN permission BIT(64) NOT NULL DEFAULT B'0'::BIT(64);
UPDATE roles r
SET permission = (-1)::BIT(64)
WHERE r.name = 'Ore_Admin';
UPDATE roles r
SET permission = (1::BIT(64) << 27) | (1::BIT(64) << 26) | (1::BIT(64) << 25) | (1::BIT(64) << 24)
WHERE r.name = 'Ore_Mod';
UPDATE roles r
SET permission = (1::BIT(64) << 32) | (1::BIT(64) << 34) | (1::BIT(64) << 35) | (1::BIT(64) << 40)
WHERE r.name = 'Ore_Dev';
UPDATE roles r
SET permission = (1::BIT(64) << 32) | (1::BIT(64) << 35)
WHERE r.name = 'Web_Dev';

UPDATE roles r
SET permission = (1::BIT(64) << 2) | (1::BIT(64) << 4) | (1::BIT(64) << 5) | (1::BIT(64) << 6) | (1::BIT(64) << 10) |
                 (1::BIT(64) << 14) | (1::BIT(64) << 12) | (1::BIT(64) << 13) | (1::BIT(64) << 15) | (1::BIT(64) << 9)
WHERE r.name = 'Project_Owner';
UPDATE roles r
SET permission = (1::BIT(64) << 12) | (1::BIT(64) << 13) | (1::BIT(64) << 15) | (1::BIT(64) << 9)
WHERE r.name = 'Project_Developer';
UPDATE roles r
SET permission = (1::BIT(64) << 9)
WHERE r.name = 'Project_Editor';

UPDATE roles r
SET permission = (1::BIT(64) << 2) | (1::BIT(64) << 6) | (1::BIT(64) << 5) | (1::BIT(64) << 1) | (1::BIT(64) << 10) |
                 (1::BIT(64) << 14) | (1::BIT(64) << 8) | (1::BIT(64) << 4) | (1::BIT(64) << 12) | (1::BIT(64) << 13) |
                 (1::BIT(64) << 15) | (1::BIT(64) << 9) | (1::BIT(64) << 21)
WHERE r.name = 'Organization_Owner'
   OR r.name = 'Organization';
UPDATE roles r
SET permission = (1::BIT(64) << 2) | (1::BIT(64) << 5) | (1::BIT(64) << 1) | (1::BIT(64) << 10) | (1::BIT(64) << 14) |
                 (1::BIT(64) << 8) | (1::BIT(64) << 4) | (1::BIT(64) << 12) | (1::BIT(64) << 13) | (1::BIT(64) << 15) |
                 (1::BIT(64) << 9) | (1::BIT(64) << 21)
WHERE r.name = 'Organization_Admin';
UPDATE roles r
SET permission = (1::BIT(64) << 8) | (1::BIT(64) << 4) | (1::BIT(64) << 12) | (1::BIT(64) << 13) | (1::BIT(64) << 15) |
                 (1::BIT(64) << 9) | (1::BIT(64) << 21)
WHERE r.name = 'Organization_Developer';
UPDATE roles r
SET permission = (1::BIT(64) << 9) | (1::BIT(64) << 21)
WHERE r.name = 'Organization_Editor';
UPDATE roles r
SET permission = (1::BIT(64) << 21)
WHERE r.name = 'Organization_Support';

DROP VIEW global_trust;
DROP VIEW project_trust;
DROP VIEW organization_trust;

CREATE OR REPLACE VIEW global_trust AS
SELECT gr.user_id, coalesce(bit_or(r.permission), B'0'::bit(64)) AS permission
FROM user_global_roles gr
         JOIN roles r ON gr.role_id = r.id
GROUP BY gr.user_id;

CREATE OR REPLACE VIEW project_trust AS
SELECT pm.project_id, pm.user_id, coalesce(bit_or(r.permission), B'0'::bit(64)) AS permission
FROM project_members pm
         JOIN user_project_roles rp ON pm.project_id = rp.project_id AND pm.user_id = rp.user_id AND rp.is_accepted
         JOIN roles r ON rp.role_type = r.name
GROUP BY pm.project_id, pm.user_id;

CREATE OR REPLACE VIEW organization_trust AS
SELECT om.organization_id, om.user_id, coalesce(bit_or(r.permission), B'0'::bit(64)) AS permission
FROM organization_members om
         JOIN user_organization_roles ro
              ON om.organization_id = ro.organization_id AND om.user_id = ro.user_id AND ro.is_accepted
         JOIN roles r ON ro.role_type = r.name
GROUP BY om.organization_id, om.user_id;

ALTER TABLE roles
    DROP COLUMN trust;

# --- !Downs

ALTER TABLE roles
    ADD COLUMN trust INTEGER NOT NULL DEFAULT 0;

UPDATE roles r
SET trust = 5
WHERE r.name = 'Ore_Admin';
UPDATE roles r
SET trust = 2
WHERE r.name = 'Ore_Mod';
UPDATE roles r
SET trust = 5
WHERE r.name = 'Project_Owner';
UPDATE roles r
SET trust = 3
WHERE r.name = 'Project_Developer';
UPDATE roles r
SET trust = 1
WHERE r.name = 'Project_Editor';
UPDATE roles r
SET trust = 5
WHERE r.name = 'Organization';
UPDATE roles r
SET trust = 5
WHERE r.name = 'Organization_Owner';
UPDATE roles r
SET trust = 4
WHERE r.name = 'Organization_Admin';
UPDATE roles r
SET trust = 3
WHERE r.name = 'Organization_Developer';
UPDATE roles r
SET trust = 1
WHERE r.name = 'Organization_Editor';

DROP VIEW global_trust;
DROP VIEW project_trust;
DROP VIEW organization_trust;

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

ALTER TABLE roles
    DROP COLUMN permission;
