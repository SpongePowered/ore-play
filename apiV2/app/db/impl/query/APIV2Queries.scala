package db.impl.query

import java.time.LocalDate

import play.api.mvc.RequestHeader

import controllers.apiv2.Users.UserSortingStrategy
import controllers.apiv2.{Pages, Projects, Users, Versions}
import controllers.sugar.Requests.ApiAuthInfo
import models.protocols.APIV2
import models.protocols.APIV2.Organization
import models.querymodels._
import ore.{OreConfig, OrePlatform}
import ore.data.project.Category
import ore.db.DbRef
import ore.db.impl.query.DoobieOreProtocol
import ore.models.api.ApiKey
import ore.models.project.io.ProjectFiles
import ore.models.project.{Page, Project, ProjectSortingStrategy, Version}
import ore.models.user.User
import ore.permission.Permission
import ore.permission.role.Role

import cats.Reducible
import cats.data.NonEmptyList
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import doobie.implicits.javasql._
import doobie.implicits.javatime.JavaTimeLocalDateMeta
import doobie.util.Put
import doobie.util.fragment.Elem
import io.circe.DecodingFailure
import squeal.category._
import squeal.category.syntax.all._
import zio.ZIO
import zio.blocking.Blocking

object APIV2Queries extends DoobieOreProtocol {

