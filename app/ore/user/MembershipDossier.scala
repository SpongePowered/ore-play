package ore.user

import scala.language.{higherKinds, implicitConversions}

import db.access.{ModelAccess, ModelAssociationAccess, ModelAssociationAccessImpl}
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{OrganizationMembersTable, ProjectMembersTable}
import db.table.AssociativeTable
import db.{AssociationQuery, DbRef, InsertFunc, Model, ModelQuery, ModelService}
import models.project.Project
import models.user.role.{OrganizationUserRole, ProjectUserRole, UserRoleModel}
import models.user.{Organization, User}
import ore.organization.OrganizationMember
import ore.project.ProjectMember
import util.syntax._

import cats.effect.IO
import cats.syntax.all._

/**
  * Handles and keeps track of [[User]] "memberships" for an [[Model]].
  */
trait MembershipDossier[F[_], M <: Model] {
  type RoleType <: UserRoleModel { type M = RoleType }
  type MemberType <: Member[RoleType]

  def roles(model: M): ModelAccess[RoleType]

  def roleAccess: ModelAccess[RoleType]

  /**
    * Clears the roles of a User
    *
    * @param user User instance
    */
  def clearRoles(model: M, user: User): F[Int]

  /**
    * Constructs a new member object of the MemberType.
    *
    * @param userId User ID of member
    * @return       New Member
    */
  def newMember(model: M, userId: DbRef[User]): MemberType

  /**
    * Returns all members of the model. This includes members that have not
    * yet accepted membership.
    *
    * @return All members
    */
  def members(model: M): F[Set[MemberType]]

  /**
    * Adds a new role to the dossier and adds the user as a member if not already.
    *
    * @param role Role to add
    */
  def addRole(model: M, userId: DbRef[User], role: InsertFunc[RoleType]): F[RoleType]

  /**
    * Returns all roles for the specified [[User]].
    *
    * @param user User to get roles for
    * @return     User roles
    */
  def getRoles(model: M, user: User): F[Set[RoleType]]

  /**
    * Removes a role from the dossier and removes the member if last role.
    *
    * @param role Role to remove
    */
  def removeRole(model: M, role: RoleType): F[Unit]

  /**
    * Clears all user roles and removes the user from the dossier.
    *
    * @param user User to remove
    * @return
    */
  def removeMember(model: M, user: User): F[Unit]
}

object MembershipDossier {

  type Aux[F[_], M <: Model, RoleType0 <: UserRoleModel, MemberType0 <: Member[RoleType0]] = MembershipDossier[F, M] {
    type RoleType   = RoleType0
    type MemberType = MemberType0
  }

  def apply[F[_], M <: Model](
      implicit dossier: MembershipDossier[F, M]
  ): Aux[F, M, dossier.RoleType, dossier.MemberType] = dossier

  abstract class AbstractMembershipDossier[
      M0 <: Model { type M                = M0 }: ModelQuery,
      RoleType0 <: UserRoleModel { type M = RoleType0 }: ModelQuery,
      MembersTable <: AssociativeTable[User, M0]
  ](childFilter: (RoleType0#T, M0) => Rep[Boolean])(
      implicit service: ModelService,
      assocQuery: AssociationQuery[MembersTable, User, M0],
      roleUserOwned: UserOwned[RoleType0]
  ) extends MembershipDossier[IO, M0] {

    type RoleType = RoleType0

    private def association: ModelAssociationAccess[MembersTable, User, M0, IO] =
      new ModelAssociationAccessImpl

    private def addMember(model: M0, user: User) =
      association.addAssoc(user, model)

    def roles(model: M0): ModelAccess[RoleType] = service.access[RoleType](childFilter(_, model))

    def roleAccess: ModelAccess[RoleType] =
      service.access[RoleType]()

    def members(model: M0): IO[Set[MemberType]] =
      association
        .allFromChild(model)
        .map(_.map(user => newMember(model, user.id.value)).toSet)

    def addRole(model: M0, userId: DbRef[User], role: InsertFunc[RoleType]): IO[RoleType] = {
      for {
        user   <- service.get[User](userId).getOrElseF(IO.raiseError(new Exception("Get on none")))
        exists <- roles(model).exists(_.userId === user.id.value)
        _      <- if (!exists) addMember(model, user) else IO.pure(user)
        ret    <- roleAccess.add(role)
      } yield ret
    }

    def getRoles(model: M0, user: User): IO[Set[RoleType]] =
      roles(model).filter(_.userId === user.id.value).map(_.toSet)

    def removeRole(model: M0, role: RoleType): IO[Unit] = {
      for {
        _      <- roleAccess.remove(role)
        user   <- role.user
        exists <- roles(model).exists(_.userId === user.id.value)
        _      <- if (!exists) removeMember(model, user) else IO.pure(0)
      } yield ()
    }

    def removeMember(model: M0, user: User): IO[Unit] =
      clearRoles(model, user) *> association.removeAssoc(user, model)
  }

  implicit def project(
      implicit service: ModelService
  ): Aux[IO, Project, ProjectUserRole, ProjectMember] =
    new AbstractMembershipDossier[Project, ProjectUserRole, ProjectMembersTable](_.projectId === _.id.value) {
      override type MemberType = ProjectMember

      override def newMember(model: Project, userId: DbRef[User]): ProjectMember = new ProjectMember(model, userId)

      override def clearRoles(model: Project, user: User): IO[Int] =
        this.roleAccess.removeAll(s => (s.userId === user.id.value) && (s.projectId === model.id.value))
    }

  implicit def organization(
      implicit service: ModelService
  ): Aux[IO, Organization, OrganizationUserRole, OrganizationMember] =
    new AbstractMembershipDossier[Organization, OrganizationUserRole, OrganizationMembersTable](
      _.organizationId === _.id.value
    ) {
      override type MemberType = OrganizationMember

      override def newMember(model: Organization, userId: DbRef[User]): OrganizationMember =
        new OrganizationMember(model, userId)

      override def clearRoles(model: Organization, user: User): IO[Int] =
        this.roleAccess.removeAll(s => (s.userId === user.id.value) && (s.organizationId === model.id.value))
    }

  val STATUS_DECLINE  = "decline"
  val STATUS_ACCEPT   = "accept"
  val STATUS_UNACCEPT = "unaccept"

}
