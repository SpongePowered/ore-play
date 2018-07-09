package models.user.role

import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.OrganizationRoleTable
import ore.Visitable
import ore.permission.role.RoleType
import ore.permission.scope.OrganizationScope

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a [[RoleModel]] within an [[models.user.Organization]].
  *
  * @param id             Model ID
  * @param createdAt      Timestamp instant of creation
  * @param userId         ID of User this role belongs to
  * @param organizationId ID of Organization this role belongs to
  * @param _roleType      Type of Role
  * @param _isAccepted    True if has been accepted
  */
case class OrganizationRole(override val id: ObjectId = ObjectId.Uninitialized,
                            override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                            override val userId: ObjectReference,
                            override val organizationId: ObjectReference = -1,
                            private val _roleType: RoleType,
                            private val _isAccepted: Boolean = false)
                            extends RoleModel(id, createdAt, userId, _roleType, _isAccepted)
                              with OrganizationScope {

  override type M = OrganizationRole
  override type T = OrganizationRoleTable

  def this(userId: ObjectReference, roleType: RoleType) = this(userId = userId, _roleType = roleType)

  override def subject(implicit ec: ExecutionContext): Future[Visitable] = this.organization
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(id = id, createdAt = theTime)

}
