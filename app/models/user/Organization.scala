package models.user

import db.impl.access.UserBase
import db.impl.model.common.Named
import db.impl.schema.OrganizationTable
import db._
import models.user.role.OrganizationUserRole
import ore.organization.OrganizationMember
import ore.permission.role.Role
import ore.permission.scope.HasScope
import ore.user.{MembershipDossier, UserOwned}
import ore.{Joinable, Visitable}
import security.spauth.SpongeAuthApi
import util.OreMDC
import util.syntax._

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import slick.lifted.TableQuery

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
case class Organization private (
    id: ObjId[Organization],
    createdAt: ObjTimestamp,
    username: String,
    ownerId: DbRef[User]
) extends Model
    with Named
    with Visitable
    with Joinable[OrganizationMember, Organization] {

  override type M = Organization
  override type T = OrganizationTable

  /**
    * Contains all information for [[User]] memberships.
    */
  override def memberships(
      implicit service: ModelService
  ): MembershipDossier.Aux[IO, Organization, OrganizationUserRole, OrganizationMember] =
    MembershipDossier[IO, Organization]

  /**
    * Returns the [[User]] that owns this Organization.
    *
    * @return User that owns organization
    */
  override def owner(implicit service: ModelService): OrganizationMember =
    new OrganizationMember(this, this.ownerId)

  override def transferOwner(
      member: OrganizationMember
  )(implicit service: ModelService, cs: ContextShift[IO]): IO[Organization] = {
    import cats.instances.vector._
    // Down-grade current owner to "Admin"
    for {
      t1 <- (owner.user, member.user).parTupled
      (owner, memberUser) = t1
      t2 <- (memberships.getRoles(this, owner), memberships.getRoles(this, memberUser)).parTupled
      (roles, memberRoles) = t2
      setOwner <- service.update(copy(ownerId = memberUser.id))
      _ <- roles
        .filter(_.role == Role.OrganizationOwner)
        .map(role => service.update(role.copy(role = Role.OrganizationAdmin)))
        .toVector
        .parSequence
      _ <- memberRoles.toVector.parTraverse(role => service.update(role.copy(role = Role.OrganizationOwner)))
    } yield setOwner
  }

  /**
    * Returns this Organization as a [[User]].
    *
    * @return This Organization as a User
    */
  def toUser(implicit users: UserBase, auth: SpongeAuthApi, mdc: OreMDC): OptionT[IO, User] =
    users.withName(this.username)

  override val name: String = this.username
  override def url: String  = this.username
}

object Organization {
  implicit val orgHasScope: HasScope[Organization] = HasScope.orgScope(_.id.value)

  def partial(id: ObjId[Organization], username: String, ownerId: DbRef[User]): InsertFunc[Organization] =
    (_, time) => Organization(id, time, username, ownerId)

  implicit val query: ModelQuery[Organization] =
    ModelQuery.from[Organization](TableQuery[OrganizationTable], (obj, _, time) => obj.copy(createdAt = time))

  implicit val isUserOwned: UserOwned[Organization] = (a: Organization) => a.ownerId
}