  def getApiAuthInfo(token: String): Query0[ApiAuthInfo] =
    sql"""|SELECT u.id,
          |       u.created_at,
          |       u.full_name,
          |       u.name,
          |       u.email,
          |       u.tagline,
          |       u.join_date,
          |       u.read_prompts,
          |       u.language,
          |       ak.name,
          |       ak.owner_id,
          |       ak.token,
          |       ak.raw_key_permissions,
          |       aks.token,
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

  def findApiKey(identifier: String, token: String): Query0[(DbRef[ApiKey], DbRef[User])] =
    sql"""SELECT k.id, k.owner_id FROM api_keys k WHERE k.token_identifier = $identifier AND k.token = crypt($token, k.token)"""
      .query[(DbRef[ApiKey], DbRef[User])]

  def createApiKey(
      name: String,
      ownerId: DbRef[User],
      tokenIdentifier: String,
      token: String,
      perms: Permission
  ): doobie.Update0 =
    sql"""|INSERT INTO api_keys (created_at, name, owner_id, token_identifier, token, raw_key_permissions)
          |VALUES (now(), $name, $ownerId, $tokenIdentifier, crypt($token, gen_salt('bf')), $perms)""".stripMargin.update

  def deleteApiKey(name: String, ownerId: DbRef[User]): doobie.Update0 =
    sql"""DELETE FROM api_keys k WHERE k.name = $name AND k.owner_id = $ownerId""".update

  //Like in, but takes a tuple
  def in2[F[_]: Reducible, A: Put, B: Put](f: Fragment, fs: F[(A, B)]): Fragment =
    fs.toList.map { case (a, b) => fr0"($a, $b)" }.foldSmash1(f ++ fr0"IN (", fr",", fr")")

  def array[F[_]: Reducible, A: Put](fs: F[A]): Fragment =
    fs.toList.map(a => fr0"$a").foldSmash1(fr0"ARRAY[", fr",", fr0"]")

  def array2Text[F[_]: Reducible, A: Put, B: Put](t1: String, t2: String)(fs: F[(A, B)]): Fragment =
    fs.toList
      .map { case (a, b) => fr0"($a::" ++ Fragment.const(t1) ++ fr0", $b::" ++ Fragment.const(t2) ++ fr0")::TEXT" }
      .foldSmash1(fr0"ARRAY[", fr",", fr0"]")

  def projectSelectFrag(
      projectSlug: Option[String],
      category: List[Category],
      platforms: List[(String, Option[String])],
      stability: List[Version.Stability],
      query: Option[String],
      owner: Option[String],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      exactSearch: Boolean
  )(implicit config: OreConfig): Fragment = {
    val userActionsTaken = currentUserId.fold(fr"FALSE, FALSE,") { id =>
      fr"""|EXISTS(SELECT * FROM project_stars s WHERE s.project_id = p.id AND s.user_id = $id)    AS user_stared,
           |EXISTS(SELECT * FROM project_watchers S WHERE S.project_id = p.id AND S.user_id = $id) AS user_watching,""".stripMargin
    }

    val base =
      sql"""|SELECT p.created_at,
            |       p.plugin_id,
            |       p.name,
            |       p.owner_name,
            |       p.slug,
            |       to_jsonb(
            |               ARRAY(SELECT jsonb_build_object(
            |                                    'version_string', promoted.version_string,
            |                                    'platforms', promoted.platforms,
            |                                    'platform_versions', promoted.platform_versions,
            |                                    'platform_coarse_versions', promoted.platform_coarse_versions,
            |                                    'stability', promoted.stability,
            |                                    'release_type', promoted.release_type)
            |                         FROM promoted_versions promoted
            |                         WHERE promoted.project_id = p.id
            |                         ORDER BY promoted.platform_coarse_versions DESC LIMIT 5)) AS promoted_versions,
            |       ps.views,
            |       ps.downloads,
            |       ps.recent_views,
            |       ps.recent_downloads,
            |       ps.stars,
            |       ps.watchers,
            |       p.category,
            |       p.description,
            |       ps.last_updated,
            |       p.visibility,
            |       p.topic_id,
            |       p.post_id,""".stripMargin ++ userActionsTaken ++
        fr"""|       p.keywords,
             |       p.homepage,
             |       p.issues,
             |       p.source,
             |       p.support,
             |       p.license_name,
             |       p.license_url,
             |       p.forum_sync
             |  FROM projects p JOIN project_stats ps ON p.id = ps.id""".stripMargin

    val visibilityFrag =
      if (canSeeHidden) None
      else
        currentUserId.fold(Some(fr"(p.visibility = 1)")) { id =>
          Some(
            fr"(p.visibility = 1 OR ($id IN (SELECT pm.user_id FROM project_members_all pm WHERE pm.id = p.id) AND p.visibility != 5))"
          )
        }

    val (platformsWithVersion, platformsWithoutVersion) = platforms.partitionEither {
      case (name, Some(version)) =>
        Left((name, config.ore.platformsByName.get(name).fold(version)(OrePlatform.coarseVersionOf(_)(version))))
      case (name, None) => Right(name)
    }

    val filters = Fragments.whereAndOpt(
      projectSlug.map(slug => fr"lower(p.slug) = lower($slug)"),
      NonEmptyList.fromList(category).map(Fragments.in(fr"p.category", _)),
      if (platforms.nonEmpty || stability.nonEmpty) {
        val jsSelect =
          sql"""|SELECT promoted.platform
                |    FROM (SELECT unnest(ppv.platforms)                AS platform,
                |                 unnest(ppv.platform_coarse_versions) AS platform_coarse_version,
                |                 ppv.stability
                |              FROM promoted_versions ppv) AS promoted """.stripMargin ++
            Fragments.whereAndOpt(
              NonEmptyList
                .fromList(platformsWithVersion)
                .map(t => in2(fr"(promoted.platform, promoted.platform_coarse_version)", t)),
              NonEmptyList.fromList(platformsWithoutVersion).map(t => Fragments.in(fr"promoted.platform", t)),
              NonEmptyList.fromList(stability).map(Fragments.in(fr"promoted.stability", _))
            )

        Some(fr"EXISTS" ++ Fragments.parentheses(jsSelect))
      } else
        None,
      query.map { q =>
        val trimmedQ = q.trim

        if (exactSearch) {
          fr"lower(p.slug) = lower($trimmedQ)"
        } else {
          if (q.endsWith(" ")) fr"p.search_words @@ websearch_to_tsquery('english', $trimmedQ)"
          else fr"p.search_words @@ websearch_to_tsquery_postfix('english', $trimmedQ)"
        }
      },
      owner.map(o => fr"p.owner_name = $o"),
      visibilityFrag
    )

    val groupBy =
      fr"GROUP BY p.id, ps.views, ps.downloads, ps.recent_views, ps.recent_downloads, ps.stars, ps.watchers, ps.last_updated"

    base ++ filters ++ groupBy
  }

  def projectQuery(
      projectSlug: Option[String],
      category: List[Category],
      platforms: List[(String, Option[String])],
      stability: List[Version.Stability],
      query: Option[String],
      owner: Option[String],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      order: ProjectSortingStrategy,
      orderWithRelevance: Boolean,
      exactSearch: Boolean,
      limit: Long,
      offset: Long
  )(
      implicit projectFiles: ProjectFiles[ZIO[Blocking, Nothing, ?]],
      requestHeader: RequestHeader,
      config: OreConfig
  ): Query0[ZIO[Blocking, Nothing, APIV2.Project]] = {
    val ordering = if (orderWithRelevance && query.nonEmpty) {
      val relevance = query.fold(fr"1") { q =>
        val trimmedQ = q.trim

        if (q.endsWith(" ")) fr"ts_rank(p.search_words, websearch_to_tsquery('english', $trimmedQ)) DESC"
        else fr"ts_rank(p.search_words, websearch_to_tsquery_postfix('english', $trimmedQ)) DESC"
      }

      // 1483056000 is the Ore epoch
      // 86400 seconds to days
      // 604800‬ seconds to weeks
      order match {
        case ProjectSortingStrategy.MostStars     => fr"ps.stars *" ++ relevance
        case ProjectSortingStrategy.MostDownloads => fr"(ps.downloads / 100) *" ++ relevance
        case ProjectSortingStrategy.MostViews     => fr"(ps.views / 200) *" ++ relevance
        case ProjectSortingStrategy.Newest =>
          fr"((EXTRACT(EPOCH FROM p.created_at) - 1483056000) / 86400) *" ++ relevance
        case ProjectSortingStrategy.RecentlyUpdated =>
          fr"((EXTRACT(EPOCH FROM ps.last_updated) - 1483056000) / 604800) *" ++ relevance
        case ProjectSortingStrategy.OnlyRelevance   => relevance
        case ProjectSortingStrategy.RecentViews     => fr"ps.recent_views *" ++ relevance
        case ProjectSortingStrategy.RecentDownloads => fr"ps.recent_downloads*" ++ relevance
      }
    } else order.fragment

    val select = projectSelectFrag(
      projectSlug,
      category,
      platforms,
      stability,
      query,
      owner,
      canSeeHidden,
      currentUserId,
      exactSearch
    )
    (select ++ fr"ORDER BY" ++ ordering ++ fr"LIMIT $limit OFFSET $offset").query[APIV2QueryProject].map(_.asProtocol)
  }

  def singleProjectQuery(
      projectOwner: String,
      projectSlug: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  )(
      implicit projectFiles: ProjectFiles[ZIO[Blocking, Nothing, ?]],
      requestHeader: RequestHeader,
      config: OreConfig
  ): Query0[ZIO[Blocking, Nothing, APIV2.Project]] =
    APIV2Queries.projectQuery(
      Some(projectSlug),
      Nil,
      Nil,
      Nil,
      None,
      Some(projectOwner),
      canSeeHidden,
      currentUserId,
      ProjectSortingStrategy.Default,
      orderWithRelevance = false,
      false,
      1,
      0
    )

  def projectCountQuery(
      projectSlug: Option[String],
      category: List[Category],
      platforms: List[(String, Option[String])],
      stability: List[Version.Stability],
      query: Option[String],
      owner: Option[String],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      exactSearch: Boolean
  )(implicit config: OreConfig): Query0[Long] = {
    val select = projectSelectFrag(
      projectSlug,
      category,
      platforms,
      stability,
      query,
      owner,
      canSeeHidden,
      currentUserId,
      exactSearch
    )
    (sql"SELECT COUNT(*) FROM " ++ Fragments.parentheses(select) ++ fr"sq").query[Long]
  }

  case class Column[A](name: String, mkElem: A => Elem)
  object Column {
    def arg[A](name: String)(implicit put: Put[A]): Column[A]         = Column(name, Elem.Arg(_, put))
    def opt[A](name: String)(implicit put: Put[A]): Column[Option[A]] = Column(name, Elem.Opt(_, put))
  }

  private def updateTable[F[_[_]]: ApplicativeKC: FoldableKC](
      table: String,
      columns: F[Column],
      edits: F[Option]
  ): Fragment = {

    val applyUpdate = new FunctionK[Tuple2K[Option, Column]#λ, Compose2[Option, Const[Fragment]#λ, *]] {
      override def apply[A](tuple: Tuple2K[Option, Column]#λ[A]): Option[Fragment] = {
        val column = tuple._2
        tuple._1.map(value => Fragment.const(column.name) ++ Fragment("= ?", List(column.mkElem(value))))
      }
    }

    val updatesSeq = edits
      .map2KC(columns)(applyUpdate)
      .foldMapKC[List[Option[Fragment]]](
        λ[Compose2[Option, Const[Fragment]#λ, *] ~>: Compose3[List, Option, Const[Fragment]#λ, *]](List(_))
      )

    val updates = Fragments.setOpt(updatesSeq: _*)

    sql"""UPDATE """ ++ Fragment.const(table) ++ updates
  }

  def updateProject(projectId: DbRef[Project], edits: Projects.EditableProject): Update0 = {
    val projectColumns = Projects.EditableProjectF[Column](
      Column.arg("name"),
      Projects.EditableProjectNamespaceF[Column](Column.arg("owner_name")),
      Column.arg("category"),
      Column.opt("description"),
      Projects.EditableProjectSettingsF[Column](
        Column.arg("keywords"),
        Column.opt("homepage"),
        Column.opt("issues"),
        Column.opt("sources"),
        Column.opt("support"),
        Projects.EditableProjectLicenseF[Column](
          Column.opt("license_name"),
          Column.opt("license_url")
        ),
        Column.arg("forum_sync")
      )
    )

    import cats.instances.option._
    import cats.instances.tuple._

    val (newOwnerSet, newOwnerFrom, newOwnerFilter) = edits.namespace.owner.foldMap { owner =>
      (fr", owner_id = u.id", fr"FROM users u", fr"AND u.name = $owner")
    }

    (updateTable("projects p", projectColumns, edits) ++ newOwnerSet ++ newOwnerFrom ++ fr" WHERE p.id = $projectId " ++ newOwnerFilter).update
  }

  def projectMembers(projectId: DbRef[Project], limit: Long, offset: Long): Query0[APIV2.Member] =
    sql"""|SELECT u.name, r.name, upr.is_accepted
          |  FROM projects p
          |         JOIN user_project_roles upr ON p.id = upr.project_id
          |         JOIN users u ON upr.user_id = u.id
          |         JOIN roles r ON upr.role_type = r.name
          |  WHERE p.id = $projectId
          |  ORDER BY r.permission & ~B'1'::BIT(64) DESC LIMIT $limit OFFSET $offset""".stripMargin
      .query[APIV2QueryMember]
      .map(_.asProtocol)

  def orgaMembers(organizationId: DbRef[Organization], limit: Long, offset: Long): Query0[APIV2.Member] =
    sql"""|SELECT u.name, r.name, uor.is_accepted
          |  FROM organizations o
          |         JOIN user_organization_roles uor ON o.id = uor.organization_id
          |         JOIN users u ON uor.user_id = u.id
          |         JOIN roles r ON uor.role_type = r.name
          |  WHERE o.id = $organizationId
          |  ORDER BY r.permission & ~B'1'::BIT(64) DESC LIMIT $limit OFFSET $offset""".stripMargin
      .query[APIV2QueryMember]
      .map(_.asProtocol)

  def versionSelectFrag(
      projectId: DbRef[Project],
      versionName: Option[String],
      platforms: List[(String, Option[String])],
      stability: List[Version.Stability],
      releaseType: List[Version.ReleaseType],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  )(implicit config: OreConfig): Fragment = {
    val base =
      sql"""|SELECT pv.created_at,
            |       pv.version_string,
            |       pv.dependency_ids,
            |       pv.dependency_versions,
            |       pv.visibility,
            |       coalesce((SELECT sum(pvd.downloads) FROM project_versions_downloads pvd WHERE p.id = pvd.project_id AND pv.id = pvd.version_id), 0),
            |       pv.file_size,
            |       pv.hash,
            |       pv.file_name,
            |       u.name,
            |       pv.review_state,
            |       pv.uses_mixin,
            |       pv.stability,
            |       pv.release_type,
            |       coalesce(array_agg(pvp.platform) FILTER ( WHERE pvp.platform IS NOT NULL ), ARRAY []::TEXT[]),
            |       coalesce(array_agg(pvp.platform_version) FILTER ( WHERE pvp.platform IS NOT NULL ), ARRAY []::TEXT[]),
            |       pv.post_id
            |    FROM projects p
            |             JOIN project_versions pv ON p.id = pv.project_id
            |             LEFT JOIN users u ON pv.author_id = u.id
            |             LEFT JOIN project_version_platforms pvp ON pv.id = pvp.version_id """.stripMargin

    val coarsePlatforms = platforms.map {
      case (name, optVersion) =>
        (
          name,
          optVersion.map(version =>
            config.ore.platformsByName.get(name).fold(version)(OrePlatform.coarseVersionOf(_)(version))
          )
        )
    }

    val visibilityFrag =
      if (canSeeHidden) None
      else
        currentUserId.fold(Some(fr"(pv.visibility = 1)")) { id =>
          Some(
            fr"(pv.visibility = 1 OR ($id IN (SELECT pm.user_id FROM project_members_all pm WHERE pm.id = p.id) AND pv.visibility != 5))"
          )
        }

    val filters = Fragments.whereAndOpt(
      Some(fr"p.id = $projectId"),
      versionName.map(v => fr"pv.version_string = $v"),
      if (coarsePlatforms.isEmpty) None
      else
        Some(
          Fragments.or(
            coarsePlatforms.map {
              case (platform, Some(version)) => fr"pvp.platform = $platform AND pvp.platform_coarse_version = $version"
              case (platform, None)          => fr"pvp.platform = $platform"
            }: _*
          )
        ),
      NonEmptyList.fromList(stability).map(Fragments.in(fr"pv.stability", _)),
      NonEmptyList.fromList(releaseType).map(Fragments.in(fr"pv.release_type", _)),
      visibilityFrag
    )

    base ++ filters ++ fr"GROUP BY p.id, pv.id, u.id"
  }

  def versionQuery(
      projectId: DbRef[Project],
      versionName: Option[String],
      platforms: List[(String, Option[String])],
      stability: List[Version.Stability],
      releaseType: List[Version.ReleaseType],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      limit: Long,
      offset: Long
  )(implicit config: OreConfig): Query0[APIV2.Version] =
    (versionSelectFrag(
      projectId,
      versionName,
      platforms,
      stability,
      releaseType,
      canSeeHidden,
      currentUserId
    ) ++ fr"ORDER BY pv.created_at DESC LIMIT $limit OFFSET $offset")
      .query[APIV2QueryVersion]
      .map(_.asProtocol)

  def singleVersionQuery(
      projectId: DbRef[Project],
      versionName: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  )(implicit config: OreConfig): doobie.Query0[APIV2.Version] =
    versionQuery(projectId, Some(versionName), Nil, Nil, Nil, canSeeHidden, currentUserId, 1, 0)

  def versionCountQuery(
      projectId: DbRef[Project],
      platforms: List[(String, Option[String])],
      stability: List[Version.Stability],
      releaseType: List[Version.ReleaseType],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  )(implicit config: OreConfig): Query0[Long] =
    (sql"SELECT COUNT(*) FROM " ++ Fragments.parentheses(
      versionSelectFrag(projectId, None, platforms, stability, releaseType, canSeeHidden, currentUserId)
    ) ++ fr"sq").query[Long]

  def updateVersion(
      projectId: DbRef[Project],
      versionName: String,
      edits: Versions.DbEditableVersion
  ): Update0 = {
    val versionColumns = Versions.DbEditableVersionF[Column](
      Column.arg("stability"),
      Column.opt("release_type")
    )

    (updateTable("project_versions", versionColumns, edits) ++ fr" WHERE project_id = $projectId AND version_string = $versionName").update
  }

  def userSearchFrag(
      q: Option[String],
      minProjects: Int,
      roles: Seq[Role],
      excludeOrganizations: Boolean
  ): Fragment = {
    val initialFilters = Fragments.whereAndOpt(
      q.map(s => if (s.endsWith("%")) fr"u.name LIKE $s" else fr"u.name LIKE ${s + "%"}"),
      if (excludeOrganizations) Some(fr"r IS NULL OR r.name != 'Organization'") else None,
      NonEmptyList.fromList(roles.toList).map(roles => Fragments.in(fr"r.name", roles))
    )
    val outerFilters = Fragments.whereAndOpt(
      if (minProjects > 0) Some(fr"sq.power >= $minProjects") else None
    )

    sql"""|SELECT sq.created_at, sq.name, sq.tagline, sq.join_date, projects, roles
          |    FROM (SELECT u.name,
          |                 u.tagline,
          |                 u.created_at,
          |                 u.join_date,
          |                 count(p.plugin_id)                                           AS projects,
          |                 array_remove(array_agg(DISTINCT r.name), NULL)               AS roles,
          |                 coalesce((bit_or(r.permission) & ~B'1'::BIT(64))::BIGINT, 0) AS power
          |              FROM users u
          |                       LEFT JOIN project_members_all pma ON u.id = pma.user_id
          |                       LEFT JOIN projects p ON p.id = pma.id
          |                       LEFT JOIN user_global_roles ugr ON u.id = ugr.user_id
          |                       LEFT JOIN roles r ON ugr.role_id = r.ID
          |              $initialFilters
          |              GROUP BY u.name, u.tagline, u.join_date, u.created_at) sq
          |    $outerFilters""".stripMargin
  }

  def userSearchQuery(
      q: Option[String],
      minProjects: Int,
      roles: Seq[Role],
      excludeOrganizations: Boolean,
      strategy: Users.UserSortingStrategy,
      sortDescending: Boolean,
      limit: Long,
      offset: Long
  ): Query0[APIV2.User] = {
    val select = userSearchFrag(q, minProjects, roles, excludeOrganizations)

    val primaryRawSort = strategy match {
      case UserSortingStrategy.Name     => fr"sq.name"
      case UserSortingStrategy.Roles    => fr"sq.power"
      case UserSortingStrategy.Joined   => fr"sq.join_date"
      case UserSortingStrategy.Projects => fr"sq.projects"
    }
    val primarySort = if (sortDescending) primaryRawSort ++ fr"DESC" else primaryRawSort ++ fr"ASC"
    val sortFrag    = if (strategy != UserSortingStrategy.Name) primarySort ++ fr", sq.name" else primarySort

    (select ++ fr" ORDER BY" ++ sortFrag ++ fr"LIMIT $limit OFFSET $offset").query[APIV2QueryUser].map(_.asProtocol)
  }

  def userSearchCountQuery(
      q: Option[String],
      minProjects: Int,
      roles: Seq[Role],
      excludeOrganizations: Boolean
  ): Query0[Long] = {
    val select = userSearchFrag(q, minProjects, roles, excludeOrganizations)

    (sql"SELECT COUNT(*) FROM " ++ Fragments.parentheses(select) ++ fr" sq").query[Long]
  }

  def userQuery(name: String): Query0[APIV2.User] =
    sql"""|SELECT u.created_at,
          |       u.name,
          |       u.tagline,
          |       u.join_date,
          |       count(DISTINCT p.plugin_id),
          |       array_remove(array_agg(DISTINCT r.name), NULL)
          |    FROM users u
          |             LEFT JOIN user_global_roles ugr ON u.id = ugr.user_id
          |             LEFT JOIN roles r ON ugr.role_id = r.id
          |             LEFT JOIN project_members_all pma ON u.id = pma.user_id
          |             LEFT JOIN projects p ON p.id = pma.id
          |    WHERE u.name = $name
          |    GROUP BY u.id""".stripMargin.query[APIV2QueryUser].map(_.asProtocol)

  def organizationQuery(name: String): Query0[APIV2.Organization] =
    sql"""|SELECT ou.name,
          |       u.created_at,
          |       u.name,
          |       u.tagline,
          |       u.join_date,
          |       count(DISTINCT p.plugin_id),
          |       array_remove(array_agg(DISTINCT r.name), NULL)
          |    FROM organizations o
          |             JOIN users u ON o.user_id = u.id
          |             JOIN users ou ON o.owner_id = ou.id
          |             LEFT JOIN user_global_roles ugr ON u.id = ugr.user_id
          |             LEFT JOIN roles r ON ugr.role_id = r.id
          |             LEFT JOIN project_members_all pma ON u.id = pma.user_id
          |             LEFT JOIN projects p ON p.id = pma.id
          |    WHERE u.name = $name
          |    GROUP BY ou.id, u.id""".stripMargin.query[APIV2QueryOrganization].map(_.asProtocol)

  def getMemberships(user: String) =
    sql"""|SELECT 'organization', o.name, NULL, NULL, NULL, uor.role_type, uor.is_accepted
          |    FROM user_organization_roles uor
          |             JOIN users u ON uor.user_id = u.id
          |             JOIN organizations o ON uor.organization_id = o.id
          |    WHERE u.name = $user
          |UNION
          |SELECT 'project', NULL, p.plugin_id, p.owner_name, p.slug, upr.role_type, upr.is_accepted
          |    FROM user_project_roles upr
          |             JOIN users u ON upr.user_id = u.id
          |             JOIN projects p ON upr.project_id = p.id
          |    WHERE u.name = $user""".stripMargin.query[APIV2QueryMembership].map(_.asProtocol)

  private def actionFrag(
      table: Fragment,
      user: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  ): Fragment = {
    val base =
      sql"""|SELECT p.plugin_id,
            |       p.name,
            |       p.owner_name,
            |       p.slug,
            |       to_jsonb(
            |               ARRAY(SELECT jsonb_build_object(
            |                                    'version_string', promoted.version_string,
            |                                    'platforms', promoted.platforms,
            |                                    'platform_versions', promoted.platform_versions,
            |                                    'platform_coarse_versions', promoted.platform_coarse_versions,
            |                                    'stability', promoted.stability,
            |                                    'release_type', promoted.release_type)
            |                         FROM promoted_versions promoted
            |                         WHERE promoted.project_id = p.id
            |                         ORDER BY promoted.platform_coarse_versions DESC LIMIT 5)) AS promoted_versions,
            |       ps.views,
            |       ps.downloads,
            |       ps.recent_views,
            |       ps.recent_downloads,
            |       ps.stars,
            |       ps.watchers,
            |       p.category,
            |       p.visibility
            |    FROM users u JOIN """.stripMargin ++ table ++
        fr"""|psw ON u.id = psw.user_id
             |             JOIN projects p ON psw.project_id = p.id JOIN project_stats ps ON psw.project_id = ps.id""".stripMargin

    val visibilityFrag =
      if (canSeeHidden) None
      else
        currentUserId.fold(Some(fr"(p.visibility = 1 OR p.visibility = 2)")) { id =>
          Some(
            fr"(p.visibility = 1 OR p.visibility = 2 OR ($id IN (SELECT pm.user_id FROM project_members_all pm WHERE pm.id = p.id) AND p.visibility != 5))"
          )
        }

    val filters = Fragments.whereAndOpt(
      Some(fr"u.name = $user"),
      visibilityFrag
    )

    base ++ filters
  }

  private def actionQuery(
      table: Fragment,
      user: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      order: ProjectSortingStrategy,
      limit: Long,
      offset: Long
  ): Query0[Either[DecodingFailure, APIV2.CompactProject]] = {
    val ordering = order.fragment

    val select = actionFrag(table, user, canSeeHidden, currentUserId)
    (select ++ fr"ORDER BY" ++ ordering ++ fr"LIMIT $limit OFFSET $offset")
      .query[APIV2QueryCompactProject]
      .map(_.asProtocol)
  }

  private def actionCountQuery(
      table: Fragment,
      user: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  ): Query0[Long] = {
    val select = actionFrag(table, user, canSeeHidden, currentUserId)
    (sql"SELECT COUNT(*) FROM " ++ Fragments.parentheses(select) ++ fr"sq").query[Long]
  }

  def starredQuery(
      user: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      order: ProjectSortingStrategy,
      limit: Long,
      offset: Long
  ): Query0[Either[DecodingFailure, APIV2.CompactProject]] =
    actionQuery(Fragment.const("project_stars"), user, canSeeHidden, currentUserId, order, limit, offset)

  def starredCountQuery(
      user: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  ): Query0[Long] = actionCountQuery(Fragment.const("project_stars"), user, canSeeHidden, currentUserId)

  def watchingQuery(
      user: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      order: ProjectSortingStrategy,
      limit: Long,
      offset: Long
  ): Query0[Either[DecodingFailure, APIV2.CompactProject]] =
    actionQuery(Fragment.const("project_watchers"), user, canSeeHidden, currentUserId, order, limit, offset)

  def watchingCountQuery(
      user: String,
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]]
  ): Query0[Long] = actionCountQuery(Fragment.const("project_watchers"), user, canSeeHidden, currentUserId)

  def projectStats(
      projectId: DbRef[Project],
      startDate: LocalDate,
      endDate: LocalDate
  ): Query0[APIV2ProjectStatsQuery] =
    sql"""|SELECT CAST(dates.day AS DATE), coalesce(sum(pvd.downloads), 0) AS downloads, coalesce(pv.views, 0) AS views
          |    FROM projects p,
          |         (SELECT generate_series($startDate::DATE, $endDate::DATE, INTERVAL '1 DAY') AS day) dates
          |             LEFT JOIN project_versions_downloads pvd ON dates.day = pvd.day
          |             LEFT JOIN project_views pv ON dates.day = pv.day AND pvd.project_id = pv.project_id
          |    WHERE p.id = $projectId
          |      AND (pvd IS NULL OR pvd.project_id = p.id)
          |    GROUP BY pv.views, dates.day;""".stripMargin.query[APIV2ProjectStatsQuery]

  def versionStats(
      projectId: DbRef[Project],
      versionString: String,
      startDate: LocalDate,
      endDate: LocalDate
  ): Query0[APIV2VersionStatsQuery] =
    sql"""|SELECT CAST(dates.day AS DATE), coalesce(pvd.downloads, 0) AS downloads
          |    FROM project_versions pv,
          |         (SELECT generate_series($startDate::DATE, $endDate::DATE, INTERVAL '1 DAY') AS day) dates
          |             LEFT JOIN project_versions_downloads pvd ON dates.day = pvd.day
          |    WHERE pv.project_id = $projectId
          |      AND pv.version_string = $versionString
          |      AND (pvd IS NULL OR (pvd.project_id = $projectId AND pvd.version_id = pv.id));""".stripMargin
      .query[APIV2VersionStatsQuery]

  def canUploadToOrg(uploader: DbRef[User], orgName: String): Query0[(DbRef[User], Boolean)] =
    sql"""|SELECT ou.id,
          |       ((coalesce(gt.permission, B'0'::BIT(64)) | coalesce(ot.permission, B'0'::BIT(64))) & (1::BIT(64) << 9)) =
          |       (1::BIT(64) << 9)
          |    FROM organizations o
          |             JOIN users ou ON o.user_id = ou.id
          |             LEFT JOIN user_organization_roles om ON o.id = om.organization_id AND om.user_id = $uploader
          |             LEFT JOIN global_trust gt ON gt.user_id = om.user_id
          |             LEFT JOIN organization_trust ot ON ot.user_id = om.user_id AND ot.organization_id = o.id
          |    WHERE o.name = $orgName;""".stripMargin.query[(DbRef[User], Boolean)]

  def getPage(projectId: DbRef[Project], page: String): Query0[(DbRef[Page], String, Option[String])] =
    sql"""|WITH RECURSIVE pages_rec(n, name, slug, contents, id) AS (
          |    SELECT 2, pp.name, pp.slug, pp.contents, pp.id
          |        FROM project_pages pp
          |        WHERE pp.project_id = $projectId
          |          AND lower(split_part($page, '/', 1)) = lower(pp.slug)
          |          AND pp.parent_id IS NULL
          |    UNION
          |    SELECT pr.n + 1, pp.name, pp.slug, pp.contents, pp.id
          |        FROM pages_rec pr,
          |             project_pages pp
          |        WHERE pp.project_id = $projectId
          |          AND pp.parent_id = pr.id
          |          AND lower(split_part($page, '/', pr.n)) = lower(pp.slug)
          |)
          |SELECT pp.id, pp.name, pp.contents
          |    FROM pages_rec pp
          |    WHERE lower(pp.slug) = lower(split_part($page, '/', array_length(regexp_split_to_array($page, '/'), 1)));""".stripMargin
      .query[(DbRef[Page], String, Option[String])]

  def pageList(projectId: DbRef[Project]): Query0[(DbRef[Page], List[String], List[String], Boolean)] =
    sql"""|WITH RECURSIVE pages_rec(name, slug, id, navigational) AS (
          |    SELECT ARRAY[pp.name]::TEXT[], ARRAY[pp.slug]::TEXT[], pp.id, pp.contents IS NULL
          |        FROM project_pages pp
          |        WHERE pp.project_id = $projectId
          |          AND pp.parent_id IS NULL
          |    UNION
          |    SELECT array_append(pr.name, pp.name::TEXT), array_append(pr.slug, pp.slug::TEXT), pp.id, pp.contents IS NULL
          |        FROM pages_rec pr,
          |             project_pages pp
          |        WHERE pp.project_id = $projectId
          |          AND pp.parent_id = pr.id
          |)
          |SELECT pp.id, pp.name, pp.slug, navigational
          |    FROM pages_rec pp ORDER BY pp.name;""".stripMargin
      .query[(DbRef[Page], List[String], List[String], Boolean)]

  def patchPage(
      patch: Pages.PatchPageF[Option],
      newSlug: Option[String],
      id: DbRef[Page],
      parentId: Option[Option[DbRef[Page]]]
  ): doobie.Update0 = {
    val sets = Fragments.setOpt(
      patch.name.map(n => fr"name = $n"),
      newSlug.map(n => fr"slug = $n"),
      patch.content.map(c => fr"contents = $c"),
      parentId.map(p => fr"parent_id = $p")
    )
    (sql"UPDATE project_pages " ++ sets ++ fr"WHERE id = $id").update
  }

}
