package models.api

import db.impl.schema.ProjectApiKeyTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjTimestamp}
import models.project.Project
import ore.project.ProjectOwned
import ore.rest.ProjectApiKeyType

import slick.lifted.TableQuery

case class ProjectApiKey private (
    id: ObjId[ProjectApiKey],
    createdAt: ObjTimestamp,
    projectId: DbRef[Project],
    keyType: ProjectApiKeyType,
    value: String
) extends Model {

  override type T = ProjectApiKeyTable
  override type M = ProjectApiKey
}
object ProjectApiKey {
  def partial(
      projectId: DbRef[Project],
      keyType: ProjectApiKeyType,
      value: String
  ): InsertFunc[ProjectApiKey] = (id, time) => ProjectApiKey(id, time, projectId, keyType, value)

  implicit val query: ModelQuery[ProjectApiKey] =
    ModelQuery.from[ProjectApiKey](TableQuery[ProjectApiKeyTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[ProjectApiKey] = (a: ProjectApiKey) => a.projectId
}
