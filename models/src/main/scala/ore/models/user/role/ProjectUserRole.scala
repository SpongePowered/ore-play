package ore.models.user.role

import scala.language.higherKinds

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.common.Visitable
import ore.db.impl.schema.ProjectRoleTable
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.models.project.{Project, ProjectOwned}
import ore.models.user.{User, UserOwned}
import ore.permission.role.Role
import ore.permission.scope.ProjectScope

import cats.MonadError
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Represents a [[ore.models.user.User]]'s role in a
  * [[ore.models.project.Project]]. A ProjectRole determines what a Member can and
  * cannot do within a [[ProjectScope]].
  *
  * @param userId     ID of User this role belongs to
  * @param role   Type of role
  * @param projectId  ID of project this role belongs to
  */
case class ProjectUserRole(
    userId: DbRef[User],
    projectId: DbRef[Project],
    role: Role,
    isAccepted: Boolean = false
) extends UserRoleModel[ProjectUserRole] {

  override def subject[F[_]: ModelService](implicit F: MonadError[F, Throwable]): F[Model[Visitable]] =
    ProjectOwned[ProjectUserRole].project(this).widen[Model[Visitable]]

  override def withRole(role: Role): ProjectUserRole = copy(role = role)

  override def withAccepted(accepted: Boolean): ProjectUserRole = copy(isAccepted = accepted)
}
object ProjectUserRole extends DefaultModelCompanion[ProjectUserRole, ProjectRoleTable](TableQuery[ProjectRoleTable]) {

  implicit val query: ModelQuery[ProjectUserRole] = ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[ProjectUserRole] = (a: ProjectUserRole) => a.projectId
  implicit val isUserOwned: UserOwned[ProjectUserRole]       = (a: ProjectUserRole) => a.userId
}
