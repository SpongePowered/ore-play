package models.user

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import db.impl.schema.OrganizationTable
import db.{Model, ModelService, Named, ObjectId, ObjectReference, ObjectTimestamp}
import models.user.role.OrganizationUserRole
import ore.organization.OrganizationMember
import ore.permission.role.Role
import ore.permission.scope.HasScope
import ore.user.{MembershipDossier, UserOwned}
import ore.{Joinable, Visitable}
import security.spauth.SpongeAuthApi

import cats.data.OptionT

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
case class Organization(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    username: String,
    ownerId: ObjectReference
) extends Model
    with UserOwned
    with Named
    with Visitable
    with Joinable[OrganizationMember, Organization] {

  override type M = Organization
  override type T = OrganizationTable

  /**
    * Contains all information for [[User]] memberships.
    */
  override def memberships(
      implicit ec: ExecutionContext,
      service: ModelService
  ): MembershipDossier.Aux[Future, Organization, OrganizationUserRole, OrganizationMember] =
    MembershipDossier[Future, Organization]

  /**
    * Returns the [[User]] that owns this Organization.
    *
    * @return User that owns organization
    */
  override def owner(implicit service: ModelService): OrganizationMember =
    new OrganizationMember(this, this.ownerId)

  override def transferOwner(
      member: OrganizationMember
  )(implicit ec: ExecutionContext, service: ModelService): Future[Organization] =
    // Down-grade current owner to "Admin"
    for {
      (owner, memberUser)  <- this.owner.user.zip(member.user)
      (roles, memberRoles) <- this.memberships.getRoles(this, owner).zip(this.memberships.getRoles(this, memberUser))
      setOwner             <- service.update(copy(ownerId = memberUser.id.value))
      _ <- Future.sequence(
        roles
          .filter(_.role == Role.OrganizationOwner)
          .map(role => service.update(role.copy(role = Role.OrganizationAdmin)))
      )
      _ <- Future.sequence(memberRoles.map(role => service.update(role.copy(role = Role.OrganizationOwner))))
    } yield setOwner

  /**
    * Returns this Organization as a [[User]].
    *
    * @return This Organization as a User
    */
  def toUser(implicit ec: ExecutionContext, users: UserBase, auth: SpongeAuthApi): OptionT[Future, User] =
    users.withName(this.username)

  override val name: String                                            = this.username
  override def url: String                                             = this.username
  override val userId: ObjectReference                                 = this.ownerId
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(createdAt = theTime)

}

object Organization {
  implicit val orgHasScope: HasScope[Organization] = HasScope.orgScope(_.id.value)
}
