package ore.db.impl.schema

import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.{Organization, User}
import ore.db.DbRef

class OrganizationMembersTable(tag: Tag) extends AssociativeTable[User, Organization](tag, "organization_members") {

  def userId         = column[DbRef[User]]("user_id")
  def organizationId = column[DbRef[Organization]]("organization_id")

  override def * = (userId, organizationId)
}
