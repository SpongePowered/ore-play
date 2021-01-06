package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.RoleTable
import ore.models.organization.Organization
import ore.models.user.role.OrganizationUserRole

class OrganizationRoleTable(tag: Tag)
    extends ModelTable[OrganizationUserRole](tag, "user_organization_roles")
    with RoleTable[OrganizationUserRole] {

  def organizationId = column[DbRef[Organization]]("organization_id")

  override def * =
    (id.?, createdAt.?, (userId, organizationId, roleType, isAccepted)).<>(
      mkApply(
        (OrganizationUserRole.apply _).tupled
      ),
      mkUnapply(OrganizationUserRole.unapply)
    )
}
