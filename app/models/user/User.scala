package models.user

import java.sql.Timestamp

import play.api.i18n.Lang
import play.api.mvc.Request

import db._
import db.access.{ModelAccess, ModelAssociationAccess, ModelAssociationAccessImpl}
import db.impl.OrePostgresDriver.api._
import db.impl.access.{OrganizationBase, UserBase}
import db.impl.model.common.Named
import db.impl.schema._
import models.project.{Flag, Project, Visibility}
import models.user.role.{DbRole, OrganizationUserRole, ProjectUserRole}
import ore.OreConfig
import ore.permission._
import ore.permission.role.{Role, _}
import ore.permission.scope._
import ore.user.Prompt
import security.pgp.PGPPublicKeyInfo
import security.spauth.{SpongeAuthApi, SpongeUser}
import util.OreMDCCtx
import util.StringUtils._
import util.syntax._

import cats.Parallel
import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
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
case class User(
    id: ObjId[User],
    createdAt: ObjectTimestamp,
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

  private def biggestRoleTpe(roles: Set[Role]): Trust =
    if (roles.isEmpty) Trust.Default
    else roles.map(_.trust).max

  private def globalTrust(implicit service: ModelService) = {
    val q = for {
      ur <- TableQuery[UserGlobalRolesTable] if ur.userId === id.value
      r  <- TableQuery[DbRoleTable] if ur.roleId === r.id
    } yield r.trust

    service.runDBIO(Query(q.max).result.head).map(_.getOrElse(Trust.Default))
  }

  def trustIn[A: HasScope](a: A)(implicit service: ModelService, cs: ContextShift[IO]): IO[Trust] =
    trustIn(a.scope)

  /**
    * Returns this User's highest level of Trust.
    *
    * @return Highest level of trust
    */
  def trustIn(scope: Scope = GlobalScope)(implicit service: ModelService, cs: ContextShift[IO]): IO[Trust] = {
    scope match {
      case GlobalScope => globalTrust
      case ProjectScope(projectId) =>
        val projectRoles = service.runDBIO(Project.roleForTrustQuery((projectId, this.id.value)).result)

        val projectTrust = projectRoles
          .map(biggestRoleTpe)
          .flatMap { userTrust =>
            val projectsTable = TableQuery[ProjectTableMain]
            val orgaTable     = TableQuery[OrganizationTable]
            val memberTable   = TableQuery[OrganizationMembersTable]
            val roleTable     = TableQuery[OrganizationRoleTable]

            val query = for {
              p <- projectsTable if projectId.bind === p.id
              o <- orgaTable if p.userId === o.id
              m <- memberTable if m.organizationId === o.id && m.userId === id.value.bind
              r <- roleTable if id.value.bind === r.userId && r.organizationId === o.id
            } yield r.roleType

            service.runDBIO(query.to[Set].result).map { roleTypes =>
              if (roleTypes.contains(Role.OrganizationAdmin) || roleTypes.contains(Role.OrganizationOwner)) {
                biggestRoleTpe(roleTypes)
              } else {
                userTrust
              }
            }
          }

        Parallel.parMap2(projectTrust, globalTrust)(_ max _)
      case OrganizationScope(organizationId) =>
        Parallel.parMap2(Organization.getTrust(id.value, organizationId), globalTrust)(_ max _)
    }
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
    * Returns true if this User is the currently authenticated user.
    *
    * @return True if currently authenticated user
    */
  def isCurrent(
      implicit request: Request[_],
      service: ModelService,
      auth: SpongeAuthApi,
      mdc: OreMDCCtx
  ): IO[Boolean] = {
    checkNotNull(request, "null request", "")
    UserBase().current
      .semiflatMap { user =>
        if (user == this) IO.pure(true)
        else this.toMaybeOrganization.semiflatMap(_.owner.user).exists(_ == user)
      }
      .exists(identity)
  }

  /**
    * Returns all [[Project]]s owned by this user.
    *
    * @return Projects owned by user
    */
  def projects(implicit service: ModelService): ModelAccess[Project] = service.access(_.userId === id.value)

  /**
    * Returns the Project with the specified name that this User owns.
    *
    * @param name Name of project
    * @return Owned project, if any, None otherwise
    */
  def getProject(name: String)(implicit service: ModelService): OptionT[IO, Project] =
    this.projects.find(equalsIgnoreCase(_.name, name))

  /**
    * Returns a [[ModelAccess]] of [[ProjectUserRole]]s.
    *
    * @return ProjectRoles
    */
  def projectRoles(implicit service: ModelService): ModelAccess[ProjectUserRole] = service.access(_.userId === id.value)

  /**
    * Returns the [[Organization]]s that this User owns.
    *
    * @return Organizations user owns
    */
  def ownedOrganizations(implicit service: ModelService): ModelAccess[Organization] =
    service.access(_.userId === id.value)

  /**
    * Returns the [[Organization]]s that this User belongs to.
    *
    * @return Organizations user belongs to
    */
  def organizations(
      implicit service: ModelService
  ): ModelAssociationAccess[OrganizationMembersTable, User, Organization, IO] = new ModelAssociationAccessImpl

  /**
    * Returns a [[ModelAccess]] of [[OrganizationUserRole]]s.
    *
    * @return OrganizationRoles
    */
  def organizationRoles(implicit service: ModelService): ModelAccess[OrganizationUserRole] =
    service.access(_.userId === id.value)

  /**
    * Converts this User to an [[Organization]].
    *
    * @return Organization
    */
  def toMaybeOrganization(implicit service: ModelService): OptionT[IO, Organization] =
    OrganizationBase().get(this.id.value)

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
    checkNotNull(project, "null project", "")
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
  def flags(implicit service: ModelService): ModelAccess[Flag] = service.access(_.userId === id.value)

  /**
    * Returns true if the User has an unresolved [[Flag]] on the specified
    * [[Project]].
    *
    * @param project Project to check
    * @return True if has pending flag on Project
    */
  def hasUnresolvedFlagFor(project: Project)(implicit service: ModelService): IO[Boolean] = {
    checkNotNull(project, "null project", "")
    this.flags.exists(f => f.projectId === project.id.value && !f.isResolved)
  }

  /**
    * Returns this User's notifications.
    *
    * @return User notifications
    */
  def notifications(implicit service: ModelService): ModelAccess[Notification] = service.access(_.userId === id.value)

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
