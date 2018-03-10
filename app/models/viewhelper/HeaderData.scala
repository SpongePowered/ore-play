package models.viewhelper

import models.user.User
import ore.permission.Permission

// TODO perms in GlobalScope ;  ReviewFlags - ReviewVisibility - ReviewProjects - ViewStats - ViewHealth

case class HeaderData(currentUser: Option[User],
                      permissions: Map[Permission, Boolean],

                      hasUnreadNotifications: Boolean,   // user.hasUnreadNotif
                      unresolvedFlags: Boolean,         // flags.filterNot(_.isResolved).nonEmpty
                      hasProjectApprovals: Boolean, // >= 1 val futureApproval = projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsApproval).fn, ProjectSortingStrategies.Default, -1, 0)
                      hasReviewQueue: Boolean // queue.nonEmpty
                     )

