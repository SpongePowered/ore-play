package ore.user.notification

import models.user.User
import models.user.role.RoleModel

import scala.concurrent.Future
import scala.language.implicitConversions

/**
  * A collection of ways to filter invites.
  */
object InviteFilters extends Enumeration {

  val All = InviteFilter(0, "all", "notification.invite.all", user => {
    user.projectRoles.filterNot(_.isAccepted).flatMap(q1 => user.organizationRoles.filterNot(_.isAccepted).map(q1 ++ _))
  })

  val Projects = InviteFilter(1, "projects", "notification.invite.projects", user => {
    user.projectRoles.filterNot(_.isAccepted)
  })

  val Organizations = InviteFilter(2, "organizations", "notification.invite.organizations", user => {
    user.organizationRoles.filterNot(_.isAccepted)
  })

  case class InviteFilter(i: Int,
                          name: String,
                          title: String,
                          filter: User => Future[Seq[RoleModel]]) extends super.Val(i, name) {

    def apply(user: User): Future[Seq[RoleModel]] = this.filter(user)

  }

  implicit def convert(value: Value): InviteFilter = value.asInstanceOf[InviteFilter]

}
