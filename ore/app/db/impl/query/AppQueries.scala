package db.impl.query

import java.time.LocalDate

import scala.concurrent.duration.FiniteDuration

import models.querymodels._
import ore.data.project.Category
import ore.db.{DbRef, Model}
import ore.models.admin.LoggedActionViewModel
import ore.models.project.{Page, Project, ProjectSortingStrategy, ReviewState, Version}
import ore.models.user.User

import cats.syntax.all._
import doobie._
import doobie.implicits._

object AppQueries extends WebDoobieOreProtocol {

  //implicit val logger: LogHandler = createLogger("Database")

  def getHomeProjects(
      currentUserId: Option[DbRef[User]],
      canSeeHidden: Boolean,
      platformNames: List[String],
      categories: List[Category],
      query: Option[String],
      order: ProjectSortingStrategy,
      offset: Long,
      pageSize: Long,
      orderWithRelevance: Boolean
  ): Query0[ProjectListEntry] = {
    //TODO: Let the query handle tag search in the future
    val platformFrag = platformNames.map(_.toLowerCase).toNel.map(Fragments.in(fr"lower(p.tag_name)", _))
    val categoryFrag = categories.toNel.map(Fragments.in(fr"p.category", _))
    val visibilityFrag =
      if (canSeeHidden) None
      else
        currentUserId.fold(Some(fr"(p.visibility = 1)")) { id =>
          Some(fr"(p.visibility = 1 OR (p.owner_id = $id AND p.visibility != 5))")
        }
    val queryFrag = query.map(q => fr"p.search_words @@ websearch_to_tsquery($q)")

    val ordering = if (orderWithRelevance && query.nonEmpty) {
      val relevance = query.fold(fr"1") { q =>
        fr"ts_rank(p.search_words, websearch_to_tsquery($q)) DESC"
      }
      order match {
        case ProjectSortingStrategy.MostStars       => fr"p.stars *" ++ relevance
        case ProjectSortingStrategy.MostDownloads   => fr"p.downloads*" ++ relevance
        case ProjectSortingStrategy.MostViews       => fr"p.views *" ++ relevance
        case ProjectSortingStrategy.Newest          => fr"extract(EPOCH from p.created_at) *" ++ relevance
        case ProjectSortingStrategy.RecentlyUpdated => fr"extract(EPOCH from p.last_updated) *" ++ relevance
        case ProjectSortingStrategy.OnlyRelevance   => relevance
      }
    } else order.fragment

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
            |  FROM home_projects p """.stripMargin ++
        Fragments.whereAndOpt(visibilityFrag, queryFrag, platformFrag, categoryFrag) ++
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
        fr"ORDER BY" ++ ordering ++
        fr"LIMIT $pageSize OFFSET $offset"

    fragments.query[ProjectListEntry]
  }

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

  def flags(userId: DbRef[User]): Query0[ShownFlag] = {
    sql"""|SELECT pf.id        AS flag_id,
          |       pf.reason    AS flag_reason,
          |       pf.comment   AS flag_comment,
          |       fu.name      AS reporter,
          |       p.owner_name AS project_owner_name,
          |       p.slug       AS project_slug,
          |       p.visibility AS project_visibility
          |  FROM project_flags pf
          |         JOIN projects p ON pf.project_id = p.id
          |         JOIN users fu ON pf.user_id = fu.id
          |         JOIN users ru ON ru.id = 9001
          |         JOIN user_global_roles rgr ON ru.id = rgr.user_id
          |         JOIN roles rr ON rgr.role_id = rr.id
          |  WHERE NOT pf.is_resolved
          |  GROUP BY pf.id, fu.id, p.id;""".stripMargin.query[ShownFlag]
  }

  def getUnhealtyProjects(staleTime: FiniteDuration): Query0[UnhealtyProject] = {
    sql"""|SELECT p.owner_name, p.slug, p.topic_id, p.post_id, p.is_topic_dirty, p.last_updated, p.visibility
          |  FROM projects p
          |  WHERE p.topic_id IS NULL
          |     OR p.post_id IS NULL
          |     OR p.is_topic_dirty
          |     OR p.last_updated > (now() - $staleTime::INTERVAL)
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
      userFilter: Option[DbRef[User]],
      projectFilter: Option[DbRef[Project]],
      versionFilter: Option[DbRef[Version]],
      pageFilter: Option[DbRef[Page]],
      actionFilter: Option[Int],
      subjectFilter: Option[DbRef[_]]
  ): Query0[Model[LoggedActionViewModel[Any]]] = {
    val pageSize = 50L
    val page     = oPage.getOrElse(1)
    val offset   = (page - 1) * pageSize

    val frags = sql"SELECT * FROM v_logged_actions la " ++ Fragments.whereAndOpt(
      userFilter.map(id => fr"la.user_id = $id"),
      projectFilter.map(id => fr"la.filter_project = $id"),
      versionFilter.map(id => fr"la.filter_version = $id"),
      pageFilter.map(id => fr"la.filter_page = $id"),
      actionFilter.map(i => fr"la.filter_action = $i"),
      subjectFilter.map(id => fr"la.filter_subject = $id")
    ) ++ fr"ORDER BY la.id DESC OFFSET $offset LIMIT $pageSize"

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
}
