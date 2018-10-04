package ore.user

import scala.language.implicitConversions

import scala.concurrent.{ExecutionContext, Future}

import db.{ModelService, ObjectReference}
import models.user.User
import models.user.role.RoleModel

/**
  * Represents a [[User]] member of some entity.
  */
abstract class Member[RoleType <: RoleModel](override val userId: ObjectReference) extends UserOwned {

  /**
    * Returns the [[RoleModel]]s the user has in this entity.
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
