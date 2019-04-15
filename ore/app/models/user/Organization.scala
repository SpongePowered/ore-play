package models.user

import db.impl.ModelCompanionPartial
import db.impl.access.UserBase
import db.impl.common.Named
import db.impl.schema.{OrganizationRoleTable, OrganizationTable}
import models.user.role.OrganizationUserRole
import ore.db._
import ore.organization.OrganizationMember
import ore.permission.role.Role
import ore.permission.scope.HasScope
import ore.user.{MembershipDossier, UserOwned}
import ore.{Joinable, JoinableOps, Visitable}
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
  * @param id             External ID provided by authentication.
  * @param ownerId        The ID of the [[User]] that owns this organization
  */
case class Organization(
    private val id: ObjId[Organization],
    username: String,
    ownerId: DbRef[User]
) extends Named
    with Visitable
    with Joinable {

  /**
    * Returns this Organization as a [[User]].
    *
    * @return This Organization as a User
    */
  def toUser(implicit users: UserBase, auth: SpongeAuthApi, mdc: OreMDC): OptionT[IO, Model[User]] =
    users.withName(this.username)

  override val name: String = this.username
  override def url: String  = this.username
}

object Organization extends ModelCompanionPartial[Organization, OrganizationTable](TableQuery[OrganizationTable]) {
  implicit val orgHasScope: HasScope[Organization] = HasScope.orgScope(_.id.value)

  override def asDbModel(
      model: Organization,
      id: ObjId[Organization],
      time: ObjTimestamp
  ): Model[Organization] = Model(model.id, time, model)

  implicit val query: ModelQuery[Organization] =
    ModelQuery.from(this)

  implicit val isUserOwned: UserOwned[Organization] = (a: Organization) => a.ownerId

  implicit class OrganizationModelOps(private val self: Model[Organization])
      extends AnyVal
      with JoinableOps[Organization, OrganizationMember] {

    /**
      * Contains all information for [[User]] memberships.
      */
    override def memberships(
        implicit service: ModelService
    ): MembershipDossier.Aux[IO, Organization, OrganizationUserRole, OrganizationRoleTable, OrganizationMember] =
      MembershipDossier[IO, Organization]

    /**
      * Returns the [[User]] that owns this Organization.
      *
      * @return User that owns organization
      */
    override def owner(implicit service: ModelService): OrganizationMember =
      new OrganizationMember(self, self.ownerId)

    override def transferOwner(
        member: OrganizationMember
    )(implicit service: ModelService, cs: ContextShift[IO]): IO[Model[Organization]] = {
      import cats.instances.vector._
      // Down-grade current owner to "Admin"
      for {
        t1 <- (owner.user, member.user).parTupled
        (owner, memberUser) = t1
        t2 <- (memberships.getRoles(self, owner), memberships.getRoles(self, memberUser)).parTupled
        (roles, memberRoles) = t2
        setOwner <- service.update(self)(_.copy(ownerId = memberUser.id))
        _ <- roles
          .filter(_.role == Role.OrganizationOwner)
          .map(role => service.update(role)(_.copy(role = Role.OrganizationAdmin)))
          .toVector
          .parSequence
        _ <- memberRoles.toVector.parTraverse(role => service.update(role)(_.copy(role = Role.OrganizationOwner)))
      } yield setOwner
    }
  }
}
