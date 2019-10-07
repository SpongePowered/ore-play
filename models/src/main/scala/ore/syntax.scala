package ore

import ore.db.access.QueryView
import ore.db.impl.common.Hideable
import ore.member.Joinable
import ore.models.organization.OrganizationOwned
import ore.models.project.ProjectOwned
import ore.models.user.UserOwned
import ore.permission.scope.HasScope

object syntax
    extends HasScope.ToHasScopeOps
    with OrganizationOwned.ToOrganizationOwnedOps
    with ProjectOwned.ToProjectOwnedOps
    with UserOwned.ToUserOwnedOps
    with QueryView.ToQueryFilterableOps
    with Hideable.ToHideableOps
    with Joinable.ToJoinableOps
