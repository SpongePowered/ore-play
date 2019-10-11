package db.impl.query

import java.time.LocalDate

import scala.concurrent.duration.FiniteDuration

import models.querymodels._
import ore.data.project.Category
import ore.db.{DbRef, Model}
import ore.models.admin.LoggedActionViewModel
import ore.models.organization.Organization
import ore.models.project._
import ore.models.user.User

import cats.data.NonEmptyList
import cats.syntax.all._
import doobie._
import doobie.implicits._

object AppQueries extends WebDoobieOreProtocol {

  //implicit val logger: LogHandler = createLogger("Database")

  val getQueue: Query0[UnsortedQueueEntry] = {
    val reviewStateId = ReviewState.Unreviewed.value
    sql"""|SELECT sq.project_author,
          |       sq.project_slug,
          |       sq.project_name,
          |       sq.version_string,
          |       sq.version_created_at,
          |       sq.channel_name,
          |       sq.channel_color,
          |       sq.version_author,
          |       sq.reviewer_id,
          |       sq.reviewer_name,
          |       sq.review_started,
          |       sq.review_ended
          |  FROM (SELECT pu.name                                                                  AS project_author,
          |               p.name                                                                   AS project_name,
          |               p.slug                                                                   AS project_slug,
          |               v.version_string,
          |               v.created_at                                                             AS version_created_at,
          |               c.name                                                                   AS channel_name,
          |               c.color                                                                  AS channel_color,
          |               vu.name                                                                  AS version_author,
          |               r.user_id                                                                AS reviewer_id,
          |               ru.name                                                                  AS reviewer_name,
          |               r.created_at                                                             AS review_started,
          |               r.ended_at                                                               AS review_ended,
          |               row_number() OVER (PARTITION BY (p.id, v.id) ORDER BY r.created_at DESC) AS row
          |          FROM project_versions v
          |                 LEFT JOIN users vu ON v.author_id = vu.id
          |                 INNER JOIN project_channels c ON v.channel_id = c.id
          |                 INNER JOIN projects p ON v.project_id = p.id
          |                 INNER JOIN users pu ON p.owner_id = pu.id
          |                 LEFT JOIN project_version_reviews r ON v.id = r.version_id
          |                 LEFT JOIN users ru ON ru.id = r.user_id
          |          WHERE v.review_state = $reviewStateId
          |            AND p.visibility != 5
          |            AND v.visibility != 5) sq
          |  WHERE row = 1
          |  ORDER BY sq.project_name DESC, sq.version_string DESC""".stripMargin.query[UnsortedQueueEntry]
  }

  val flags: Query0[ShownFlag] = {
    sql"""|SELECT pf.id        AS flag_id,
          |       pf.created_at AS flag_creation_date,
          |       pf.reason    AS flag_reason,
          |       pf.comment   AS flag_comment,
          |       fu.name      AS reporter,
          |       p.owner_name AS project_owner_name,
          |       p.slug       AS project_slug,
          |       p.visibility AS project_visibility
          |  FROM project_flags pf
          |         JOIN projects p ON pf.project_id = p.id
          |         JOIN users fu ON pf.user_id = fu.id
          |  WHERE NOT pf.is_resolved
          |  GROUP BY pf.id, fu.id, p.id;""".stripMargin.query[ShownFlag]
  }

  def getUnhealtyProjects(staleTime: FiniteDuration): Query0[UnhealtyProject] = {
    sql"""|SELECT p.owner_name, p.slug, p.topic_id, p.post_id, p.is_topic_dirty, coalesce(hp.last_updated, p.created_at), p.visibility
          |  FROM projects p JOIN home_projects hp ON p.id = hp.id
          |  WHERE p.topic_id IS NULL
          |     OR p.post_id IS NULL
          |     OR p.is_topic_dirty
          |     OR hp.last_updated > (now() - $staleTime::INTERVAL)
          |     OR p.visibility != 1""".stripMargin.query[UnhealtyProject]
  }

  def getReviewActivity(username: String): Query0[ReviewActivity] = {
    sql"""|SELECT pvr.ended_at, pvr.id, p.owner_name, p.slug
          |  FROM users u
          |         JOIN project_version_reviews pvr ON u.id = pvr.user_id
          |         JOIN project_versions pv ON pvr.version_id = pv.id
          |         JOIN projects p ON pv.project_id = p.id
          |  WHERE u.name = $username
          |  LIMIT 20""".stripMargin.query[ReviewActivity]
  }

  def getFlagActivity(username: String): Query0[FlagActivity] = {
    sql"""|SELECT pf.resolved_at, p.owner_name, p.slug
          |  FROM users u
          |         JOIN project_flags pf ON u.id = pf.user_id
          |         JOIN projects p ON pf.project_id = p.id
          |  WHERE u.name = $username
          |  LIMIT 20""".stripMargin.query[FlagActivity]
  }

