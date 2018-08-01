package ore.permission.scope

import models.user.User
import ore.permission.Permission

import scala.concurrent.{ExecutionContext, Future}
import db.ModelService

/**
  * Represents a "scope" for testing permissions within the application.
  */
trait Scope extends ScopeSubject {

  /**
    * Returns the parent scope for this scope if any.
    *
    * @return Parent scope if any, None otherwise
    */
  def parent: Option[Scope] = None

  /**
    * Test the given permission for the given user in this scope only.
    *
    * @param user User to check on
    * @param p    Permission to test
    * @return     True if user has permission in this scope
    */
  def check(user: User, p: Permission)(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] = user.trustIn(this).map(userTrust => p.trust <= userTrust)

  /**
    * Tests the given permission for the given user.
    *
    * @param user User to check on
    * @param p    Permission to test
    * @return     True if user has permission
    */
  def test(user: User, p: Permission)(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] = {

    this.check(user, p).flatMap {
      case true => Future.successful(true)
      case false =>
        this.parent match {
          case Some(parent) => parent.test(user, p)
          case None => Future.successful(false)
        }
    }

  }

  override val scope: Scope = this

}
