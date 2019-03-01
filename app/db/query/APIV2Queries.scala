package db.query

import java.sql.Timestamp
import java.time.LocalDateTime

import db.DbRef
import models.querymodels._
import models.user.User
import ore.project.{Category, ProjectSortingStrategy}

import cats.data.NonEmptyList
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object APIV2Queries extends DoobieOreProtocol {

  implicit val localDateTimeMeta: Meta[LocalDateTime] = Meta[Timestamp].timap(_.toLocalDateTime)(Timestamp.valueOf)

  def projectQuery(
      pluginId: Option[String],
      category: List[Category],
      tags: List[String],
      query: Option[String],
      owner: Option[String],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
      order: ProjectSortingStrategy,
      orderWithRelevance: Boolean,
      limit: Long,
      offset: Long
  ): Query0[APIV2Project] = {
    val userActionsTaken = currentUserId.fold(fr"FALSE, FALSE,") { id =>
      fr"""|EXISTS(SELECT * FROM project_stars s WHERE s.project_id = p.id AND s.user_id = $id)    AS user_stared,
           |EXISTS(SELECT * FROM project_watchers s WHERE s.project_id = p.id AND s.user_id = $id) AS user_watching,""".stripMargin
    }

    val base =
      sql"""|SELECT p.created_at,
       |       p.plugin_id,
       |       p.name,
       |       p.owner_name,
       |       p.slug,
       |       p.version_string,
       |       array_agg(p.tag_name) FILTER ( WHERE p.tag_name IS NOT NULL)                              AS tag_names,
       |       array_agg(p.tag_data) FILTER ( WHERE p.tag_data IS NOT NULL)                              AS tag_names,
       |       array_agg(p.tag_color) FILTER ( WHERE p.tag_color IS NOT NULL)                            AS tag_names,
       |       p.views,
       |       p.downloads,
       |       p.stars,
       |       p.category,
       |       p.description,
       |       p.last_updated,
       |       p.visibility, """ ++ userActionsTaken ++
        fr"""|       EXISTS(SELECT * FROM project_stars s WHERE s.project_id = p.id AND s.user_id = 19023)    AS user_stared,
             |       EXISTS(SELECT * FROM project_watchers s WHERE s.project_id = p.id AND s.user_id = 19023) AS user_watching,
             |       ps.homepage,
             |       ps.issues,
             |       ps.source,
             |       ps.license_name,
             |       ps.license_url,
             |       ps.forum_sync
             |  FROM home_projects p
             |         JOIN project_settings ps ON p.id = ps.project_id """.stripMargin
    val groupBy =
      fr"""|GROUP BY p.created_at, p.plugin_id, p.name, p.owner_name, p.slug, p.version_string, p.views, p.downloads, p.stars,
           |           p.category, p.description, p.last_updated, p.visibility, p.id, ps.id""".stripMargin

    val visibilityFrag =
      if (canSeeHidden) None
      else
        currentUserId.fold(Some(fr"(p.visibility = 1 OR p.visibility = 2)")) { id =>
          Some(fr"(p.visibility = 1 OR p.visibility = 2 OR (p.owner_id = $id AND p.visibility != 5))")
        }

    val filters = Fragments.whereAndOpt(
      pluginId.map(id => fr"p.plugin_id = $id"),
      NonEmptyList.fromList(category).map(Fragments.in(fr"p.category", _)),
      NonEmptyList
        .fromList(tags)
        .map(t => Fragments.or(Fragments.in(fr"p.tag_name || p.tag_data", t), Fragments.in(fr"p.tag_name", t))),
      query.map(q => fr"p.search_words @@ websearch_to_tsquery($q)"),
      owner.map(o => fr"p.owner_name = $o"),
      visibilityFrag
    )

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

    (base ++ filters ++ ordering ++ groupBy ++ fr"LIMIT $limit OFFSET $offset").query[APIV2Project]
  }

  def projectMembers(pluginId: String, limit: Long, offset: Long): Query0[APIV2ProjectMember] =
    sql"""|SELECT u.name, array_agg(r.name)
          |  FROM projects p
          |         JOIN user_project_roles upr ON p.id = upr.project_id
          |         JOIN users u ON upr.user_id = u.id
          |         JOIN roles r ON upr.role_type = r.name
          |  WHERE p.plugin_id = $pluginId
          |  GROUP BY u.name LIMIT $limit OFFSET $offset""".stripMargin.query[APIV2ProjectMember]

  def versionQuery(
      pluginId: String,
      versionName: Option[String],
      tags: List[String],
      limit: Long,
      offset: Long
  ): Query0[APIV2Version] = {
    val base =
      sql"""|SELECT pv.created_at,
            |       pv.version_string,
            |       pv.dependencies,
            |       pv.description,
            |       pv.downloads,
            |       pv.file_size,
            |       pv.hash,
            |       pv.file_name,
            |       u.name,
            |       pv.review_state,
            |       array_agg(pvt.name),
            |       array_agg(pvt.data),
            |       array_agg(pvt.color)
            |  FROM projects p
            |         JOIN project_versions pv ON p.id = pv.project_id
            |         LEFT JOIN users u ON pv.author_id = u.id
            |         LEFT JOIN project_version_tags pvt ON pv.id = pvt.version_id """.stripMargin

    val filters = Fragments.whereAndOpt(
      Some(fr"p.plugin_id = $pluginId"),
      versionName.map(v => fr"pv.version_string = $v"),
      NonEmptyList
        .fromList(tags)
        .map(t => Fragments.or(Fragments.in(fr"pvt.name || pvt.data", t), Fragments.in(fr"pvt.name", t)))
    )

    (base ++ filters ++ fr"GROUP BY pv.id, u.id LIMIT $limit OFFSET $offset").stripMargin.query[APIV2Version]
  }

  def userQuery(name: String): Query0[APIV2User] =
    sql"""|SELECT u.created_at, u.name, u.tagline, u.join_date, array_agg(r.name)
          |  FROM users u
          |         JOIN user_global_roles ugr ON u.id = ugr.user_id
          |         JOIN roles r ON ugr.role_id = r.id
          |  WHERE u.name = $name
          |  GROUP BY u.id""".stripMargin.query[APIV2User]

}
