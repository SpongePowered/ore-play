package models.api

import java.sql.Timestamp

import db.impl.ProjectApiKeyTable
import db.impl.model.OreModel
import ore.project.ProjectOwned
import ore.rest.ProjectApiKeyTypes.ProjectApiKeyType

case class ProjectApiKey(override val id: Option[Int] = None,
                         override val createdAt: Option[Timestamp] = None,
                         override val projectId: Int,
                         keyType: ProjectApiKeyType,
                         value: String)
                         extends OreModel(id, createdAt) with ProjectOwned {

  override type T = ProjectApiKeyTable
  override type M = ProjectApiKey

  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(id = id, createdAt = theTime)

}
