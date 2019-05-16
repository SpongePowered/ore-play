package db.impl.query

import models.querymodels._
import ore.db.DbRef
import ore.models.competition.Competition

import doobie._
import doobie.implicits._

object CompetitionQueries extends WebDoobieOreProtocol {

  def getEntries(competitionId: DbRef[Competition]): Query0[ProjectListEntry] = {
    sql"""|SELECT p.owner_name,
          |       p.slug,
          |       p.visibility,
          |       p.views,
          |       p.downloads,
          |       p.stars,
          |       p.category,
          |       p.description,
          |       p.name,
          |       pv.version_string,
          |       array_remove(array_agg(pvt.name), NULL)  AS tag_names,
          |       array_remove(array_agg(pvt.data), NULL)  AS tag_datas,
          |       array_remove(array_agg(pvt.color), NULL) AS tag_colors
          |    FROM project_competition_entries pce
          |             JOIN projects p ON pce.project_id = p.id
          |             LEFT JOIN project_versions pv ON p.recommended_version_id = pv.id
          |             LEFT JOIN project_version_tags pvt ON pv.id = pvt.version_id
          |    WHERE pce.competition_id = $competitionId
          |    GROUP BY (p.owner_name,
          |              p.slug,
          |              p.visibility,
          |              p.views,
          |              p.downloads,
          |              p.stars,
          |              p.category,
          |              p.description,
          |              p.name,
          |              pv.version_string) ORDER BY p.name""".stripMargin.query[ProjectListEntry]
  }
}
