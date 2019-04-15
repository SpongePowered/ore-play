package util

import ore.db.access.QueryView
import ore.organization.OrganizationOwned
import ore.permission.scope.HasScope
import ore.project.ProjectOwned
import ore.user.UserOwned

package object syntax
    extends HasScope.ToHasScopeOps
    with OrganizationOwned.ToOrganizationOwnedOps
    with ProjectOwned.ToProjectOwnedOps
    with UserOwned.ToUserOwnedOps
    with QueryView.ToQueryFilterableOps
