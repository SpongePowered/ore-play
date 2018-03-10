package ore.user

import db.impl.access.UserBase
import models.user.User
import models.user.role.RoleModel
import ore.permission.scope.ScopeSubject

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
  * Represents a [[User]] member of some entity.
  */
abstract class Member[RoleType <: RoleModel](override val userId: Int)(implicit users: UserBase)
                                             extends ScopeSubject with UserOwned {

  /**
    * Returns the [[RoleModel]]s the user has in this entity.
    *
    * @return Roles user has
    */
  def roles(implicit ec: ExecutionContext): Future[Set[RoleType]]

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  def headRole(implicit ec: ExecutionContext): Future[RoleType]

  override def user(implicit users: UserBase = null, ec: ExecutionContext): Future[User] = super.user(this.users, ec)

}
