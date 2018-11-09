# --- !Ups

CREATE OR REPLACE VIEW global_trust AS
    SELECT u.id AS user_id, coalesce(max(r.trust), 0) AS trust
    FROM users u
           LEFT JOIN user_global_roles gr ON u.id = gr.user_id
           LEFT JOIN roles r ON gr.role_id = r.id
    GROUP BY u.id;

CREATE OR REPLACE VIEW project_trust AS
  SELECT sq.user_id, sq.project_id, max(sq.trust) AS trust
  FROM ((SELECT p.id AS project_id, pm.user_id AS user_id, pr.trust AS trust
         FROM projects p
                JOIN project_members pm ON p.id = pm.project_id
                JOIN user_project_roles upr ON pm.user_id = upr.user_id AND pm.project_id = upr.project_id
                JOIN roles pr ON upr.role_type = pr.name)
        UNION (SELECT p.id AS project_id, om.user_id AS user_id, ro.trust AS trust
               FROM projects p
                      JOIN organizations o ON p.owner_id = o.id
                      JOIN organization_members om ON o.id = om.organization_id
                      JOIN user_organization_roles uor ON o.id = uor.organization_id AND uor.user_id = om.user_id
                      JOIN roles ro ON uor.role_type = ro.name)) sq
  GROUP BY sq.project_id, sq.user_id;

CREATE OR REPLACE VIEW organization_trust AS
  SELECT o.id AS organization_id, om.user_id, coalesce(max(r.trust), 0) AS trust
  FROM organizations o
         JOIN organization_members om ON o.id = om.organization_id
         JOIN user_organization_roles ro ON o.id = ro.organization_id AND om.user_id = ro.user_id
         JOIN roles r ON ro.role_type = r.name
  GROUP BY o.id, o.name, om.user_id;

# --- !Downs

DROP VIEW global_trust;
DROP VIEW project_trust;
DROP VIEW organization_trust;
