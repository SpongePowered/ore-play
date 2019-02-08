package models.user

import scala.language.higherKinds

import java.sql.Timestamp

import play.api.i18n.Lang

import db._
import db.access.{ModelAssociationAccess, ModelAssociationAccessImpl, ModelView, QueryView}
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.Named
import db.impl.schema._
import db.query.UserQueries
import models.project.{Flag, Project, Visibility}
import models.user.role.{DbRole, OrganizationUserRole, ProjectUserRole}
import ore.OreConfig
import ore.permission._
import ore.permission.role._
import ore.permission.scope._
import ore.user.Prompt
import security.pgp.PGPPublicKeyInfo
import security.spauth.SpongeUser
import util.syntax._

import cats.data.OptionT
import cats.effect.IO
import com.google.common.base.Preconditions._
import slick.lifted.TableQuery

/**
  * Represents a Sponge user.
  *
  * @param id           External ID provided by authentication.
  * @param createdAt    Date this user first logged onto Ore.
  * @param fullName     Full name of user
  * @param name         Username
  * @param email        Email
  * @param tagline      The user configured "tagline" displayed on the user page.
  */
case class User private (
    id: ObjId[User],
    createdAt: ObjTimestamp,
    fullName: Option[String],
    name: String,
    email: Option[String],
    tagline: Option[String],
    joinDate: Option[Timestamp],
    readPrompts: List[Prompt],
    pgpPubKey: Option[String],
    lastPgpPubKeyUpdate: Option[Timestamp],
    isLocked: Boolean,
    lang: Option[Lang]
) extends Model
    with Named {

  //TODO: Check this in some way
  //checkArgument(tagline.forall(_.length <= config.users.get[Int]("max-tagline-len")), "tagline too long", "")

  override type M = User
  override type T = UserTable

  /**
    * The User's [[PermissionPredicate]]. All permission checks go through
    * here.
    */
  val can: PermissionPredicate = PermissionPredicate(this)

  def avatarUrl(implicit config: OreConfig): String = User.avatarUrl(name)

  /**
    * Decodes this user's raw PGP public key and returns information about the
    * key.
    *
    * @return Public key information
    */
  def pgpPubKeyInfo: Option[PGPPublicKeyInfo] = this.pgpPubKey.flatMap(PGPPublicKeyInfo.decode(_).toOption)

  /**
    * Returns true if this user's PGP Public Key is ready for use.
    *
    * @return True if key is ready for use
    */
  def isPgpPubKeyReady(implicit config: OreConfig, service: ModelService): Boolean =
    this.pgpPubKey.isDefined && this.lastPgpPubKeyUpdate.forall { lastUpdate =>
      val cooldown = config.security.keyChangeCooldown
      val minTime  = new Timestamp(lastUpdate.getTime + cooldown)
      minTime.before(service.theTime)
    }

  /**
    * Returns this user's current language, or the default language if none
    * was configured.
    */
  implicit def langOrDefault: Lang = lang.getOrElse(Lang.defaultLang)

  /**
    * Returns the [[DbRole]]s that this User has.
    *
    * @return Roles the user has.
    */
  def globalRoles(
      implicit service: ModelService
  ): ModelAssociationAccess[UserGlobalRolesTable, User, DbRole, IO] =
    new ModelAssociationAccessImpl[UserGlobalRolesTable, User, DbRole]

  /**
    * Returns the highest level [[DonorRole]] this User has.
    *
    * @return Highest level donor type
    */
  def donorType(implicit service: ModelService): OptionT[IO, DonorRole] =
    OptionT(
      service.runDBIO(this.globalRoles.allQueryFromParent(this).filter(_.rank.?.isDefined).result).map { seq =>
        seq
          .map(_.toRole)
          .collect { case donor: DonorRole => donor }
          .sortBy(_.rank)
          .headOption
      }
    )

  def trustIn[A: HasScope](a: A)(implicit service: ModelService): IO[Trust] =
    trustIn(a.scope)

  /**
    * Returns this User's highest level of Trust.
    *
    * @return Highest level of trust
    */
  def trustIn(scope: Scope = GlobalScope)(implicit service: ModelService): IO[Trust] = {
    val conIO = scope match {
      case GlobalScope                       => UserQueries.globalTrust(id.value).unique
      case ProjectScope(projectId)           => UserQueries.projectTrust(id.value, projectId).unique
      case OrganizationScope(organizationId) => UserQueries.organizationTrust(id.value, organizationId).unique
    }

    service.runDbCon(conIO)
  }

  /**
    * Returns the Projects that this User has starred.
    *
    * @return Projects user has starred
    */
  def starred()(implicit service: ModelService): IO[Seq[Project]] = {
    val filter = Visibility.isPublicFilter[Project]

    val baseQuery = for {
      assoc   <- TableQuery[ProjectStarsTable] if assoc.userId === id.value
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
  def projects[V[_, _]: QueryView](view: V[Project#T, Project]): V[Project#T, Project] =
    view.filterView(_.userId === id.value)

  /**
    * Returns a [[ModelView]] of [[ProjectUserRole]]s.
    *
    * @return ProjectRoles
    */
  def projectRoles[V[_, _]: QueryView](
      view: V[ProjectUserRole#T, ProjectUserRole]
  ): V[ProjectUserRole#T, ProjectUserRole] =
    view.filterView(_.userId === id.value)

  /**
    * Returns the [[Organization]]s that this User owns.
    *
    * @return Organizations user owns
    */
  def ownedOrganizations[V[_, _]: QueryView](view: V[Organization#T, Organization]): V[Organization#T, Organization] =
    view.filterView(_.userId === id.value)

  /**
    * Returns the [[Organization]]s that this User belongs to.
    *
    * @return Organizations user belongs to
    */
  def organizations(
      implicit service: ModelService
  ): ModelAssociationAccess[OrganizationMembersTable, User, Organization, IO] = new ModelAssociationAccessImpl

  /**
    * Returns a [[ModelView]] of [[OrganizationUserRole]]s.
    *
    * @return OrganizationRoles
    */
  def organizationRoles[V[_, _]: QueryView](
      view: V[OrganizationUserRole#T, OrganizationUserRole]
  ): V[OrganizationUserRole#T, OrganizationUserRole] =
    view.filterView(_.userId === id.value)

  /**
    * Converts this User to an [[Organization]].
    *
    * @return Organization
    */
  def toMaybeOrganization[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, Organization#T, Organization]): QOptRet =
    view.get(this.id.value)

  /**
    * Returns the [[Project]]s that this User is watching.
    *
    * @return Projects user is watching
    */
  def watching(
      implicit service: ModelService
  ): ModelAssociationAccess[ProjectWatchersTable, Project, User, IO] =
    new ModelAssociationAccessImpl

  /**
    * Sets the "watching" status on the specified project.
    *
    * @param project  Project to update status on
    * @param watching True if watching
    */
  def setWatching(
      project: Project,
      watching: Boolean
  )(implicit service: ModelService): IO[Unit] = {
    val contains = this.watching.contains(project, this)
    contains.flatMap {
      case true  => if (!watching) this.watching.removeAssoc(project, this) else IO.unit
      case false => if (watching) this.watching.addAssoc(project, this) else IO.unit
    }
  }

  /**
    * Returns the [[Flag]]s submitted by this User.
    *
    * @return Flags submitted by user
    */
  def flags[V[_, _]: QueryView](view: V[Flag#T, Flag]): V[Flag#T, Flag] =
    view.filterView(_.userId === id.value)

  /**
    * Returns true if the User has an unresolved [[Flag]] on the specified
    * [[Project]].
    *
    * @param project Project to check
    * @return True if has pending flag on Project
    */
  def hasUnresolvedFlagFor[QOptRet, SRet[_]](
      project: Project,
      view: ModelView[QOptRet, SRet, Flag#T, Flag]
  ): SRet[Boolean] =
    this.flags(view).exists(f => f.projectId === project.id.value && !f.isResolved)

  /**
    * Returns this User's notifications.
    *
    * @return User notifications
    */
  def notifications[V[_, _]: QueryView](view: V[Notification#T, Notification]): V[Notification#T, Notification] =
    view.filterView(_.userId === id.value)

  /**
    * Marks a [[Prompt]] as read by this User.
    *
    * @param prompt Prompt to mark as read
    */
  def markPromptAsRead(prompt: Prompt)(implicit service: ModelService): IO[User] = {
    checkNotNull(prompt, "null prompt", "")
    service.update(
      copy(
        readPrompts = readPrompts :+ prompt
      )
    )
  }
}

object User {

  /**
    * Copy this User with the information SpongeUser provides.
    *
    * @param user Sponge User
    */
  def partialFromSponge(user: SpongeUser): InsertFunc[User] =
    (_, time) =>
      User(
        id = ObjId(user.id),
        createdAt = time,
        fullName = None,
        name = user.username,
        email = Some(user.email),
        lang = user.lang,
        tagline = None,
        joinDate = None,
        readPrompts = Nil,
        pgpPubKey = None,
        lastPgpPubKeyUpdate = None,
        isLocked = false
    )

  def partial(
      id: ObjId[User],
      fullName: Option[String] = None,
      name: String = "",
      email: Option[String] = None,
      tagline: Option[String] = None,
      joinDate: Option[Timestamp] = None,
      readPrompts: List[Prompt] = List(),
      pgpPubKey: Option[String] = None,
      lastPgpPubKeyUpdate: Option[Timestamp] = None,
      isLocked: Boolean = false,
      lang: Option[Lang] = None
  ): InsertFunc[User] =
    (_, time) =>
      User(
        id,
        time,
        fullName,
        name,
        email,
        tagline,
        joinDate,
        readPrompts,
        pgpPubKey,
        lastPgpPubKeyUpdate,
        isLocked,
        lang
    )

  implicit val query: ModelQuery[User] =
    ModelQuery.from[User](TableQuery[UserTable], (obj, _, time) => obj.copy(createdAt = time))

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
}
