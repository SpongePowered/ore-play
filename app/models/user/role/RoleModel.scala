package models.user.role

import java.sql.Timestamp

import com.google.common.base.Preconditions._
import db.impl.RoleTable
import db.impl.model.OreModel
import db.impl.table.ModelKeys
import db.impl.table.ModelKeys._
import models.user.User
import ore.Visitable
import ore.permission.role.Role
import ore.permission.role.RoleType

import scala.concurrent.{ExecutionContext, Future}

import db.{ObjectId, ObjectTimestamp}

/**
  * Represents a [[Role]] in something like a [[models.project.Project]] or
  * [[models.user.Organization]].
  *
  * @param id           Model ID
  * @param createdAt    Timestamp instant of creation
  * @param userId       ID of User this role belongs to
  * @param _roleType    Type of Role
  * @param _isAccepted  True if has been accepted
  */
abstract class RoleModel(override val id: ObjectId,
                         override val createdAt: ObjectTimestamp,
                         override val userId: Int,
                         private var _roleType: RoleType,
                         private var _isAccepted: Boolean = false)
                         extends OreModel(id, createdAt)
                           with Role { self =>

  override type M <: RoleModel { type M = self.M }
  override type T <: RoleTable[M]

  /**
    * Returns the subject of this Role.
    *
    * @return Subject of Role
    */
  def subject(implicit ec: ExecutionContext): Future[Visitable]

  /**
    * Sets whether this role has been accepted by the [[User]] it belongs to.
    *
    * @param accepted True if role accepted
    */
  def setAccepted(accepted: Boolean): Future[Int] = Defined {
    this._isAccepted = accepted
    update(IsAccepted)
  }

  /**
    * Returns true if this role has been accepted by the [[User]] it belongs to.
    *
    * @return True if accepted by user
    */
  def isAccepted: Boolean = this._isAccepted

  override def roleType: RoleType = this._roleType

  /**
    * Sets this role's [[RoleType]].
    *
    * @param _roleType Role type to set
    */
  def setRoleType(_roleType: RoleType) = {
    checkNotNull(_roleType, "null role type", "")
    this._roleType = _roleType
    if (isDefined) update(ModelKeys.RoleType)
  }

}
