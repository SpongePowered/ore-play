package models.user

import scala.language.higherKinds

import java.sql.Timestamp

import play.api.i18n.Lang

import db.impl.OrePostgresDriver.api._
import db.impl.common.Named
import db.impl.query.UserQueries
import db.impl.schema._
import db.impl.{ModelCompanionPartial, OrePostgresDriver}
import models.project.{Flag, Project, Visibility}
import models.user.role.{DbRole, OrganizationUserRole, ProjectUserRole}
import ore.OreConfig
import ore.db.access._
import ore.db._
import ore.permission._
import ore.permission.scope._
import ore.user.Prompt
import security.spauth.SpongeUser
import util.syntax._

import cats.effect.IO
import com.google.common.base.Preconditions._
import slick.lifted.TableQuery

/**
  * Represents a Sponge user.
  *
  * @param id           External ID provided by authentication.
  * @param fullName     Full name of user
  * @param name         Username
  * @param email        Email
  * @param tagline      The user configured "tagline" displayed on the user page.
  */
case class User(
    private val id: ObjId[User],
    fullName: Option[String] = None,
    name: String = "",
    email: Option[String] = None,
    tagline: Option[String] = None,
    joinDate: Option[Timestamp] = None,
    readPrompts: List[Prompt] = Nil,
    isLocked: Boolean = false,
    lang: Option[Lang] = None
) extends Named {

  //TODO: Check this in some way
  //checkArgument(tagline.forall(_.length <= config.users.get[Int]("max-tagline-len")), "tagline too long", "")

  def avatarUrl(implicit config: OreConfig): String = User.avatarUrl(name)

  /**
    * Returns this user's current language, or the default language if none
    * was configured.
    */
  implicit def langOrDefault: Lang = lang.getOrElse(Lang.defaultLang)
}

object User extends ModelCompanionPartial[User, UserTable](TableQuery[UserTable]) {

  override def asDbModel(
      model: User,
      id: ObjId[User],
      time: ObjTimestamp
  ): Model[User] = Model(model.id, time, model)

  /**
    * Copy this User with the information SpongeUser provides.
    *
    * @param user Sponge User
    */
  def fromSponge(user: SpongeUser): User = User(
    id = ObjId(user.id),
    fullName = None,
    name = user.username,
    email = Some(user.email),
    lang = user.lang,
    tagline = None,
    joinDate = None,
    readPrompts = Nil
  )

  implicit val query: ModelQuery[User] =
    ModelQuery.from(this)

  implicit val assocMembersQuery: AssociationQuery[ProjectMembersTable, User, Project] =
    AssociationQuery.from[ProjectMembersTable, User, Project](TableQuery[ProjectMembersTable])(_.userId, _.projectId)

  implicit val assocOrgMembersQuery: AssociationQuery[OrganizationMembersTable, User, Organization] =
    AssociationQuery.from[OrganizationMembersTable, User, Organization](TableQuery[OrganizationMembersTable])(
      _.userId,
      _.organizationId
    )

  implicit val assocStarsQuery: AssociationQuery[ProjectStarsTable, User, Project] =
    AssociationQuery.from[ProjectStarsTable, User, Project](TableQuery[ProjectStarsTable])(_.userId, _.projectId)

  implicit val assocRolesQuery: AssociationQuery[UserGlobalRolesTable, User, DbRole] =
    AssociationQuery.from[UserGlobalRolesTable, User, DbRole](TableQuery[UserGlobalRolesTable])(_.userId, _.roleId)

  def avatarUrl(name: String)(implicit config: OreConfig): String =
    config.security.api.avatarUrl.format(name)

