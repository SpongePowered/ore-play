package ore.user

import models.user.User
import models.user.role.RoleModel
import ore.permission.scope.ScopeSubject
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

import db.ModelService

/**
  * Represents a [[User]] member of some entity.
  */
abstract class Member[RoleType <: RoleModel](override val userId: Int)
                                             extends ScopeSubject with UserOwned {

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
