package ore.db.impl.schema

import ore.db.impl.OrePostgresDriver.api._
import ore.models.api.ProjectApiKey
import ore.models.project.Project
import ore.db.DbRef

class ProjectApiKeyTable(tag: Tag) extends ModelTable[ProjectApiKey](tag, "project_api_keys") {

  def projectId = column[DbRef[Project]]("project_id")
  def value     = column[String]("value")

  override def * =
    (id.?, createdAt.?, (projectId, value)) <> (mkApply((ProjectApiKey.apply _).tupled), mkUnapply(
      ProjectApiKey.unapply
    ))
}