  implicit class UserModelOps(private val self: Model[User]) extends AnyVal {

    /**
      * Returns the [[DbRole]]s that this User has.
      *
      * @return Roles the user has.
      */
    def globalRoles(
        implicit service: ModelService
    ): ParentAssociationAccess[UserGlobalRolesTable, User, DbRole, UserTable, DbRoleTable, IO] =
      new ModelAssociationAccessImpl(OrePostgresDriver)(User, DbRole).applyParent(self)

    /**
      * Returns the [[Organization]]s that this User belongs to.
      *
      * @return Organizations user belongs to
      */
    def organizations(
        implicit service: ModelService
    ): ParentAssociationAccess[OrganizationMembersTable, User, Organization, UserTable, OrganizationTable, IO] =
      new ModelAssociationAccessImpl(OrePostgresDriver)(User, Organization).applyParent(self)

    /**
      * Returns the [[Project]]s that this User is watching.
      *
      * @return Projects user is watching
      */
    def watching(
        implicit service: ModelService
    ): ChildAssociationAccess[ProjectWatchersTable, Project, User, ProjectTableMain, UserTable, IO] =
      new ModelAssociationAccessImpl(OrePostgresDriver)(Project, User).applyChild(self)

    /**
      * Sets the "watching" status on the specified project.
      *
      * @param project  Project to update status on
      * @param watching True if watching
      */
    def setWatching(
        project: Model[Project],
        watching: Boolean
    )(implicit service: ModelService): IO[Unit] = {
      val contains = self.watching.contains(project)
      contains.flatMap {
        case true  => if (!watching) self.watching.removeAssoc(project) else IO.unit
        case false => if (watching) self.watching.addAssoc(project) else IO.unit
      }
    }

    def permissionsIn[A: HasScope](a: A)(implicit service: ModelService): IO[Permission] =
      permissionsIn(a.scope)

    /**
      * Returns this User's highest level of Trust.
      *
      * @return Highest level of trust
      */
    def permissionsIn(scope: Scope = GlobalScope)(implicit service: ModelService): IO[Permission] = {
      val conIO = scope match {
        case GlobalScope             => UserQueries.globalPermission(self.id.value).unique
        case ProjectScope(projectId) => UserQueries.projectPermission(self.id.value, projectId).unique
        case OrganizationScope(organizationId) =>
          UserQueries.organizationPermission(self.id.value, organizationId).unique
      }

      service.runDbCon(conIO)
    }

    /**
      * Returns the Projects that this User has starred.
      *
      * @return Projects user has starred
      */
    def starred()(implicit service: ModelService): IO[Seq[Model[Project]]] = {
      val filter = Visibility.isPublicFilter[ProjectTableMain]

      val baseQuery = for {
        assoc   <- TableQuery[ProjectStarsTable] if assoc.userId === self.id.value
        project <- TableQuery[ProjectTableMain] if assoc.projectId === project.id
        if filter(project)
      } yield project

      service.runDBIO(baseQuery.sortBy(_.name).result)
    }

    /**
      * Returns all [[Project]]s owned by this user.
      *
      * @return Projects owned by user
      */
    def projects[V[_, _]: QueryView](
        view: V[ProjectTableMain, Model[Project]]
    ): V[ProjectTableMain, Model[Project]] =
      view.filterView(_.userId === self.id.value)

    /**
      * Returns a [[ModelView]] of [[ProjectUserRole]]s.
      *
      * @return ProjectRoles
      */
    def projectRoles[V[_, _]: QueryView](
        view: V[ProjectRoleTable, Model[ProjectUserRole]]
    ): V[ProjectRoleTable, Model[ProjectUserRole]] =
      view.filterView(_.userId === self.id.value)

    /**
      * Returns the [[Organization]]s that this User owns.
      *
      * @return Organizations user owns
      */
    def ownedOrganizations[V[_, _]: QueryView](
        view: V[OrganizationTable, Model[Organization]]
    ): V[OrganizationTable, Model[Organization]] =
      view.filterView(_.userId === self.id.value)

    /**
      * Returns a [[ModelView]] of [[OrganizationUserRole]]s.
      *
      * @return OrganizationRoles
      */
    def organizationRoles[V[_, _]: QueryView](
        view: V[OrganizationRoleTable, Model[OrganizationUserRole]]
    ): V[OrganizationRoleTable, Model[OrganizationUserRole]] =
      view.filterView(_.userId === self.id.value)

    /**
      * Converts this User to an [[Organization]].
      *
      * @return Organization
      */
    def toMaybeOrganization[QOptRet, SRet[_]](
        view: ModelView[QOptRet, SRet, OrganizationTable, Model[Organization]]
    ): QOptRet = view.get(self.id.value)

    /**
      * Returns the [[Flag]]s submitted by this User.
      *
      * @return Flags submitted by user
      */
    def flags[V[_, _]: QueryView](view: V[FlagTable, Model[Flag]]): V[FlagTable, Model[Flag]] =
      view.filterView(_.userId === self.id.value)

    /**
      * Returns true if the User has an unresolved [[Flag]] on the specified
      * [[Project]].
      *
      * @param project Project to check
      * @return True if has pending flag on Project
      */
    def hasUnresolvedFlagFor[QOptRet, SRet[_]](
        project: Model[Project],
        view: ModelView[QOptRet, SRet, FlagTable, Model[Flag]]
    ): SRet[Boolean] =
      flags(view).exists(f => f.projectId === project.id.value && !f.isResolved)

    /**
      * Returns this User's notifications.
      *
      * @return User notifications
      */
    def notifications[V[_, _]: QueryView](
        view: V[NotificationTable, Model[Notification]]
    ): V[NotificationTable, Model[Notification]] =
      view.filterView(_.userId === self.id.value)

    /**
      * Marks a [[Prompt]] as read by this User.
      *
      * @param prompt Prompt to mark as read
      */
    def markPromptAsRead(prompt: Prompt)(implicit service: ModelService): IO[Model[User]] = {
      checkNotNull(prompt, "null prompt", "")
      service.update(self)(
        _.copy(
          readPrompts = self.readPrompts :+ prompt
        )
      )
    }
  }
}
