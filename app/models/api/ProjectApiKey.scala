package models.api

import db.impl.schema.ProjectApiKeyTable
import db.{DbRef, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.Project
import ore.project.ProjectOwned
import ore.rest.ProjectApiKeyType

import slick.lifted.TableQuery

case class ProjectApiKey(
    id: ObjId[ProjectApiKey] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: DbRef[Project],
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
