package models.user

import java.sql.Timestamp

import com.google.common.base.Preconditions._
import db.impl.access.UserBase
import db.impl.model.OreModel
import db.impl.{OrganizationMembersTable, OrganizationRoleTable, OrganizationTable}
import db.impl.table.ModelKeys._
import db.{Model, Named}
import models.user.role.OrganizationRole
import ore.organization.OrganizationMember
import ore.permission.role.RoleTypes
import ore.permission.scope.OrganizationScope
import ore.user.{MembershipDossier, UserOwned}
import ore.{Joinable, Visitable}

/**
  * Represents an Ore Organization. An organization is like a [[User]] in the
  * sense that it shares many qualities with Users and also has a companion
  * User on the forums. An organization is made up by a group of Users who each
  * have a corresponding rank within the organization.
  *
  * @param id             Unique ID
  * @param createdAt      Date of creation
  * @param ownerId        The ID of the [[User]] that owns this organization
  */
case class Organization(override val id: Option[Int] = None,
                        override val createdAt: Option[Timestamp] = None,
                        username: String,
                        private var ownerId: Int)
                        extends OreModel(id, createdAt)
                          with UserOwned
                          with OrganizationScope
                          with Named
                          with Visitable
                          with Joinable[OrganizationMember] {

  override type M = Organization
  override type T = OrganizationTable

  /**
    * Contains all information for [[User]] memberships.
    */
  override val memberships = new MembershipDossier {

    type ModelType = Organization
    type RoleType = OrganizationRole
    type RoleTable = OrganizationRoleTable
    type MembersTable = OrganizationMembersTable
    type MemberType = OrganizationMember

    val membersTableClass: Class[MembersTable] = classOf[OrganizationMembersTable]
    val roleClass: Class[RoleType] = classOf[OrganizationRole]
    val model: ModelType = Organization.this

    def newMember(userId: Int) = new OrganizationMember(this.model, userId)

  }

  /**
    * Returns the [[User]] that owns this Organization.
    *
    * @return User that owns organization
    */
  override def owner: OrganizationMember = new OrganizationMember(this, this.ownerId)

  override def transferOwner(member: OrganizationMember) {
    // Down-grade current owner to "Admin"
    this.memberships.getRoles(this.owner.user).filter(_.roleType == RoleTypes.OrganizationOwner)
      .foreach(_.roleType = RoleTypes.OrganizationAdmin);
    this.memberships.getRoles(member.user).foreach(_.roleType = RoleTypes.OrganizationOwner);
    this.owner = member.user;
  }


  /**
    * Sets the [[User]] that owns this Organization.
    *
    * @param user User that owns this organization
    */
  def owner_=(user: User) = {
    checkNotNull(user, "null user", "")
    checkArgument(user.isDefined, "undefined user", "")
    this.ownerId = user.id.get
    if (isDefined) {
      update(OrgOwnerId)
    }
  }

  /**
    * Returns this Organization as a [[User]].
    *
    * @return This Organization as a User
    */
  def toUser = this.service.getModelBase(classOf[UserBase]).withName(this.username).get

  override val name: String = this.username
  override def url: String = this.toUser.url
  override val userId: Int = this.ownerId
  override def organizationId: Int = this.id.get
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]): Model = this.copy(createdAt = theTime)

}
