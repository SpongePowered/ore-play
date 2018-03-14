package ore.user

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.impl.model.OreModel
import db.table.AssociativeTable
import models.user.User
import models.user.role.RoleModel
import ore.permission.role.Default

import scala.language.implicitConversions

/**
  * Handles and keeps track of [[User]] "memberships" for an [[OreModel]].
  */
trait MembershipDossier {

  type ModelType <: OreModel
  type RoleType <: RoleModel
  type MemberType <: Member[RoleType]
  type MembersTable <: AssociativeTable

  val model: ModelType
  val roleClass: Class[RoleType]
  val membersTableClass: Class[MembersTable]

  implicit def userBase: UserBase = this.model.userBase

  implicit def convertModel(model: ModelType): this.model.M = model.asInstanceOf[this.model.M]

  def roles: ModelAccess[RoleType] = this.model.schema.getChildren[RoleType](this.roleClass, this.model)

  private def association
  = this.model.schema.getAssociation[MembersTable, User](this.membersTableClass, this.model)
  private def roleAccess: ModelAccess[RoleType] = this.model.service.access[RoleType](roleClass)
  private def addMember(user: User) = this.association.add(user)

  private def clearRoles(user: User) = this.roleAccess.removeAll(_.userId === user.id.get)

  /**
    * Constructs a new member object of the MemberType.
    *
    * @param userId User ID of member
    * @return       New Member
    */
  def newMember(userId: Int): MemberType

  /**
    * Returns all members of the model. This includes members that have not
    * yet accepted membership.
    *
    * @return All members
    */
  def members: Set[MemberType] = {
    this.association.all.map { user =>
      newMember(user.id.get)
    }
  }

  /**
    * Adds a new role to the dossier and adds the user as a member if not already.
    *
    * @param role Role to add
    */
  def addRole(role: RoleType) = {
    val user = role.user
    if (!this.roles.exists(_.userId === user.id.get))
      addMember(user)
    this.roleAccess.add(role)
  }

  /**
    * Returns all roles for the specified [[User]].
    *
    * @param user User to get roles for
    * @return     User roles
    */
  def getRoles(user: User): Set[RoleType] = this.roles.filter(_.userId === user.id.get).toSet

  /**
    * Returns the highest level of [[ore.permission.role.Trust]] this user has.
    *
    * @param user User to get trust of
    * @return Trust of user
    */
  def getTrust(user: User) = {
    this.members
      .find(_.user.equals(user))
      .flatMap(_.roles
        .filter(_.isAccepted).toList.sorted.lastOption
        .map(_.roleType.trust))
      .getOrElse(Default)
  }

  /**
    * Removes a role from the dossier and removes the member if last role.
    *
    * @param role Role to remove
    */
  def removeRole(role: RoleType) = {
    this.roleAccess.remove(role)
    val user = role.user
    if (!this.roles.exists(_.userId === user.id.get))
      removeMember(user)
  }

  /**
    * Clears all user roles and removes the user from the dossier.
    *
    * @param user User to remove
    * @return
    */
  def removeMember(user: User) = {
    clearRoles(user)
    this.association.remove(user)
  }

}

object MembershipDossier {

  val STATUS_DECLINE = "decline"
  val STATUS_ACCEPT = "accept"
  val STATUS_UNACCEPT = "unaccept"

}
