package models.api

import db.{ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.ProjectApiKeyTable
import db.impl.model.OreModel
import ore.project.ProjectOwned
import ore.rest.ProjectApiKeyTypes.ProjectApiKeyType

case class ProjectApiKey(override val id: ObjectId = ObjectId.Uninitialized,
                         override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                         override val projectId: ObjectReference,
                         keyType: ProjectApiKeyType,
                         value: String)
                         extends OreModel(id, createdAt) with ProjectOwned {

  override type T = ProjectApiKeyTable
  override type M = ProjectApiKey

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectApiKey = this.copy(id = id, createdAt = theTime)
}
