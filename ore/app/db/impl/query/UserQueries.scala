package db.impl.query

import java.sql.Timestamp

import controllers.sugar.Requests.ApiAuthInfo
import db.impl.access.UserBase.UserOrdering
import models.project.Project
import models.querymodels.ProjectListEntry
import models.user.{Organization, User}
import ore.OreConfig
import ore.db.DbRef
import ore.permission.Permission
import ore.permission.role.Role
import ore.project.ProjectSortingStrategy

import doobie._
import doobie.implicits._

object UserQueries extends DoobieOreProtocol {

  //implicit val logger: LogHandler = createLogger("Database")

  def getProjects(
      username: String,
      currentUserId: Option[DbRef[User]],
      canSeeHidden: Boolean,
      order: ProjectSortingStrategy,
      pageSize: Long,
      offset: Long
  ): Query0[ProjectListEntry] = {
    val visibilityFrag =
      if (canSeeHidden) None
      else
        currentUserId.fold(Some(fr"(p.visibility = 1 OR p.visibility = 2)")) { id =>
          Some(fr"(p.visibility = 1 OR p.visibility = 2 OR (p.owner_id = $id AND p.visibility != 5))")
        }

    val fragments =
      sql"""|SELECT p.owner_name,
            |       p.slug,
            |       p.visibility,
            |       p.views,
            |       p.downloads,
            |       p.stars,
            |       p.category,
            |       p.description,
            |       p.name,
            |       p.version_string,
            |       array_remove(array_agg(p.tag_name), NULL)  AS tag_names,
            |       array_remove(array_agg(p.tag_data), NULL)  AS tag_datas,
            |       array_remove(array_agg(p.tag_color), NULL) AS tag_colors
            |  FROM home_projects p
            |  WHERE p.owner_name = $username """.stripMargin ++ visibilityFrag.fold(fr0"")(frag => fr"AND" ++ frag) ++
        fr"""|GROUP BY (p.owner_name,
             |          p.slug,
             |          p.visibility,
             |          p.views,
             |          p.downloads,
             |          p.stars,
             |          p.category,
             |          p.description,
             |          p.name,
             |          p.created_at,
             |          p.last_updated,
             |          p.version_string,
             |          p.search_words)""".stripMargin ++
        fr"ORDER BY" ++ order.fragment ++
        fr"LIMIT $pageSize OFFSET $offset"

    fragments.query[ProjectListEntry]
  }

  private def userFragOrder(reverse: Boolean, sortStr: String) = {
    val sort = if (reverse) fr"ASC" else fr"DESC"

    val sortUserName     = fr"sq.name" ++ sort
    val thenSortUserName = fr"," ++ sortUserName

    sortStr match {
      case UserOrdering.JoinDate => fr"ORDER BY sq.join_date" ++ sort
      case UserOrdering.UserName => fr"ORDER BY" ++ sortUserName
      case UserOrdering.Projects => fr"ORDER BY sq.count" ++ sort ++ thenSortUserName
      case UserOrdering.Role =>
        fr"ORDER BY sq.permission::BIGINT" ++ sort ++ fr"NULLS LAST" ++ fr", sq.role" ++ sort ++ thenSortUserName
    }
  }

  def getAuthors(page: Int, ordering: String)(
      implicit config: OreConfig
  ): Query0[(String, Option[Timestamp], Timestamp, Option[Role], Option[Role], Long)] = {
    val (sort, reverse) = if (ordering.startsWith("-")) (ordering.substring(1), false) else (ordering, true)
    val pageSize        = config.ore.users.authorPageSize
    val offset          = (page - 1) * pageSize

    val fragments =
      sql"""|SELECT sq.name,
            |       sq.join_date,
            |       sq.created_at,
            |       sq.role,
            |       sq.donator_role,
            |       sq.count
            |  FROM (SELECT u.name,
            |               u.join_date,
            |               u.created_at,
            |               r.name                                                      AS role,
            |               r.permission,
            |               (SELECT COUNT(*) FROM projects WHERE owner_id = u.id)       AS count,
            |               CASE WHEN dr.rank IS NULL THEN NULL ELSE dr.name END        AS donator_role,
            |               row_number() OVER (PARTITION BY u.id ORDER BY r.permission::BIGINT DESC, dr.rank ASC NULLS LAST) AS row
            |          FROM projects p
            |                 JOIN users u ON p.owner_id = u.id
            |                 LEFT JOIN user_global_roles gr ON gr.user_id = u.id
            |                 LEFT JOIN roles r ON gr.role_id = r.id
            |                 LEFT JOIN user_global_roles dgr on dgr.user_id = u.id
            |                 LEFT JOIN roles dr ON dgr.role_id = dr.id) sq
            |  WHERE sq.row = 1 """.stripMargin ++
        userFragOrder(reverse, sort) ++
        fr"""OFFSET $offset LIMIT $pageSize"""

    fragments.query[(String, Option[Timestamp], Timestamp, Option[Role], Option[Role], Long)]
  }

  def getStaff(page: Int, ordering: String)(
      implicit config: OreConfig
  ): Query0[(String, Role, Option[Timestamp], Timestamp)] = {
    val (sort, reverse) = if (ordering.startsWith("-")) (ordering.substring(1), false) else (ordering, true)
    val pageSize        = config.ore.users.authorPageSize
    val offset          = (page - 1) * pageSize

    val fragments =
      sql"""|SELECT sq.name, sq.role, sq.join_date, sq.created_at
            |  FROM (SELECT u.name                                                  AS name,
            |               r.name                                                  AS role,
            |               u.join_date,
            |               u.created_at,
            |               r.permission,
            |               rank() OVER (PARTITION BY u.name ORDER BY r.permission::BIGINT DESC) AS rank
            |          FROM users u
            |                 JOIN user_global_roles ugr ON u.id = ugr.user_id
            |                 JOIN roles r ON ugr.role_id = r.id
            |          WHERE r.name IN ('Ore_Admin', 'Ore_Mod')) sq
            |  WHERE sq.rank = 1 """.stripMargin ++
        userFragOrder(reverse, sort) ++
        fr"""OFFSET $offset LIMIT $pageSize"""

    fragments.query[(String, Role, Option[Timestamp], Timestamp)]
  }

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

  def getApiAuthInfo(token: String): Query0[ApiAuthInfo] =
    sql"""|SELECT u.id,
          |       u.created_at,
          |       u.full_name,
          |       u.name,
          |       u.email,
          |       u.tagline,
          |       u.join_date,
          |       u.read_prompts,
          |       u.is_locked,
          |       u.language,
          |       ak.name,
          |       ak.owner_id,
          |       ak.token,
          |       ak.raw_key_permissions,
          |       aks.expires,
          |       CASE
          |           WHEN u.id IS NULL THEN 1::BIT(64)
          |           ELSE (coalesce(gt.permission, B'0'::BIT(64)) | 1::BIT(64) | (1::BIT(64) << 1) | (1::BIT(64) << 2)) &
          |                coalesce(ak.raw_key_permissions, (-1)::BIT(64))
          |           END
          |    FROM api_sessions aks
          |             LEFT JOIN api_keys ak ON aks.key_id = ak.id
          |             LEFT JOIN users u ON aks.user_id = u.id
          |             LEFT JOIN global_trust gt ON gt.user_id = u.id
          |  WHERE aks.token = $token""".stripMargin.query[ApiAuthInfo]

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
