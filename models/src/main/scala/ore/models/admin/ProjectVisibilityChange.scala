package ore.models.admin

import java.time.Instant

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.common.VisibilityChange
import ore.db.impl.schema.ProjectVisibilityChangeTable
import ore.models.project.{Project, Visibility}
import ore.models.user.User
import ore.db.{DbRef, ModelQuery}

import slick.lifted.TableQuery

case class ProjectVisibilityChange(
    createdBy: Option[DbRef[User]],
    projectId: DbRef[Project],
    comment: String,
    resolvedAt: Option[Instant],
    resolvedBy: Option[DbRef[User]],
    visibility: Visibility
) extends VisibilityChange
object ProjectVisibilityChange
    extends DefaultModelCompanion[ProjectVisibilityChange, ProjectVisibilityChangeTable](
      TableQuery[ProjectVisibilityChangeTable]
    ) {

  implicit val query: ModelQuery[ProjectVisibilityChange] =
    ModelQuery.from(this)
}
