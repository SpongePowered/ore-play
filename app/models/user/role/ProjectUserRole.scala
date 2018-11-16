package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.ProjectRoleTable
import db.{DbRef, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.project.Project
import models.user.User
import ore.Visitable
import ore.permission.role.Role
import ore.permission.scope.ProjectScope
import ore.project.ProjectOwned
import ore.user.UserOwned

import slick.lifted.TableQuery

/**
  * Represents a [[ore.project.ProjectMember]]'s role in a
  * [[models.project.Project]]. A ProjectRole determines what a Member can and
  * cannot do within a [[ProjectScope]].
  *
  * @param id         Model ID
  * @param createdAt  Timestamp instant of creation
  * @param userId     ID of User this role belongs to
  * @param role   Type of role
  * @param projectId  ID of project this role belongs to
  */
case class ProjectUserRole(
    id: ObjId[ProjectUserRole] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: DbRef[User],
    projectId: DbRef[Project],
    role: Role,
    isAccepted: Boolean = false
) extends UserRoleModel {

  override type M = ProjectUserRole
  override type T = ProjectRoleTable

  def this(
      userId: DbRef[User],
      roleType: Role,
      projectId: DbRef[Project],
      accepted: Boolean,
      visible: Boolean
  ) = this(
    id = ObjId.Uninitialized(),
    createdAt = ObjectTimestamp.Uninitialized,
    userId = userId,
    role = roleType,
    projectId = projectId,
    isAccepted = accepted
  )

  override def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable] =
    ProjectOwned[ProjectUserRole].project(this)
}
object ProjectUserRole {
  implicit val query: ModelQuery[ProjectUserRole] =
    ModelQuery.from[ProjectUserRole](TableQuery[ProjectRoleTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[ProjectUserRole] = (a: ProjectUserRole) => a.projectId
  implicit val isUserOwned: UserOwned[ProjectUserRole]       = (a: ProjectUserRole) => a.userId
}
