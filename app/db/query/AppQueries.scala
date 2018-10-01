package db.query

import db.ObjectReference
import models.querymodels._
import ore.project.{Category, ProjectSortingStrategy}

import cats.syntax.all._
import doobie._
import doobie.implicits._

object AppQueries extends DoobieOreProtocol {

  //implicit val logger: LogHandler = createLogger("AppQueries")

  def getHomeProjects(
      currentUserId: ObjectReference,
      canSeeHidden: Boolean,
      platformNames: List[String],
      categories: List[Category],
      query: String,
      order: ProjectSortingStrategy,
      offset: Int,
      pageSize: Int
  ): Query0[ProjectListEntry] = {

    val platformFrag =
      platformNames.toNel.map(Fragments.in(fr"SELECT version_id FROM project_version_tags WHERE lower(name)", _))
    val categoryFrag = categories.toNel.map(Fragments.in(fr"p.category", _))

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
            |       v.version_string,
            |       COALESCE((SELECT array_agg(t.name) AS name FROM project_version_tags t WHERE t.version_id = v.id), ARRAY[]::VARCHAR(255)[]),
            |       COALESCE((SELECT array_agg(t.data) AS data FROM project_version_tags t WHERE t.version_id = v.id), ARRAY[]::VARCHAR(255)[]),
            |       COALESCE((SELECT array_agg(t.color) AS color FROM project_version_tags t WHERE t.version_id = v.id), ARRAY[]::INTEGER[])
            |  FROM projects p
            |         JOIN project_versions v ON p.recommended_version_id = v.id
            |         JOIN users u ON p.owner_id = u.id
            |  WHERE ($canSeeHidden OR p.visibility = 1 OR p.visibility = 2 OR (p.owner_id = $currentUserId AND p.visibility != 5))
            |    AND (lower(p.name) LIKE $query
            |           OR lower(p.description) LIKE $query
            |           OR lower(p.owner_name) LIKE $query
            |           OR lower(p.plugin_id) LIKE $query) """.stripMargin ++
        Fragments.andOpt(platformFrag, categoryFrag) ++
        fr"ORDER BY" ++ order.fragment ++
        fr"LIMIT $pageSize OFFSET $offset"

    fragments.query[ProjectListEntry]
  }

  val getQueue: Query0[UnsortedQueueEntry] = {
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
          |          WHERE v.is_reviewed = FALSE
          |            AND v.is_non_reviewed = FALSE
          |            AND p.visibility != 5) sq
          |  WHERE row = 1
          |  ORDER BY sq.project_name DESC, sq.version_string DESC""".stripMargin.query[UnsortedQueueEntry]
  }

  val getUnhealtyProjects: Query0[UnhealtyProject] = {
    sql"""|SELECT p.owner_name, p.slug, p.topic_id, p.post_id, p.is_topic_dirty, p.last_updated, p.visibility
          |  FROM projects p
          |  WHERE p.topic_id IS NULL
          |     OR p.post_id IS NULL
          |     OR p.is_topic_dirty
          |     OR p.last_updated > now()
          |     OR p.visibility != 1""".stripMargin
      .query[UnhealtyProject]
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

  def getStats(skippedDays: Int, daysBack: Int): Query0[Stats] = {
    sql"""|SELECT (SELECT COUNT(*) FROM project_version_reviews WHERE CAST(ended_at AS DATE) = day)            AS review_count,
          |       (SELECT COUNT(*) FROM project_versions WHERE CAST(created_at AS DATE) = day)                 AS created_projects,
          |       (SELECT COUNT(*) FROM project_version_downloads WHERE CAST(created_at AS DATE) = day)        AS download_count,
          |       (SELECT COUNT(*)
          |          FROM project_version_unsafe_downloads
          |          WHERE CAST(created_at AS DATE) = day)                                                     AS unsafe_download_count,
          |       (SELECT COUNT(*)
          |          FROM project_flags
          |          WHERE CAST(created_at AS DATE) <= day
          |            AND (CAST(resolved_at AS DATE) >= day OR resolved_at IS NULL))                          AS flags_created,
          |       (SELECT COUNT(*) FROM project_flags WHERE CAST(resolved_at AS DATE) = day)                   AS flags_resolved,
          |       CAST(day AS DATE)
          |  FROM (SELECT CURRENT_DATE - (INTERVAL '1 day' * generate_series($skippedDays, $daysBack)) AS day) dates
          |  ORDER BY day ASC""".stripMargin.query[Stats]
  }

  //TODO: Only latest changes
  val getVisibilityNeedsApproval = {
    sql"""|SELECT p.id              AS project_id,
          |       p.owner_name,
          |       p.slug,
          |       p.visibility,
          |       vcr.comment       AS change_request_comment,
          |       uvcr.name         AS change_requester,
          |       vc.id IS NOT NULL AS has_previous_change,
          |       uvc.name          AS last_changer
          |  FROM projects p
          |         LEFT JOIN project_visibility_changes vc ON p.id = vc.project_id
          |         LEFT JOIN project_visibility_changes vcr ON p.id = vcr.project_id
          |         LEFT JOIN users uvc ON vc.created_by = uvc.id
          |         LEFT JOIN users uvcr ON vcr.created_by = uvcr.id
          |  WHERE (vcr.visibility IS NULL OR vcr.visibility = 3)
          |    AND (vc.visibility IS NULL OR vc.resolved_at IS NULL)
          |    AND p.visibility = 4""".stripMargin.query[VisibilityNeedApprovalProject]
  }

  //TODO: Only latest changes
  val getVisibilityWaitingProject = {
    sql"""|SELECT p.id              AS project_id,
          |       p.owner_name,
          |       p.slug,
          |       vcr.comment       AS change_request_comment,
          |       uvc.name          AS last_changer
          |  FROM projects p
          |         LEFT JOIN project_visibility_changes vc ON p.id = vc.project_id
          |         LEFT JOIN project_visibility_changes vcr ON p.id = vcr.project_id
          |         LEFT JOIN users uvc ON vc.created_by = uvc.id
          |  WHERE (vcr.visibility IS NULL OR vcr.visibility = 3)
          |    AND (vc.visibility IS NULL OR vc.resolved_at IS NULL)
          |    AND p.visibility = 3""".stripMargin.query[VisibilityWaitingProject]
  }
}
