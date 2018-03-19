package ore.permission

import db.impl.access.OrganizationBase
import models.project.Project
import models.user.{Organization, User}
import ore.permission.role.RoleTypes
import ore.permission.scope.ScopeSubject

/**
  * Permission wrapper used for chaining permission checks.
  *
  * @param user User to check
  */
case class PermissionPredicate(user: User, not: Boolean = false) {

  def apply(p: Permission): AndThen = AndThen(user, p, not)

  protected case class AndThen(user: User, p: Permission, not: Boolean) {
    def in(subject: ScopeSubject): Boolean = {
      // Special Ore Developer Case
      if (user.globalRoles.contains(RoleTypes.OreDev)) {
        if (p == ViewHealth
          || p == ViewLogs
          || p == ViewActivity
          || p == ViewStats
        ) {
          return !not
        }
      }

      // Test org perms on projects
      subject match {
        case project: Project =>
          val id = project.ownerId
          val maybeOrg: Option[Organization] = project.service.getModelBase(classOf[OrganizationBase]).get(id)
          if (maybeOrg.isDefined) {
            // Project's owner is an organization
            val org = maybeOrg.get
            // Test the org scope and the project scope
            val orgTest = org.scope.test(user, p)
            val projectTest = project.scope.test(user, p)
            return orgTest | projectTest
          }
        case _ =>
      }
      val result = subject.scope.test(user, p)
      if (not) !result else result
    }
  }

}
