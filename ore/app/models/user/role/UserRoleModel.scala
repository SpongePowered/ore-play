package models.user.role

import ore.Visitable
import ore.db.{Model, ModelService}
import ore.permission.role.Role

import cats.effect.IO

/**
  * Represents a user's [[Role]] in something like a [[models.project.Project]] or
  * [[models.user.Organization]].
  */
abstract class UserRoleModel[Self] {

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
  def subject(implicit service: ModelService): IO[Model[Visitable]]

  def withRole(role: Role): Self

  def withAccepted(accepted: Boolean): Self
}
