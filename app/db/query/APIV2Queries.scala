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

  def projectSelectFrag(
      pluginId: Option[String],
      category: List[Category],
      tags: List[String],
      query: Option[String],
      owner: Option[String],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
  ): Fragment = {
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
            |       array_remove(array_append(array_agg(p.tag_name), CASE WHEN pc IS NULL THEN NULL ELSE 'Channel'::VARCHAR(255) END),
            |                    NULL)                                                 AS tag_names,
            |       array_remove(array_append(array_agg(p.tag_data), pc.name), NULL)   AS tag_datas,
            |       array_remove(array_append(array_agg(p.tag_color), pc.color + 9), NULL) AS tag_colors,
            |       p.views,
            |       p.downloads,
            |       p.stars,
            |       p.category,
            |       p.description,
            |       coalesce(p.last_updated, p.created_at)                             AS last_updated,
            |       p.visibility,""".stripMargin ++ userActionsTaken ++
        fr"""|       ps.homepage,
             |       ps.issues,
             |       ps.source,
             |       ps.license_name,
             |       ps.license_url,
             |       ps.forum_sync
             |  FROM home_projects p
             |         JOIN project_settings ps ON p.id = ps.project_id
             |         LEFT JOIN project_channels pc ON p.recommended_version_channel_id = pc.id""".stripMargin
    val groupBy =
      fr"""|GROUP BY p.created_at, p.plugin_id, p.name, p.owner_name, p.slug, p.version_string, p.views, p.downloads, p.stars,
           |           p.category, p.description, p.last_updated, p.visibility, p.id, ps.id, pc.id""".stripMargin

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
        .map { t =>
          fragParens(
            Fragments.or(
              Fragments.in(fr"p.tag_name || ':' || p.tag_data", t),
              Fragments.in(fr"p.tag_name", t),
              Fragments.in(fr"'Channel:' || pc.name", t),
              Fragments.in(fr"'Channel'", t)
            )
          )
        },
      query.map(q => fr"p.search_words @@ websearch_to_tsquery($q)"),
      owner.map(o => fr"p.owner_name = $o"),
      visibilityFrag
    )

    base ++ filters ++ groupBy
  }

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

    val select = projectSelectFrag(pluginId, category, tags, query, owner, canSeeHidden, currentUserId)
    (select ++ fr"ORDER BY" ++ ordering ++ fr"LIMIT $limit OFFSET $offset").query[APIV2Project]
  }

  def projectCountQuery(
      pluginId: Option[String],
      category: List[Category],
      tags: List[String],
      query: Option[String],
      owner: Option[String],
      canSeeHidden: Boolean,
      currentUserId: Option[DbRef[User]],
  ): Query0[Int] = {
    val select = projectSelectFrag(pluginId, category, tags, query, owner, canSeeHidden, currentUserId)
    (sql"SELECT COUNT(*) FROM " ++ fragParens(select) ++ fr"sq").query[Int]
  }

  def projectMembers(pluginId: String, limit: Long, offset: Long): Query0[APIV2ProjectMember] =
    sql"""|SELECT u.name, array_agg(r.name)
          |  FROM projects p
          |         JOIN user_project_roles upr ON p.id = upr.project_id
          |         JOIN users u ON upr.user_id = u.id
          |         JOIN roles r ON upr.role_type = r.name
          |  WHERE p.plugin_id = $pluginId
          |  GROUP BY u.name LIMIT $limit OFFSET $offset""".stripMargin.query[APIV2ProjectMember]

  def versionSelectFrag(
      pluginId: String,
      versionName: Option[String],
      tags: List[String],
  ): Fragment = {
    val base =
      sql"""|SELECT pv.created_at,
            |       pv.version_string,
            |       pv.dependencies,
            |       pv.visibility,
            |       pv.description,
            |       pv.downloads,
            |       pv.file_size,
            |       pv.hash,
            |       pv.file_name,
            |       u.name,
            |       pv.review_state,
            |       array_append(array_agg(pvt.name) FILTER ( WHERE pvt.name IS NOT NULL ), 'Channel')  AS tag_names,
            |       array_append(array_agg(pvt.data) FILTER ( WHERE pvt.data IS NOT NULL ), pc.name)    AS tag_datas,
            |       array_append(array_agg(pvt.color) FILTER ( WHERE pvt.color IS NOT NULL ), pc.color + 9) AS tag_colors
            |    FROM projects p
            |             JOIN project_versions pv ON p.id = pv.project_id
            |             LEFT JOIN users u ON pv.author_id = u.id
            |             LEFT JOIN project_version_tags pvt ON pv.id = pvt.version_id
            |             LEFT JOIN project_channels pc ON pv.channel_id = pc.id """.stripMargin

    val filters = Fragments.whereAndOpt(
      Some(fr"p.plugin_id = $pluginId"),
      versionName.map(v => fr"pv.version_string = $v"),
      NonEmptyList
        .fromList(tags)
        .map { t =>
          fragParens(
            Fragments.or(
              Fragments.in(fr"pvt.name || ':' || pvt.data", t),
              Fragments.in(fr"pvt.name", t),
              Fragments.in(fr"'Channel:' || pc.name", t),
              Fragments.in(fr"'Channel'", t)
            )
          )
        }
    )

    base ++ filters ++ fr"GROUP BY pv.id, u.id, pc.id"
  }

  def versionQuery(
      pluginId: String,
      versionName: Option[String],
      tags: List[String],
      limit: Long,
      offset: Long
  ): Query0[APIV2Version] =
    (versionSelectFrag(pluginId, versionName, tags) ++ fr"ORDER BY pv.created_at DESC LIMIT $limit OFFSET $offset")
      .query[APIV2Version]

  def versionCountQuery(pluginId: String, tags: List[String]): Query0[Int] =
    (sql"SELECT COUNT(*) FROM " ++ fragParens(versionSelectFrag(pluginId, None, tags)) ++ fr"sq").query[Int]

  def userQuery(name: String): Query0[APIV2User] =
    sql"""|SELECT u.created_at, u.name, u.tagline, u.join_date, array_agg(r.name)
          |  FROM users u
          |         JOIN user_global_roles ugr ON u.id = ugr.user_id
          |         JOIN roles r ON ugr.role_id = r.id
          |  WHERE u.name = $name
          |  GROUP BY u.id""".stripMargin.query[APIV2User]

}
