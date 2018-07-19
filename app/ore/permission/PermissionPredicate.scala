package ore.permission

import db.impl.access.OrganizationBase
import models.project.Project
import models.user.User
import ore.permission.role.RoleTypes
import ore.permission.scope.ScopeSubject

import scala.concurrent.{ExecutionContext, Future}

import util.FutureUtils
import util.instances.future._

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

      val projectTested = subject match {
        case project: Project =>
          checkProjectPerm(project)
        case _ =>
          Future.successful(false)
      }

      projectTested.flatMap {
        case true => Future.successful(!not)
        case false =>
          for {
            result <- subject.scope.test(user, p)
          } yield {
            if (not) !result else result
          }
      }
    }

    private def checkProjectPerm(project: Project): Future[Boolean] = {
      val orgTest = project.service
        .getModelBase(classOf[OrganizationBase])
        .get(project.ownerId)
        .fold(Future.successful(false))(_.scope.test(user, p))
        .flatten

      val projectTest = project.scope.test(user, p)

      FutureUtils.raceBoolean(orgTest, projectTest)
    }

    def in(subject: Option[ScopeSubject]): Future[Boolean] = {
      subject match {
        case None => Future.successful(false)
        case Some(s) => this.in(s)
      }
    }
  }

}
