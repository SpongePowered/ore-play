package models.api

import db.impl.schema.ProjectApiKeyTable
import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import ore.project.ProjectOwned
import ore.rest.ProjectApiKeyType

case class ProjectApiKey(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: ObjectReference,
    keyType: ProjectApiKeyType,
    value: String
) extends Model {

  override type T = ProjectApiKeyTable
  override type M = ProjectApiKey

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectApiKey = this.copy(id = id, createdAt = theTime)
}
object ProjectApiKey {
  implicit val isProjectOwned: ProjectOwned[ProjectApiKey] = (a: ProjectApiKey) => a.projectId
}