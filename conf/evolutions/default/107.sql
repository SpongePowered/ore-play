# --- !Ups

--Tags
CREATE TABLE project_version_tags (
  id         BIGSERIAL PRIMARY KEY              NOT NULL,
  version_id BIGINT REFERENCES project_versions NOT NULL,
  name       VARCHAR(255)                       NOT NULL,
  data       VARCHAR(255)                       NOT NULL,
  color      INTEGER                            NOT NULL
);

INSERT INTO project_version_tags (version_id, name, data, color)
SELECT version.id, pt.name, pt.data, pt.color
FROM (SELECT unnest(tags) AS tags, id FROM project_versions) AS version
       JOIN project_tags pt ON version.tags = pt.id
       JOIN project_versions v ON v.id = version.id;

ALTER TABLE project_versions
  DROP COLUMN tags;
DROP TABLE project_tags;

--Roles
CREATE TYPE ROLE_CATEGORY AS ENUM ('global', 'project', 'organization');

CREATE TABLE roles (
  id            BIGINT PRIMARY KEY NOT NULL,
  name          VARCHAR(255)       NOT NULL,
  category      ROLE_CATEGORY      NOT NULL,
  trust         INTEGER            NOT NULL,
  title         VARCHAR(255)       NOT NULL,
  color         VARCHAR(255)       NOT NULL,
  is_assignable BOOLEAN            NOT NULL,
  rank          INTEGER
);

INSERT INTO roles (id, name, category, trust, title, color, is_assignable, rank)
VALUES (1, 'Ore_Admin', 'global', 5, 'Ore Admin', '#DC0000', TRUE, NULL),
       (2, 'Ore_Mod', 'global', 2, 'Ore Moderator', '#0096FF', TRUE, NULL),
       (3, 'Sponge_Leader', 'global', 0, 'Sponge Leader', '#FFC800', TRUE, NULL),
       (4, 'Team_Leader', 'global', 0, 'Team Leader', '#FFC800', TRUE, NULL),
       (5, 'Community_Leader', 'global', 0, 'Community Leader', '#FFC800', TRUE, NULL),
       (6, 'Sponge_Staff', 'global', 0, 'Sponge Staff', '#FFC800', TRUE, NULL),
       (7, 'Sponge_Developer', 'global', 0, 'Sponge Developer', '#00DC00', TRUE, NULL),
       (8, 'Ore_Dev', 'global', 0, 'Ore Developer', '#FF8200', TRUE, NULL),
       (9, 'Web_Dev', 'global', 0, 'Web Developer', '#0000FF', TRUE, NULL),
       (10, 'Documenter', 'global', 0, 'Documenter', '#0096FF', TRUE, NULL),
       (11, 'Support', 'global', 0, 'Support', '#0096FF', TRUE, NULL),
       (12, 'Contributor', 'global', 0, 'Contributor', '#00DC00', TRUE, NULL),
       (13, 'Advisor', 'global', 0, 'Advisor', '#0096FF', TRUE, NULL);

INSERT INTO roles (id, name, category, trust, title, color, is_assignable, rank)
VALUES (14, 'Stone_Donor', 'global', 0, 'Stone Donor', '#A9A9A9', TRUE, 5),
       (15, 'Quartz_Donor', 'global', 0, 'Quartz Donor', '#E7FEFF', TRUE, 4),
       (16, 'Iron_Donor', 'global', 0, 'Iron Donor', '#C0C0C0', TRUE, 3),
       (17, 'Gold_Donor', 'global', 0, 'Gold Donor', '#CFB53B', TRUE, 2),
       (18, 'Diamond_Donor', 'global', 0, 'Diamond Donor', '#B9F2FF', TRUE, 1);

INSERT INTO roles (id, name, category, trust, title, color, is_assignable)
VALUES (19, 'Project_Owner', 'project', 5, 'Owner', 'transparent', FALSE),
       (20, 'Project_Developer', 'project', 3, 'Developer', 'transparent', TRUE),
       (21, 'Project_Editor', 'project', 1, 'Editor', 'transparent', TRUE),
       (22, 'Project_Support', 'project', 0, 'Support', 'transparent', TRUE);

INSERT INTO roles (id, name, category, trust, title, color, is_assignable)
VALUES (23, 'Organization', 'organization', 5, 'Organization', '#B400FF', FALSE),
       (24, 'Organization_Owner', 'organization', 5, 'Owner', '#B400FF', FALSE),
       (25, 'Organization_Admin', 'organization', 4, 'Admin', '#B400FF', TRUE),
       (26, 'Organization_Developer', 'organization', 3, 'Developer', 'transparent', TRUE),
       (27, 'Organization_Editor', 'organization', 1, 'Editor', 'transparent', TRUE),
       (28, 'Organization_Support', 'organization', 0, 'Support', 'transparent', TRUE);

CREATE UNIQUE INDEX role_name_idx ON roles (name);

CREATE TABLE user_global_roles (
  user_id BIGINT NOT NULL REFERENCES users ON DELETE CASCADE,
  role_id BIGINT NOT NULL REFERENCES roles ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);

INSERT INTO user_global_roles (user_id, role_id)
SELECT u.id, r.id
FROM (SELECT id, name, unnest(global_roles) AS role_name FROM users) u
       JOIN roles r ON r.name = u.role_name;

ALTER TABLE users
  DROP COLUMN global_roles;

# --- !Downs

--Tags
CREATE TABLE project_tags (
  id          BIGSERIAL    NOT NULL,
  version_ids BIGINT []    NOT NULL,
  name        VARCHAR(255) NOT NULL,
  data        VARCHAR(255) NOT NULL,
  color       INTEGER      NOT NULL
);

INSERT INTO project_tags (version_ids, name, data, color)
SELECT array_agg(version_id) AS version_ids, name, data, color
FROM project_version_tags
GROUP BY (name, data, color);

ALTER TABLE project_versions
  ADD COLUMN tags BIGINT [] DEFAULT ARRAY [] :: INTEGER [];

WITH updates AS (SELECT pt.version_id, array_agg(pt.tag_id) AS tags
                 FROM (SELECT t.id AS tag_id, unnest(t.version_ids) AS version_id FROM project_tags t) pt
                 GROUP BY pt.version_id)
UPDATE project_versions pv
SET tags = u.tags
FROM updates u
WHERE u.version_id = pv.id;

DROP TABLE project_version_tags;

--Roles
ALTER TABLE users
  ADD COLUMN global_roles VARCHAR(255) [] DEFAULT '{}' :: INTEGER [] NOT NULL;

WITH updates AS (SELECT ur.user_id, array_agg(ur.role_name) AS global_roles
                 FROM (SELECT gr.user_id, r.name AS role_name FROM user_global_roles gr JOIN roles r on gr.role_id = r.id) ur
                 GROUP BY ur.user_id)
UPDATE users
SET global_roles = u.global_roles
FROM updates u
WHERE u.user_id = users.id;

DROP TABLE user_global_roles;

DROP INDEX role_name_idx;

DROP TABLE roles;
DROP TYPE ROLE_CATEGORY;
