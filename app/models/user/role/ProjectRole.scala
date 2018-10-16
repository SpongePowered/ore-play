package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.ProjectRoleTable
import db.{ModelQuery, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import ore.Visitable
import ore.permission.role.RoleType
import ore.permission.scope.ProjectScope
import ore.project.ProjectOwned

import slick.lifted.TableQuery

/**
  * Represents a [[ore.project.ProjectMember]]'s role in a
  * [[models.project.Project]]. A ProjectRole determines what a Member can and
  * cannot do within a [[ProjectScope]].
  *
  * @param id         Model ID
  * @param createdAt  Timestamp instant of creation
  * @param userId     ID of User this role belongs to
  * @param roleType   Type of role
  * @param projectId  ID of project this role belongs to
  */
case class ProjectRole(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    userId: ObjectReference,
    projectId: ObjectReference,
    roleType: RoleType,
    isAccepted: Boolean = false
) extends RoleModel
    with ProjectOwned {

  override type M = ProjectRole
  override type T = ProjectRoleTable

  def this(
      userId: ObjectReference,
      roleType: RoleType,
      projectId: ObjectReference,
      accepted: Boolean,
      visible: Boolean
  ) = this(
    id = ObjectId.Uninitialized,
    createdAt = ObjectTimestamp.Uninitialized,
    userId = userId,
    roleType = roleType,
    projectId = projectId,
    isAccepted = accepted
  )

  override def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable] = this.project
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectRole                    = this.copy(id = id, createdAt = theTime)
}
object ProjectRole {
  implicit val query: ModelQuery[ProjectRole] =
    ModelQuery.from[ProjectRole](TableQuery[ProjectRoleTable])
}