  def getStats(startDate: LocalDate, endDate: LocalDate): Query0[Stats] = {
    sql"""|SELECT (SELECT COUNT(*) FROM project_version_reviews WHERE CAST(ended_at AS DATE) = day)     AS review_count,
          |       (SELECT COUNT(*) FROM project_versions WHERE CAST(created_at AS DATE) = day)          AS created_projects,
          |       (SELECT COUNT(*) FROM project_version_downloads WHERE CAST(created_at AS DATE) = day) AS download_count,
          |       (SELECT COUNT(*)
          |          FROM project_version_unsafe_downloads
          |          WHERE CAST(created_at AS DATE) = day)                                              AS unsafe_download_count,
          |       (SELECT COUNT(*)
          |          FROM project_flags
          |          WHERE CAST(created_at AS DATE) <= day
          |            AND (CAST(resolved_at AS DATE) >= day OR resolved_at IS NULL))                   AS flags_created,
          |       (SELECT COUNT(*) FROM project_flags WHERE CAST(resolved_at AS DATE) = day)            AS flags_resolved,
          |       CAST(day AS DATE)
          |  FROM (SELECT generate_series($startDate::DATE, $endDate::DATE, INTERVAL '1 DAY') AS day) dates
          |  ORDER BY day ASC;""".stripMargin.query[Stats]
  }

  def getLog(
      oPage: Option[Int],
      userFilter: Option[String],
      projectFilter: Option[String],
      versionFilter: Option[String],
      pageFilter: Option[DbRef[Page]],
      actionFilter: Option[String],
      subjectFilter: Option[String]
  ): Query0[Model[LoggedActionViewModel[Any]]] = {
    val pageSize = 50L
    val page     = oPage.getOrElse(1)
    val offset   = (page - 1) * pageSize

    val frags = sql"SELECT * FROM v_logged_actions la " ++ Fragments.whereAndOpt(
      userFilter.map(name => fr"la.user_name = $name"),
      projectFilter.map(id => fr"la.p_plugin_id = $id"),
      versionFilter.map(id => fr"la.pv_version_string = $id"),
      pageFilter.map(id => fr"la.pp_id = $id"),
      actionFilter.map(action => fr"la.action = $action::LOGGED_ACTION_TYPE"),
      subjectFilter.map(subject => fr"la.s_name = $subject")
    ) ++ fr"ORDER BY la.created_at DESC OFFSET $offset LIMIT $pageSize"

    frags.query[Model[LoggedActionViewModel[Any]]]
  }

  val getVisibilityNeedsApproval: Query0[ProjectNeedsApproval] = {
    sql"""|SELECT sq.owner_name,
          |       sq.slug,
          |       sq.visibility,
          |       sq.last_comment,
          |       u.name AS change_requester
          |  FROM (SELECT p.owner_name,
          |               p.slug,
          |               p.visibility,
          |               vc.resolved_at,
          |               lag(vc.comment) OVER last_vc    AS last_comment,
          |               lag(vc.visibility) OVER last_vc AS last_visibility,
          |               lag(vc.created_by) OVER last_vc AS last_changer
          |          FROM projects p
          |                 JOIN project_visibility_changes vc ON p.id = vc.project_id
          |          WHERE p.visibility = 4 WINDOW last_vc AS (PARTITION BY p.id ORDER BY vc.created_at)) sq
          |         JOIN users u ON sq.last_changer = u.id
          |  WHERE sq.resolved_at IS NULL
          |    AND sq.last_visibility = 3
          |  ORDER BY sq.owner_name || sq.slug""".stripMargin.query[ProjectNeedsApproval]
  }

  val getVisibilityWaitingProject: Query0[ProjectNeedsApproval] = {
    sql"""|SELECT p.owner_name, p.slug, p.visibility, vc.comment, u.name AS change_requester
          |  FROM projects p
          |         JOIN project_visibility_changes vc ON p.id = vc.project_id
          |         JOIN users u ON vc.created_by = u.id
          |  WHERE vc.resolved_at IS NULL
          |    AND p.visibility = 3""".stripMargin.query[ProjectNeedsApproval]
  }

  val sitemapIndexUsers: Query0[String] = {
    sql"""|SELECT u.name
          |    FROM users u
          |    ORDER BY (SELECT COUNT(*) FROM project_members_all pma WHERE pma.user_id = u.id) DESC
          |    LIMIT 49000""".stripMargin.query[String]
  }

  def apiV1IdSearch(
      q: Option[String],
      categories: List[Category],
      ordering: ProjectSortingStrategy,
      limit: Int,
      offset: Int
  ): Query0[DbRef[Project]] = {
    val query = s"%${q.getOrElse("")}%"
    val queryFilter =
      fr"p.name ILIKE $query OR p.description ILIKE $query OR p.owner_name ILIKE $query OR p.plugin_id ILIKE $query"
    val catFilter = NonEmptyList.fromList(categories).map(Fragments.in(fr"p.category", _))

    val res = (
      sql"SELECT p.id FROM home_projects p " ++
        Fragments.whereAndOpt(Some(queryFilter), catFilter) ++
        fr"ORDER BY" ++
        ordering.fragment ++
        fr"LIMIT $limit OFFSET $offset"
    ).query[DbRef[Project]]

    println(res.sql)
    res
  }
}
