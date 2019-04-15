package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.RoleTable
import db.table.ModelTable
import models.user.Organization
import models.user.role.OrganizationUserRole

class OrganizationRoleTable(tag: Tag)
    extends ModelTable[OrganizationUserRole](tag, "user_organization_roles")
    with RoleTable[OrganizationUserRole] {

  def organizationId = column[DbRef[Organization]]("organization_id")

  override def * =
    (id.?, createdAt.?, (userId, organizationId, roleType, isAccepted)) <> (mkApply(
      (OrganizationUserRole.apply _).tupled
    ), mkUnapply(OrganizationUserRole.unapply))
}
