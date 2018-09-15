# --- !Ups

CREATE FUNCTION convertGlobalRoles(roles VARCHAR[])
  RETURNS VARCHAR[] AS
$$
DECLARE newRoles VARCHAR[] = '{}';;
  DECLARE role INTEGER;;
BEGIN
  FOREACH role IN ARRAY roles LOOP
    IF role = 61 THEN
      newRoles := array_append(newRoles, 'Ore_Admin');;
    ELSIF role = 62 THEN
      newRoles := array_append(newRoles, 'Ore_Mod');;
    ELSIF role = 44 THEN
      newRoles := array_append(newRoles, 'Sponge_Leader');;
    ELSIF role = 58 THEN
      newRoles := array_append(newRoles, 'Team_Leader');;
    ELSIF role = 59 THEN
      newRoles := array_append(newRoles, 'Community_Leader');;
    ELSIF role = 3 THEN
      newRoles := array_append(newRoles, 'Sponge_Staff');;
    ELSIF role = 41 THEN
      newRoles := array_append(newRoles, 'Sponge_Developer');;
    ELSIF role = 66 THEN
      newRoles := array_append(newRoles, 'Ore_Dev');;
    ELSIF role = 45 THEN
      newRoles := array_append(newRoles, 'Web_dev');;
    ELSIF role = 51 THEN
      newRoles := array_append(newRoles, 'Documenter');;
    ELSIF role = 43 THEN
      newRoles := array_append(newRoles, 'Support');;
    ELSIF role = 49 THEN
      newRoles := array_append(newRoles, 'Contributor');;
    ELSIF role = 48 THEN
      newRoles := array_append(newRoles, 'Advisor');;
    ELSIF role = 57 THEN
      newRoles := array_append(newRoles, 'Stone_Donor');;
    ELSIF role = 54 THEN
      newRoles := array_append(newRoles, 'Quartz_Donor');;
    ELSIF role = 56 THEN
      newRoles := array_append(newRoles, 'Iron_Donor');;
    ELSIF role = 53 THEN
      newRoles := array_append(newRoles, 'Gold_Donor');;
    ELSIF role = 52 THEN
      newRoles := array_append(newRoles, 'Diamond_Donor');;
    ELSIF role = 64 THEN
      newRoles := array_append(newRoles, 'Organization');;
    END IF;;
  END LOOP;;
  RETURN newRoles;;
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION convertProjectOrgaRoles(role VARCHAR)
  RETURNS VARCHAR AS
$$
DECLARE role INTEGER = TO_NUMBER(role, 'MI9');;
BEGIN
  IF role = -1 THEN
    RETURN 'Project_Owner';;
  ELSIF role = -2 THEN
    RETURN 'Project_Developer';;
  ELSIF role = -3 THEN
    RETURN 'Project_Editor';;
  ELSIF role = -4 THEN
    RETURN 'Project_Support';;
  ELSIF role = -5 THEN
    RETURN 'Organization_Owner';;
  ELSIF role = -6 THEN
    RETURN 'Organization_Developer';;
  ELSIF role = -7 THEN
    RETURN 'Organization_Editor';;
  ELSIF role = -8 THEN
    RETURN 'Organization_Support';;
  ELSIF role = -9 THEN
    RETURN 'Organization_Admin';;
  ELSE
    RETURN '';;
  END IF;;
END
$$ LANGUAGE plpgsql;

ALTER TABLE users ALTER global_roles TYPE VARCHAR[];
UPDATE users SET global_roles = convertGlobalRoles(global_roles);

ALTER TABLE user_project_roles ALTER role_type TYPE VARCHAR;
UPDATE user_project_roles SET role_type = convertProjectOrgaRoles(role_type);

ALTER TABLE user_organization_roles ALTER role_type TYPE VARCHAR;
UPDATE user_organization_roles SET role_type = convertProjectOrgaRoles(role_type);

DROP FUNCTION convertGlobalRoles(roles VARCHAR[]);
DROP FUNCTION convertProjectOrgaRoles(role VARCHAR);

# --- !Downs

