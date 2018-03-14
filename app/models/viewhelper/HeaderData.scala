package models.viewhelper

import models.user.User
import ore.permission.Permission

// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

// TODO perms in GlobalScope ;  ReviewFlags - ReviewVisibility - ReviewProjects - ViewStats - ViewHealth - ViewLogs

case class HeaderData(currentUser: Option[User],
                      permissions: Map[Permission, Boolean],

                      hasUnreadNotifications: Boolean,   // user.hasUnreadNotif
                      unresolvedFlags: Boolean,         // flags.filterNot(_.isResolved).nonEmpty
                      hasProjectApprovals: Boolean, // >= 1 val futureApproval = projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsApproval).fn, ProjectSortingStrategies.Default, -1, 0)
                      hasReviewQueue: Boolean // queue.nonEmpty
                     ) {

  // Just some helpers in templates:

  def hasUser = currentUser.isDefined

  def apply(permission: Permission): Boolean = permissions(permission)
}

