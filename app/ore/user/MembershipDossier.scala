package ore.user

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.impl.model.OreModel
import db.table.AssociativeTable
import models.user.User
import models.user.role.RoleModel
import ore.permission.role.{Default, Trust}

import scala.concurrent.{ExecutionContext, Future}
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
  def roleAccess: ModelAccess[RoleType] = this.model.service.access[RoleType](roleClass)
  private def addMember(user: User)(implicit ec: ExecutionContext) = this.association.add(user)

  /**
    * Clears the roles of a User
    *
    * @param user User instance
    */
  def clearRoles(user: User): Unit

  /**
    * Constructs a new member object of the MemberType.
    *
    * @param userId User ID of member
    * @return       New Member
    */
  def newMember(userId: Int)(implicit ec: ExecutionContext): MemberType

  /**
    * Returns all members of the model. This includes members that have not
    * yet accepted membership.
    *
    * @return All members
    */
  def members(implicit ec: ExecutionContext): Future[Set[MemberType]] = {
    this.association.all.map(_.map { user =>
      newMember(user.id.get)
    })
  }

  /**
    * Adds a new role to the dossier and adds the user as a member if not already.
    *
    * @param role Role to add
    */
  def addRole(role: RoleType)(implicit ec: ExecutionContext) = {
    role.user.flatMap { user =>
      this.roles.exists(_.userId === user.id.get).flatMap { exists =>
        if (!exists) addMember(user) else Future.successful()
      } flatMap { _ =>
        this.roleAccess.add(role)
      }
    }
  }

  /**
    * Returns all roles for the specified [[User]].
    *
    * @param user User to get roles for
    * @return     User roles
    */
  def getRoles(user: User)(implicit ec: ExecutionContext): Future[Set[RoleType]] = this.roles.filter(_.userId === user.id.get).map(_.toSet)

  /**
    * Returns the highest level of [[ore.permission.role.Trust]] this user has.
    *
    * @param user User to get trust of
    * @return Trust of user
    */
  def getTrust(user: User)(implicit ex: ExecutionContext): Future[Trust]

  /**
    * Removes a role from the dossier and removes the member if last role.
    *
    * @param role Role to remove
    */
  def removeRole(role: RoleType)(implicit ec: ExecutionContext) = {
    val checks = for {
      _ <- this.roleAccess.remove(role)
      user   <- role.user
      exists <- this.roles.exists(_.userId === user.id.get)
    } yield {
      (exists, user)
    }
    checks.flatMap {
      case (exists, user) => if (!exists) removeMember(user) else Future.successful()
    }
  }

  /**
    * Clears all user roles and removes the user from the dossier.
    *
    * @param user User to remove
    * @return
    */
  def removeMember(user: User)(implicit ec: ExecutionContext) = {
    clearRoles(user) flatMap { _ =>
      this.association.remove(user)
    }
  }

}

object MembershipDossier {

  val STATUS_DECLINE = "decline"
  val STATUS_ACCEPT = "accept"
  val STATUS_UNACCEPT = "unaccept"

}
