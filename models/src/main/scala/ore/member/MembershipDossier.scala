package ore.member

import scala.language.{higherKinds, implicitConversions}

import ore.db._
import ore.db.access.ModelView.Now
import ore.db.access.{ModelAssociationAccess, ModelAssociationAccessImpl, ModelView}
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema._
import ore.db.impl.table.common.RoleTable
import ore.models.organization.Organization
import ore.models.project.Project
import ore.models.user.User
import ore.models.user.role.{OrganizationUserRole, ProjectUserRole, UserRoleModel}

import cats.Monad
import cats.syntax.all._

/**
  * Handles and keeps track of [[User]] "memberships" for a model.
  */
trait MembershipDossier[F[_], M] {
  type RoleType <: UserRoleModel[RoleType]
  type RoleTypeTable <: RoleTable[RoleType]

  def roles(model: Model[M]): ModelView.Now[F, RoleTypeTable, Model[RoleType]]

  /**
    * Clears the roles of a User
    *
    * @param user User instance
    */
  def clearRoles(model: Model[M])(user: DbRef[User]): F[Int]

  /**
    * Returns all members of the model. This includes members that have not
    * yet accepted membership.
    *
    * @return All members
    */
  def members(model: Model[M]): F[Set[DbRef[User]]]

  /**
    * Adds a new role to the dossier and adds the user as a member if not already.
    *
    * @param role Role to add
    */
  def addRole(model: Model[M])(userId: DbRef[User], role: RoleType): F[RoleType]

  /**
    * Returns all roles for the specified [[User]].
    *
    * @param user User to get roles for
    * @return     User roles
    */
  def getRoles(model: Model[M])(user: DbRef[User]): F[Set[Model[RoleType]]]

  /**
    * Removes a role from the dossier and removes the member if last role.
    *
    * @param role Role to remove
    */
  def removeRole(model: Model[M])(role: DbRef[RoleType]): F[Unit]

  /**
    * Clears all user roles and removes the user from the dossier.
    *
    * @param user User to remove
    * @return
    */
  def removeMember(model: Model[M])(user: DbRef[User]): F[Unit]
}

object MembershipDossier {

  type Aux[F[_], M, RoleType0 <: UserRoleModel[RoleType0], RoleTypeTable0 <: RoleTable[RoleType0]] =
    MembershipDossier[F, M] {
      type RoleType      = RoleType0
      type RoleTypeTable = RoleTypeTable0
    }

  def apply[F[_], M](
      implicit dossier: MembershipDossier[F, M]
  ): Aux[F, M, dossier.RoleType, dossier.RoleTypeTable] = dossier

  abstract class AbstractMembershipDossier[F[_], M, RoleType0 <: UserRoleModel[RoleType0], RoleTypeTable0 <: RoleTable[
    RoleType0
  ], MembersTable <: AssociativeTable[User, M]](
      M: ModelCompanion[M],
      RoleType: ModelCompanion.Aux[RoleType0, RoleTypeTable0]
  )(implicit service: ModelService[F], F: Monad[F], assocQuery: AssociationQuery[MembersTable, User, M])
      extends MembershipDossier[F, M] {
    override type RoleType      = RoleType0
    override type RoleTypeTable = RoleTypeTable0

    private def association: ModelAssociationAccess[MembersTable, User, M, UserTable, M.T, F] =
      new ModelAssociationAccessImpl(ore.db.impl.OrePostgresDriver)(User, M)

    private def addMember(model: DbRef[M], user: DbRef[User]) =
      association.addAssoc(user, model)

    override def members(model: Model[M]): F[Set[DbRef[User]]] =
      association
        .allFromChild(model.id)
        .map(_.map(_.id.value).toSet)

    override def addRole(model: Model[M])(userId: DbRef[User], role: RoleType): F[RoleType] =
      for {
        exists <- roles(model).exists(_.userId === userId)
        _      <- if (!exists) addMember(model.id, userId) else F.unit
        ret    <- service.insertRaw(RoleType)(role)
      } yield ret

    override def getRoles(model: Model[M])(user: DbRef[User]): F[Set[Model[RoleType]]] =
      service.runDBIO(roles(model).filterView(_.userId === user).query.to[Set].result)

    override def removeRole(model: Model[M])(role: DbRef[RoleType]): F[Unit] =
      for {
        userId <- service.runDBIO(RoleType.baseQuery.filter(_.id === role).map(t => t.userId).result.head)
        _      <- service.deleteWhere(RoleType)(_.id === role)
        exists <- roles(model).exists(_.userId === userId)
        _      <- if (!exists) removeMember(model)(userId) else F.unit
      } yield ()

    override def removeMember(model: Model[M])(user: DbRef[User]): F[Unit] =
      clearRoles(model)(user) *> association.removeAssoc(user, model.id)
  }

  implicit def projectHasMemberships[F[_]](
      implicit service: ModelService[F],
      F: Monad[F]
  ): MembershipDossier.Aux[F, Project, ProjectUserRole, ProjectRoleTable] =
    new AbstractMembershipDossier[F, Project, ProjectUserRole, ProjectRoleTable, ProjectMembersTable](
      Project,
      ProjectUserRole
    ) {

      override def roles(model: Model[Project]): Now[F, ProjectRoleTable, Model[ProjectUserRole]] =
        ModelView.now(ProjectUserRole).filterView(_.projectId === model.id.value)

      override def clearRoles(model: Model[Project])(user: DbRef[User]): F[Int] =
        service.deleteWhere(ProjectUserRole)(s => (s.userId === user) && (s.projectId === model.id.value))
    }

  implicit def organizationHasMemberships[F[_]](
      implicit service: ModelService[F],
      F: Monad[F]
  ): MembershipDossier.Aux[F, Organization, OrganizationUserRole, OrganizationRoleTable] =
    new AbstractMembershipDossier[F, Organization, OrganizationUserRole, OrganizationRoleTable, OrganizationMembersTable](
      Organization,
      OrganizationUserRole
    ) {

      override def roles(model: Model[Organization]): Now[F, OrganizationRoleTable, Model[OrganizationUserRole]] =
        ModelView.now(OrganizationUserRole).filterView(_.organizationId === model.id.value)

      override def clearRoles(model: Model[Organization])(user: DbRef[User]): F[Int] =
        service.deleteWhere(OrganizationUserRole)(s => (s.userId === user) && (s.organizationId === model.id.value))
    }

  val STATUS_DECLINE  = "decline"
  val STATUS_ACCEPT   = "accept"
  val STATUS_UNACCEPT = "unaccept"
}
