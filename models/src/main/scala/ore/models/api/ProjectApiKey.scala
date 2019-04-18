package ore.models.api

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.ProjectApiKeyTable
import ore.models.project.Project
import ore.db.{DbRef, ModelQuery}
import ore.models.project.ProjectOwned
import ore.rest.ProjectApiKeyType

import slick.lifted.TableQuery

case class ProjectApiKey(
    projectId: DbRef[Project],
    keyType: ProjectApiKeyType,
    value: String
)
object ProjectApiKey extends DefaultModelCompanion[ProjectApiKey, ProjectApiKeyTable](TableQuery[ProjectApiKeyTable]) {
  implicit val query: ModelQuery[ProjectApiKey] =
    ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[ProjectApiKey] = (a: ProjectApiKey) => a.projectId
}
