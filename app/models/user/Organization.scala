package models.user

import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.impl.schema.{OrganizationMembersTable, OrganizationRoleTable, OrganizationTable}
import db.{AssociationQuery, Model, ModelQuery, ModelService, Named, ObjectId, ObjectReference, ObjectTimestamp}
import models.user.role.OrganizationRole
import ore.organization.OrganizationMember
import ore.permission.role.{Default, RoleType, Trust}
import ore.permission.scope.HasScope
import ore.user.{MembershipDossier, UserOwned}
import ore.{Joinable, Visitable}
import security.spauth.SpongeAuthApi

import cats.data.OptionT
import slick.lifted.{Compiled, Rep, TableQuery}

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
  ): MembershipDossier.Aux[Future, Organization, OrganizationRole, OrganizationMember] =
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
          .filter(_.roleType == RoleType.OrganizationOwner)
          .map(role => service.update(role.copy(roleType = RoleType.OrganizationAdmin)))
      )
      _ <- Future.sequence(memberRoles.map(role => service.update(role.copy(roleType = RoleType.OrganizationOwner))))
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
  implicit val query: ModelQuery[Organization] =
    ModelQuery.from[Organization](TableQuery[OrganizationTable])

  implicit val assocMembersQuery: AssociationQuery[OrganizationMembersTable, Organization, User] =
    AssociationQuery.from(TableQuery[OrganizationMembersTable])(_.organizationId, _.userId)

  implicit val orgHasScope: HasScope[Organization] = HasScope.orgScope(_.id.value)

  lazy val roleForTrustQuery = Compiled(queryRoleForTrust _)

  private def queryRoleForTrust(orgId: Rep[ObjectReference], userId: Rep[ObjectReference]) =
    for {
      m <- TableQuery[OrganizationMembersTable] if m.organizationId === orgId && m.userId === userId
      r <- TableQuery[OrganizationRoleTable] if m.userId === r.userId && r.organizationId === orgId
    } yield r.roleType

  /**
    * Returns the highest level of [[ore.permission.role.Trust]] this user has.
    *
    * @param user User to get trust of
    * @return Trust of user
    */
  def getTrust(
      userId: ObjectReference,
      orgId: ObjectReference
  )(implicit ex: ExecutionContext, service: ModelService): Future[Trust] =
    service.DB.db
      .run(Organization.roleForTrustQuery((orgId, userId)).result)
      .map(_.sortBy(_.trust).headOption.map(_.trust).getOrElse(Default))
}
