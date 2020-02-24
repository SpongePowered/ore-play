package ore.rest

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.Project

import io.circe.Json

//TODO: Create a view for the old API to refer to promoted versions using
/*
to_jsonb(
               ARRAY(SELECT DISTINCT
                   ON (promoted.version_string) jsonb_build_object(
                                                        'version_string', promoted.version_string,
                                                        'platforms', promoted.platforms,
                                                        'platform_versions', promoted.platform_versions,
                                                        'platform_coarse_versions', promoted.platform_coarse_versions,
                                                        'stability', promoted.stability,
                                                        'release_type', promoted.release_type)
                         FROM promoted_versions promoted
                         WHERE promoted.project_id = p.id
                         LIMIT 5)) AS promoted_versions
 */
class ApiV1HomeProjectsTable(tag: Tag) extends Table[HomeProjectsV1](tag, "home_projects") {

  def id               = column[DbRef[Project]]("id")
  def promotedVersions = column[Json]("promoted_versions")

  override def * = (id, promotedVersions) <> ((HomeProjectsV1.apply _).tupled, HomeProjectsV1.unapply)
}

case class HomeProjectsV1(id: DbRef[Project], promotedVersions: Json)
