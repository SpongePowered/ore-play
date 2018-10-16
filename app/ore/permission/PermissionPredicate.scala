package ore.permission

import scala.concurrent.{ExecutionContext, Future}

import db.ModelService
import models.user.User
import ore.permission.role.{RoleType, Trust}
import ore.permission.scope.HasScope

/**
  * Permission wrapper used for chaining permission checks.
  *
  * @param user User to check
  */
case class PermissionPredicate(user: User) {

  def apply(p: Permission): AndThen = AndThen(user, p)

  def asMap(trust: Trust)(perms: Permission*): Map[Permission, Boolean] =
    perms.map(p => p -> apply(p).withTrust(trust)).toMap

  def asMap(trust: Option[Trust])(perms: Permission*): Map[Permission, Boolean] =
    perms.map(p => p -> apply(p).withTrust(trust)).toMap

  protected case class AndThen(user: User, p: Permission) {
    def withTrust(trust: Trust): Boolean =
      // Special Ore Developer Case
      if (user.globalRoles.contains(RoleType.OreDev) && (p == ViewHealth || p == ViewLogs || p == ViewActivity || p == ViewStats))
        true
      else p.trust <= trust

    def withTrust(trust: Option[Trust]): Boolean = trust match {
      case None        => false
      case Some(value) => withTrust(value)
    }

    def in[A: HasScope](subject: A)(implicit service: ModelService, ec: ExecutionContext): Future[Boolean] =
      user.trustIn(subject).map(withTrust)

    def in[A: HasScope](subject: Option[A])(implicit service: ModelService, ec: ExecutionContext): Future[Boolean] =
      subject match {
        case None    => Future.successful(false)
        case Some(s) => this.in(s)
      }
  }
}
