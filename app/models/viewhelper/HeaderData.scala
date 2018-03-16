package models.viewhelper

import models.user.User
import ore.permission.Permission

// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

// TODO perms in GlobalScope ;  ReviewFlags - ReviewVisibility - ReviewProjects - ViewStats -
//                              ViewHealth - ViewLogs - HideProjects - HardRemoveProject - UserAdmin
//                              HideProjects


case class HeaderData(currentUser: Option[User] = None,
                      permissions: Map[Permission, Boolean] = Map.empty,

                      hasUnreadNotifications: Boolean = false,   // user.hasUnreadNotif
                      unresolvedFlags: Boolean = false,         // flags.filterNot(_.isResolved).nonEmpty
                      hasProjectApprovals: Boolean = false, // >= 1 val futureApproval = projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsApproval).fn, ProjectSortingStrategies.Default, -1, 0)
                      hasReviewQueue: Boolean = false // queue.nonEmpty
                     ) {

  // Just some helpers in templates:
  def isAuthenticated = currentUser.isDefined

  def hasUser = currentUser.isDefined

  def isCurrentUser(userId: Int) = currentUser.map(_.id).contains(userId)

  def apply(permission: Permission): Boolean = permissions(permission)
}

