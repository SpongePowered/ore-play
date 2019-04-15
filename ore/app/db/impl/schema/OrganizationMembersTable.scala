package db.impl.schema

import db.impl.OrePostgresDriver.api._
import models.user.{Organization, User}
import ore.db.DbRef

class OrganizationMembersTable(tag: Tag) extends AssociativeTable[User, Organization](tag, "organization_members") {

  def userId         = column[DbRef[User]]("user_id")
  def organizationId = column[DbRef[Organization]]("organization_id")

  override def * = (userId, organizationId)
}
