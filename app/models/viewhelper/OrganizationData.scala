package models.viewhelper

import models.user.{Organization, User}
import models.user.role.OrganizationRole
import ore.organization.OrganizationMember
import ore.permission.Permission


// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

// TODO perms in orga ; EditSettings


case class OrganizationData(headerData: HeaderData,
                            joinable: Organization,
                            ownerRole: OrganizationRole,
                            members: Seq[(OrganizationMember, OrganizationRole, User)], // TODO sorted/reverse
                            permissions: Map[Permission, Boolean])
  extends JoinableData[OrganizationRole, OrganizationMember, Organization] {

  def o: Organization = joinable

  def global = headerData

  def hasUser = global.hasUser
  def currentUser = global.currentUser


}
