package ore.user.notification

import models.user.User
import models.user.role.RoleModel
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

import db.ModelService

/**
  * A collection of ways to filter invites.
  */
object InviteFilters extends Enumeration {

  val All = InviteFilter(0, "all", "notification.invite.all", implicit ec => implicit service => user => {
    user.projectRoles.filterNot(_.isAccepted).flatMap(q1 => user.organizationRoles.filterNot(_.isAccepted).map(q1 ++ _))
  })

  val Projects = InviteFilter(1, "projects", "notification.invite.projects", implicit ec => implicit service => user => {
    user.projectRoles.filterNot(_.isAccepted)
  })

  val Organizations = InviteFilter(2, "organizations", "notification.invite.organizations", implicit ec => implicit service => user => {
    user.organizationRoles.filterNot(_.isAccepted)
  })

  case class InviteFilter(i: Int,
                          name: String,
                          title: String,
                          filter: ExecutionContext => ModelService => User => Future[Seq[RoleModel]]) extends super.Val(i, name) {

    def apply(user: User)(implicit ec: ExecutionContext, service: ModelService): Future[Seq[RoleModel]] = this.filter(ec)(service)(user)

  }

  implicit def convert(value: Value): InviteFilter = value.asInstanceOf[InviteFilter]

}
