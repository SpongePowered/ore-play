package models.user.role

import db.impl.RoleTable
import ore.Visitable
import ore.permission.role.Role
import ore.permission.role.RoleType

import scala.concurrent.{ExecutionContext, Future}

import db.{Model, ModelService}

/**
  * Represents a [[Role]] in something like a [[models.project.Project]] or
  * [[models.user.Organization]].
  */
abstract class RoleModel extends Model with Role { self =>

  override type M <: RoleModel { type M = self.M }
  override type T <: RoleTable[M]

  /**
    * Type of Role
    */
  def roleType: RoleType

  /**
    * True if has been accepted
    */
  def isAccepted: Boolean

  /**
    * Returns the subject of this Role.
    *
    * @return Subject of Role
    */
  def subject(implicit ec: ExecutionContext, service: ModelService): Future[Visitable]
}
