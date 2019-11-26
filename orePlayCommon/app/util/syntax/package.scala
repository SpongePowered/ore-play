package util

import discourse.HasForumRepresentation
import ore.db.access.QueryView
import ore.db.impl.common.Hideable
import ore.markdown.IsMarkdownRenderable
import ore.member.Joinable
import ore.models.organization.OrganizationOwned
import ore.models.project.ProjectOwned
import ore.models.user.UserOwned
import ore.permission.scope.HasScope

package object syntax
    extends HasScope.ToHasScopeOps
    with OrganizationOwned.ToOrganizationOwnedOps
    with ProjectOwned.ToProjectOwnedOps
    with UserOwned.ToUserOwnedOps
    with QueryView.ToQueryFilterableOps
    with Hideable.ToHideableOps
    with Joinable.ToJoinableOps
    with IsMarkdownRenderable.ToIsMarkdownRenderableOps
    with HasForumRepresentation.ToHasForumRepresentationOps
    with ModelSyntax
    with ZIOSyntax