CREATE FUNCTION convertGlobalRoles(roles VARCHAR[])
  RETURNS INTEGER[] AS
$$
DECLARE newRoles INTEGER[] = '{}';;
  DECLARE role VARCHAR;;
BEGIN
  FOREACH role IN ARRAY roles LOOP
    IF role = 'Ore_Admin' THEN
      newRoles := array_append(newRoles, 61);;
    ELSIF role = 'Ore_Mod' THEN
      newRoles := array_append(newRoles, 62);;
    ELSIF role = 'Sponge_Leader' THEN
      newRoles := array_append(newRoles, 44);;
    ELSIF role = 'Team_Leader' THEN
      newRoles := array_append(newRoles, 58);;
    ELSIF role = 'Community_Leader' THEN
      newRoles := array_append(newRoles, 59);;
    ELSIF role = 'Sponge_Staff' THEN
      newRoles := array_append(newRoles, 3);;
    ELSIF role = 'Sponge_Developer' THEN
      newRoles := array_append(newRoles, 41);;
    ELSIF role = 'Ore_Dev' THEN
      newRoles := array_append(newRoles, 66);;
    ELSIF role = 'Web_dev' THEN
      newRoles := array_append(newRoles, 45);;
    ELSIF role = 'Documenter' THEN
      newRoles := array_append(newRoles, 51);;
    ELSIF role = 'Support' THEN
      newRoles := array_append(newRoles, 43);;
    ELSIF role = 'Contributor' THEN
      newRoles := array_append(newRoles, 49);;
    ELSIF role = 'Advisor' THEN
      newRoles := array_append(newRoles, 48);;
    ELSIF role = 'Stone_Donor' THEN
      newRoles := array_append(newRoles, 57);;
    ELSIF role = 'Quartz_Donor' THEN
      newRoles := array_append(newRoles, 54);;
    ELSIF role = 'Iron_Donor' THEN
      newRoles := array_append(newRoles, 56);;
    ELSIF role = 'Gold_Donor' THEN
      newRoles := array_append(newRoles, 53);;
    ELSIF role = 'Diamond_Donor' THEN
      newRoles := array_append(newRoles, 52);;
    ELSIF role = 'Organization' THEN
      newRoles := array_append(newRoles, 64);;
    END IF;;
  END LOOP;;
  RETURN newRoles;;
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION convertProjectOrgaRoles(role VARCHAR)
  RETURNS INTEGER AS
$$
BEGIN
  IF role = 'Project_Owner' THEN
    RETURN -1;;
  ELSIF role = 'Project_Developer' THEN
    RETURN -2;;
  ELSIF role = 'Project_Editor' THEN
    RETURN -3;;
  ELSIF role = 'Project_Support' THEN
    RETURN -4;;
  ELSIF role = 'Organization_Owner' THEN
    RETURN -5;;
  ELSIF role = 'Organization_Developer' THEN
    RETURN -6;;
  ELSIF role = 'Organization_Editor' THEN
    RETURN -7;;
  ELSIF role = 'Organization_Support' THEN
    RETURN -8;;
  ELSIF role = 'Organization_Admin' THEN
    RETURN -9;;
  ELSE
    RAISE EXCEPTION 'Role not found!';;
    RETURN -99;;
  END IF;;
END
$$ LANGUAGE plpgsql;

UPDATE users SET global_roles = convertGlobalRoles(global_roles);
UPDATE user_project_roles SET role_type = convertProjectOrgaRoles(role_type);
UPDATE user_organization_roles SET role_type = convertProjectOrgaRoles(role_type);

ALTER TABLE user_project_roles ALTER role_type TYPE INTEGER USING role_type::integer;
ALTER TABLE user_organization_roles ALTER role_type TYPE INTEGER USING role_type::integer;
/* Not possible to change the type of column global_roles from the users table back
but slick will automatically cast the varchar to an integer if this evolution is rolled back */

DROP FUNCTION convertGlobalRoles(roles VARCHAR[]);
DROP FUNCTION convertProjectOrgaRoles(role VARCHAR);
