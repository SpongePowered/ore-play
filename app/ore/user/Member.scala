package ore.user

import scala.language.implicitConversions

import scala.concurrent.{ExecutionContext, Future}

import db.ModelService
import models.user.User
import models.user.role.UserRoleModel

/**
  * Represents a [[User]] member of some entity.
  */
trait Member[RoleType <: UserRoleModel] extends UserOwned {

  /**
    * Returns the [[UserRoleModel]]s the user has in this entity.
    *
    * @return Roles user has
    */
  def roles(implicit ec: ExecutionContext, service: ModelService): Future[Set[RoleType]]

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  def headRole(implicit ec: ExecutionContext, service: ModelService): Future[RoleType]

}
