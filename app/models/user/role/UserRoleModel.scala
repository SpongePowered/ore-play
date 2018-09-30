package models.user.role

import scala.concurrent.{ExecutionContext, Future}

import db.impl.schema.RoleTable
import db.{Model, ModelService}
import ore.Visitable
import ore.permission.role.{UserRole, Role}

/**
  * Represents a [[UserRole]] in something like a [[models.project.Project]] or
  * [[models.user.Organization]].
  */
abstract class UserRoleModel extends Model with UserRole { self =>

  override type M <: UserRoleModel { type M = self.M }
  override type T <: RoleTable[M]

  /**
    * Type of Role
    */
  def role: Role

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
