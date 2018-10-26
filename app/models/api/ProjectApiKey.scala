package models.api

import db.impl.schema.ProjectApiKeyTable
import db.{Model, ModelQuery, ObjectId, ObjectReference, ObjectTimestamp}
import ore.project.ProjectOwned
import ore.rest.ProjectApiKeyType

import slick.lifted.TableQuery

case class ProjectApiKey(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: ObjectReference,
    keyType: ProjectApiKeyType,
    value: String
) extends Model
    with ProjectOwned {

  override type T = ProjectApiKeyTable
  override type M = ProjectApiKey
}
object ProjectApiKey {
  implicit val query: ModelQuery[ProjectApiKey] =
    ModelQuery.from[ProjectApiKey](TableQuery[ProjectApiKeyTable], _.copy(_, _))
}
