package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.api.ProjectApiKey
import models.project.Project
import ore.rest.ProjectApiKeyType

class ProjectApiKeyTable(tag: Tag) extends ModelTable[ProjectApiKey](tag, "project_api_keys") {

  def projectId = column[DbRef[Project]]("project_id")
  def keyType   = column[ProjectApiKeyType]("key_type")
  def value     = column[String]("value")

  override def * =
    (id.?, createdAt.?, (projectId, keyType, value)) <> (mkApply((ProjectApiKey.apply _).tupled), mkUnapply(
      ProjectApiKey.unapply
    ))
}
