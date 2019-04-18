package util

import ore.db.access.QueryView
import ore.models.organization.OrganizationOwned
import ore.permission.scope.HasScope
import ore.models.project.ProjectOwned
import ore.models.user.UserOwned

package object syntax
    extends HasScope.ToHasScopeOps
    with OrganizationOwned.ToOrganizationOwnedOps
    with ProjectOwned.ToProjectOwnedOps
    with UserOwned.ToUserOwnedOps
    with QueryView.ToQueryFilterableOps
