package ore.permission

import db.impl.access.OrganizationBase
import models.project.Project
import models.user.{Organization, User}
import ore.permission.role.RoleTypes
import ore.permission.scope.ScopeSubject

import scala.concurrent.{ExecutionContext, Future}

/**
  * Permission wrapper used for chaining permission checks.
  *
  * @param user User to check
  */
case class PermissionPredicate(user: User, not: Boolean = false) {

  def apply(p: Permission)(implicit ec: ExecutionContext): AndThen = AndThen(user, p, not)

  protected case class AndThen(user: User, p: Permission, not: Boolean)(implicit ec: ExecutionContext) {
    def in(subject: ScopeSubject): Future[Boolean] = {
      // Special Ore Developer Case
      if (user.globalRoles.contains(RoleTypes.OreDev)) {
        if (p == ViewHealth
          || p == ViewLogs
          || p == ViewActivity
          || p == ViewStats
        ) {
          return Future.successful(!not)
        }
      }
      // Test org perms on projects

      val projectTested = subject match {
        case project: Project =>
          checkProjectPerm(project)
        case _ =>
          Future.successful(false)
      }

      projectTested.flatMap {
        case true => Future.successful(true)
        case false =>
          for {
            result <- subject.scope.test(user, p)
          } yield {
            if (not) !result else result
          }
      }
    }

    private def checkProjectPerm(project: Project): Future[Boolean] = {
      val pp = project.service.getModelBase(classOf[OrganizationBase]).get(project.ownerId)
      pp.flatMap {
        case None => Future.successful(false)
        case Some(org) =>
          // Test the org scope and the project scope
          for {
            orgTest <- org.scope.test(user, p)
            projectTest <- project.scope.test(user, p)
          } yield {
            orgTest | projectTest
          }
      }
    }

    def in(subject: Option[ScopeSubject]): Future[Boolean] = {
      subject match {
        case None => Future.successful(false)
        case Some(s) => this.in(s)
      }
    }
  }

}
