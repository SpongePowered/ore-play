package ore.db.impl.query

import ore.db.DbRef
import ore.models.organization.Organization
import ore.models.project.Project
import ore.models.user.User
import ore.permission.Permission

import doobie._
import doobie.implicits._

object UserQueries extends DoobieOreProtocol {

  //implicit val logger: LogHandler = createLogger("Database")

  def globalPermission(userId: DbRef[User]): Query0[Permission] =
    sql"""|SELECT coalesce(gt.permission, B'0'::BIT(64))
          |  FROM users u
          |         LEFT JOIN global_trust gt ON gt.user_id = u.id
          |  WHERE u.id = $userId""".stripMargin.query[Permission]

  def projectPermission(userId: DbRef[User], projectId: DbRef[Project]): Query0[Permission] =
    sql"""|SELECT coalesce(gt.permission, B'0'::BIT(64)) | coalesce(pt.permission, B'0'::BIT(64)) | coalesce(ot.permission, B'0'::BIT(64))
          |  FROM users u
          |         LEFT JOIN global_trust gt ON gt.user_id = u.id
          |         LEFT JOIN projects p ON p.id = $projectId
          |         LEFT JOIN project_trust pt ON pt.user_id = u.id AND pt.project_id = p.id
          |         LEFT JOIN organization_trust ot ON ot.user_id = u.id AND ot.organization_id = p.owner_id
          |  WHERE u.id = $userId;""".stripMargin.query[Permission]

  def organizationPermission(userId: DbRef[User], organizationId: DbRef[Organization]): Query0[Permission] =
    sql"""|SELECT coalesce(gt.permission, B'0'::BIT(64)) | coalesce(ot.permission, B'0'::BIT(64))
          |  FROM users u
          |         LEFT JOIN global_trust gt ON gt.user_id = u.id
          |         LEFT JOIN organization_trust ot ON ot.user_id = u.id AND ot.organization_id = $organizationId
          |  WHERE u.id = $userId;""".stripMargin.query[Permission]

  def allPossibleProjectPermissions(userId: DbRef[User]): Query0[Permission] =
    sql"""|SELECT coalesce(bit_or(r.permission), B'0'::BIT(64))
          |    FROM user_project_roles upr
          |             JOIN roles r ON upr.role_type = r.name
          |    WHERE upr.user_id = $userId""".stripMargin.query[Permission]

  def allPossibleOrgPermissions(userId: DbRef[User]): Query0[Permission] =
    sql"""|SELECT coalesce(bit_or(r.permission), B'0'::BIT(64))
          |    FROM user_organization_roles uor
          |             JOIN roles r ON uor.role_type = r.name
          |    WHERE uor.user_id = $userId""".stripMargin.query[Permission]

}
