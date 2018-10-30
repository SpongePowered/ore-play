package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.RoleTable
import db.table.ModelTable
import models.user.Organization
import models.user.role.OrganizationRole

class OrganizationRoleTable(tag: RowTag)
    extends ModelTable[OrganizationRole](tag, "user_organization_roles")
    with RoleTable[OrganizationRole] {

  def organizationId = column[DbRef[Organization]]("organization_id")

  override def * =
    mkProj((id.?, createdAt.?, userId, organizationId, roleType, isAccepted))(mkTuple[OrganizationRole]())
}
