package models.user.role

import java.sql.Timestamp

import db.impl.ProjectRoleTable
import ore.Visitable
import ore.permission.role.RoleType
import ore.permission.scope.ProjectScope

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a [[ore.project.ProjectMember]]'s role in a
  * [[models.project.Project]]. A ProjectRole determines what a Member can and
  * cannot do within a [[ProjectScope]].
  *
  * @param id         Model ID
  * @param createdAt  Timestamp instant of creation
  * @param userId     ID of User this role belongs to
  * @param _roleType  Type of role
  * @param projectId  ID of project this role belongs to
  */
case class ProjectRole(override val id: Option[Int] = None,
                       override val createdAt: Option[Timestamp] = None,
                       override val userId: Int,
                       override val projectId: Int,
                       private val _roleType: RoleType,
                       private val _isAccepted: Boolean = false)
                       extends RoleModel(id, createdAt, userId, _roleType, _isAccepted)
                         with ProjectScope {

  override type M = ProjectRole
  override type T = ProjectRoleTable

  def this(userId: Int, roleType: RoleType, projectId: Int, accepted: Boolean, visible: Boolean) = this(
    id = None,
    createdAt = None,
    userId = userId,
    _roleType = roleType,
    projectId = projectId,
    _isAccepted = accepted
  )

  override def subject(implicit ec: ExecutionContext): Future[Visitable] = this.project
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]): ProjectRole = this.copy(id = id, createdAt = theTime)

}
