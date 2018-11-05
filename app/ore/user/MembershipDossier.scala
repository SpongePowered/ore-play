package ore.user

import scala.language.{higherKinds, implicitConversions}
import scala.concurrent.{ExecutionContext, Future}

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{OrganizationMembersTable, ProjectMembersTable}
import db.table.AssociativeTable
import db.{AssociationQuery, Model, ModelFilter, ModelQuery, ModelService, ObjectReference}
import models.project.Project
import models.user.role.{OrganizationUserRole, ProjectUserRole, UserRoleModel}
import models.user.{Organization, User}
import ore.organization.OrganizationMember
import ore.permission.role.Trust
import ore.project.ProjectMember
import cats.instances.future._
import cats.syntax.all._

/**
  * Handles and keeps track of [[User]] "memberships" for an [[Model]].
  */
trait MembershipDossier[F[_], M <: Model] {
  type RoleType <: UserRoleModel
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
  def newMember(model: M, userId: ObjectReference): MemberType

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
  def addRole(model: M, role: RoleType): F[RoleType]

  /**
    * Returns all roles for the specified [[User]].
    *
    * @param user User to get roles for
    * @return     User roles
    */
  def getRoles(model: M, user: User): F[Set[RoleType]]

  /**
    * Returns the highest level of [[ore.permission.role.Trust]] this user has.
    *
    * @param user User to get trust of
    * @return Trust of user
    */
  def getTrust(model: M, user: User): F[Trust]

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
  def removeMember(model: M, user: User): F[Int]
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
      M0 <: Model { type M = M0 },
      RoleType0 <: UserRoleModel: ModelQuery,
      MembersTable <: AssociativeTable
  ](childFilter: M0 => ModelFilter[RoleType0])(
      implicit ec: ExecutionContext,
      service: ModelService,
      assocQuery: AssociationQuery[MembersTable, M0, User]
  ) extends MembershipDossier[Future, M0] {

    type RoleType = RoleType0

    private def association(model: M0) =
      service.associationAccess[MembersTable, M0, User](model)

    private def addMember(model: M0, user: User) =
      association(model).add(user)

    def roles(model: M0): ModelAccess[RoleType] = service.access[RoleType](childFilter(model))

    def roleAccess: ModelAccess[RoleType] =
      service.access[RoleType]()

    def members(model: M0): Future[Set[MemberType]] =
      association(model).all.map(_.map { user =>
        newMember(model, user.id.value)
      })

    def addRole(model: M0, role: RoleType): Future[RoleType] = {
      for {
        user   <- role.user
        exists <- roles(model).exists(_.userId === user.id.value)
        _      <- if (!exists) addMember(model, user) else Future.successful(user)
        ret    <- roleAccess.add(role)
      } yield ret
    }

    def getRoles(model: M0, user: User): Future[Set[RoleType]] =
      roles(model).filter(_.userId === user.id.value).map(_.toSet)

    def removeRole(model: M0, role: RoleType): Future[Unit] = {
      for {
        _      <- roleAccess.remove(role)
        user   <- role.user
        exists <- roles(model).exists(_.userId === user.id.value)
        _      <- if (!exists) removeMember(model, user) else Future.successful(0)
      } yield ()
    }

    def removeMember(model: M0, user: User): Future[Int] =
      clearRoles(model, user) *> association(model).remove(user)
  }

  implicit def project(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Aux[Future, Project, ProjectUserRole, ProjectMember] =
    new AbstractMembershipDossier[Project, ProjectUserRole, ProjectMembersTable](
      p => ModelFilter(_.projectId === p.id.value)
    ) {
      override type MemberType = ProjectMember

      override def newMember(model: Project, userId: ObjectReference): ProjectMember = new ProjectMember(model, userId)

      override def getTrust(model: Project, user: User): Future[Trust] =
        service
          .doAction(Project.roleForTrustQuery((model.id.value, user.id.value)).result)
          .map(l => if (l.isEmpty) Trust.Default else l.map(_.trust).max)

      override def clearRoles(model: Project, user: User): Future[Int] =
        this.roleAccess.removeAll(s => (s.userId === user.id.value) && (s.projectId === model.id.value))
    }

  implicit def organization(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Aux[Future, Organization, OrganizationUserRole, OrganizationMember] =
    new AbstractMembershipDossier[Organization, OrganizationUserRole, OrganizationMembersTable](
      org => ModelFilter(_.organizationId === org.id.value)
    ) {
      override type MemberType = OrganizationMember

      override def newMember(model: Organization, userId: ObjectReference): OrganizationMember =
        new OrganizationMember(model, userId)

      override def getTrust(model: Organization, user: User): Future[Trust] =
        Organization.getTrust(user.id.value, model.id.value)

      override def clearRoles(model: Organization, user: User): Future[Int] =
        this.roleAccess.removeAll(s => (s.userId === user.id.value) && (s.organizationId === model.id.value))
    }

  val STATUS_DECLINE  = "decline"
  val STATUS_ACCEPT   = "accept"
  val STATUS_UNACCEPT = "unaccept"

}
