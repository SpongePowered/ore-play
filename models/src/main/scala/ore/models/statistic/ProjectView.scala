package ore.models.statistic

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.ProjectViewsTable
import ore.db.{DbRef, ModelQuery}
import ore.models.project.Project
import ore.models.user.User

import com.github.tminglei.slickpg.InetString
import slick.lifted.TableQuery

/**
  * Represents a unique view on a Project.
  *
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param userId     User ID
  */
case class ProjectView(
    modelId: DbRef[Project],
    address: InetString,
    cookie: String,
    userId: Option[DbRef[User]]
) extends StatEntry[Project]

object ProjectView extends DefaultModelCompanion[ProjectView, ProjectViewsTable](TableQuery[ProjectViewsTable]) {
  implicit val query: ModelQuery[ProjectView] = ModelQuery.from(this)
}
