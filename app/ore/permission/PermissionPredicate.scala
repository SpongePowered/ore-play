package ore.permission

import db.impl.access.OrganizationBase
import models.project.Project
import models.user.{Organization, User}
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
      // Test org perms on projects
      subject match {
        case project: Project =>
          val id = project.ownerId
          project.service.getModelBase(classOf[OrganizationBase]).get(id).map { maybeOrg =>
            if (maybeOrg.isDefined) {
              // Project's owner is an organization
              val org = maybeOrg.get
              // Test the org scope and the project scope
              // TODO remove confusing return in the middle here
              return for {
                orgTest <- org.scope.test(user, p)
                projectTest <- project.scope.test(user, p)
              } yield {
                orgTest | projectTest
              }
            }
          }
        case _ =>
      }
      for {
        result <- subject.scope.test(user, p)
      } yield {
        if (not) !result else result
      }
    }
  }

}
