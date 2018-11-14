package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.ProjectRoleTable
import db.{ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import ore.Visitable
import ore.permission.role.Role
import ore.permission.scope.ProjectScope
import ore.project.ProjectOwned
import ore.user.UserOwned

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
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: ObjectReference,
    projectId: ObjectReference,
    role: Role,
    isAccepted: Boolean = false
) extends UserRoleModel {

  override type M = ProjectUserRole
  override type T = ProjectRoleTable

  def this(
      userId: ObjectReference,
      roleType: Role,
      projectId: ObjectReference,
      accepted: Boolean,
      visible: Boolean
  ) = this(
    id = ObjectId.Uninitialized,
    createdAt = ObjectTimestamp.Uninitialized,
    userId = userId,
    role = roleType,
    projectId = projectId,
    isAccepted = accepted
  )

  override def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable] =
    ProjectOwned[ProjectUserRole].project(this)
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectUserRole =
    this.copy(id = id, createdAt = theTime)
}
object ProjectUserRole {
  implicit val isProjectOwned: ProjectOwned[ProjectUserRole] = (a: ProjectUserRole) => a.projectId
  implicit val isUserOwned: UserOwned[ProjectUserRole]       = (a: ProjectUserRole) => a.userId
}
