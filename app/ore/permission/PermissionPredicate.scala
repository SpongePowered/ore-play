package ore.permission

import db.ModelService
import models.user.User
import models.user.role.DbRole
import ore.permission.role.{Role, Trust}
import ore.permission.scope.HasScope

import cats.effect.IO
import cats.syntax.all._

/**
  * Permission wrapper used for chaining permission checks.
  *
  * @param user User to check
  */
case class PermissionPredicate(user: User) {

  def apply(p: Permission): AndThen = AndThen(user, p)

  def asMap(trust: Trust, globalRoles: Set[DbRole])(perms: Permission*): Map[Permission, Boolean] =
    perms.map(p => p -> apply(p).withTrustAndGlobalRoles(trust, globalRoles)).toMap

  def asMap(trust: Option[Trust], globalRoles: Option[Set[DbRole]])(perms: Permission*): Map[Permission, Boolean] =
    perms.map(p => p -> apply(p).withTrustAndGlobalRoles(trust, globalRoles)).toMap

  protected case class AndThen(user: User, p: Permission) {
    def withTrustAndGlobalRoles(trust: Trust, globalRoles: Set[DbRole]): Boolean =
      // Special Ore Developer Case
      if (globalRoles
            .map(_.toRole)
            .contains(Role.OreDev) && (p == ViewHealth || p == ViewLogs || p == ViewActivity || p == ViewStats))
        true
      else p.trust <= trust

    def withTrustAndGlobalRoles(optTrust: Option[Trust], optGlobalRoles: Option[Set[DbRole]]): Boolean =
      (optTrust, optGlobalRoles) match {
        case (Some(trust), Some(globalRoles)) => withTrustAndGlobalRoles(trust, globalRoles)
        case _                                => false
      }

    def in[A: HasScope](subject: A)(implicit service: ModelService): IO[Boolean] =
      user.trustIn(subject).map2(user.globalRoles.allFromParent(user))((t, r) => withTrustAndGlobalRoles(t, r.toSet))

    def in[A: HasScope](subject: Option[A])(implicit service: ModelService): IO[Boolean] =
      subject match {
        case None    => IO.pure(false)
        case Some(s) => this.in(s)
      }
  }
}
