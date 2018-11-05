package models.user

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.Lang
import play.api.mvc.Request

import db.access.{ModelAccess, ModelAssociationAccess}
import db.impl.OrePostgresDriver.api._
import db.impl.access.{OrganizationBase, UserBase}
import db.impl.schema._
import db._
import models.project.{Flag, Project, Visibility}
import models.user.role.{DbRole, OrganizationUserRole, ProjectUserRole}
import ore.OreConfig
import ore.permission._
import ore.permission.role.{Role, _}
import ore.permission.scope._
import ore.user.{Prompt, UserOwned}
import security.pgp.PGPPublicKeyInfo
import security.spauth.{SpongeAuthApi, SpongeUser}
import util.StringUtils._

import cats.data.OptionT
import cats.instances.future._
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
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    fullName: Option[String] = None,
    name: String = null,
    email: Option[String] = None,
    tagline: Option[String] = None,
    joinDate: Option[Timestamp] = None,
    readPrompts: List[Prompt] = List(),
    pgpPubKey: Option[String] = None,
    lastPgpPubKeyUpdate: Option[Timestamp] = None,
    isLocked: Boolean = false,
    lang: Option[Lang] = None
) extends Model
    with UserOwned
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
  def pgpPubKeyInfo: Option[PGPPublicKeyInfo] = this.pgpPubKey.flatMap(PGPPublicKeyInfo.decode)

  /**
    * Returns true if this user's PGP Public Key is ready for use.
    *
    * @return True if key is ready for use
    */
  def isPgpPubKeyReady(implicit config: OreConfig, service: ModelService): Boolean =
    this.pgpPubKey.isDefined && this.lastPgpPubKeyUpdate.forall { lastUpdate =>
      val cooldown = config.security.get[Long]("keyChangeCooldown")
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
  def globalRoles(implicit service: ModelService): ModelAssociationAccess[UserGlobalRolesTable, User, DbRole] =
    service.associationAccess[UserGlobalRolesTable, User, DbRole](this)

  /**
    * Returns the highest level [[DonorRole]] this User has.
    *
    * @return Highest level donor type
    */
  def donorType(implicit service: ModelService, ec: ExecutionContext): OptionT[Future, DonorRole] =
    OptionT(
      this.globalRoles.filter(_.rank.?.isDefined).map { seq =>
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

  private def globalTrust(implicit service: ModelService, ec: ExecutionContext) = {
    val q = for {
      ur <- TableQuery[UserGlobalRolesTable] if ur.userId === id.value
      r  <- TableQuery[DbRoleTable] if ur.roleId === r.id
    } yield r.trust

    service.doAction(Query(q.max).result.head).map(_.getOrElse(Trust.Default))
  }

  def trustIn[A: HasScope](a: A)(implicit ec: ExecutionContext, service: ModelService): Future[Trust] =
    trustIn(Scope.getFor(a))

  /**
    * Returns this User's highest level of Trust.
    *
    * @return Highest level of trust
    */
  def trustIn(scope: Scope = GlobalScope)(implicit ec: ExecutionContext, service: ModelService): Future[Trust] =
    Defined {
      scope match {
        case GlobalScope => globalTrust
        case ProjectScope(projectId) =>
          val projectRoles = service.doAction(Project.roleForTrustQuery((projectId, this.id.value)).result)

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
                m <- memberTable if m.organizationId === o.id && m.userId === this.userId.bind
                r <- roleTable if this.userId.bind === r.userId && r.organizationId === o.id
              } yield r.roleType

              service.doAction(query.to[Set].result).map { roleTypes =>
                if (roleTypes.contains(Role.OrganizationAdmin) || roleTypes.contains(Role.OrganizationOwner)) {
                  biggestRoleTpe(roleTypes)
                } else {
                  userTrust
                }
              }
            }

          projectTrust.map2(globalTrust)(_ max _)
        case OrganizationScope(organizationId) =>
          Organization.getTrust(id.value, organizationId).map2(globalTrust)(_ max _)
        case _ =>
          throw new RuntimeException("unknown scope: " + scope)
      }
    }

  /**
    * Returns the Projects that this User has starred.
    *
    * @param page Page of user stars
    * @return Projects user has starred
    */
  def starred(
      page: Int = -1
  )(implicit config: OreConfig, service: ModelService): Future[Seq[Project]] = Defined {
    val starsPerPage = config.users.get[Int]("stars-per-page")
    val limit        = if (page < 1) -1 else starsPerPage
    val offset       = (page - 1) * starsPerPage
    val filter       = Visibility.isPublicFilter[Project]
    service
      .associationAccess[ProjectStarsTable, User, Project](this)
      .sorted(ordering = _.name, filter = filter.fn, limit = limit, offset = offset)
  }

  /**
    * Returns true if this User is the currently authenticated user.
    *
    * @return True if currently authenticated user
    */
  def isCurrent(
      implicit request: Request[_],
      ec: ExecutionContext,
      service: ModelService,
      auth: SpongeAuthApi
  ): Future[Boolean] = {
    checkNotNull(request, "null request", "")
    UserBase().current
      .semiflatMap { user =>
        if (user == this) Future.successful(true)
        else this.toMaybeOrganization.semiflatMap(_.owner.user).exists(_ == user)
      }
      .exists(identity)
  }

  /**
    * Copy this User with the information SpongeUser provides.
    *
    * @param user Sponge User
    */
  def copyFromSponge(user: SpongeUser): User = {
    copy(
      id = ObjectId(user.id),
      name = user.username,
      email = Some(user.email),
      lang = user.lang
    )
  }

  /**
    * Returns all [[Project]]s owned by this user.
    *
    * @return Projects owned by user
    */
  def projects(implicit service: ModelService): ModelAccess[Project] =
    service.access[Project](ModelFilter(_.userId === id.value))

  /**
    * Returns the Project with the specified name that this User owns.
    *
    * @param name Name of project
    * @return Owned project, if any, None otherwise
    */
  def getProject(name: String)(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, Project] =
    this.projects.find(equalsIgnoreCase(_.name, name))

  /**
    * Returns a [[ModelAccess]] of [[ProjectUserRole]]s.
    *
    * @return ProjectRoles
    */
  def projectRoles(implicit service: ModelService): ModelAccess[ProjectUserRole] =
    service.access[ProjectUserRole](ModelFilter(_.userId === id.value))

  /**
    * Returns the [[Organization]]s that this User owns.
    *
    * @return Organizations user owns
    */
  def ownedOrganizations(implicit service: ModelService): ModelAccess[Organization] =
    service.access[Organization](ModelFilter(_.userId === id.value))

  /**
    * Returns the [[Organization]]s that this User belongs to.
    *
    * @return Organizations user belongs to
    */
  def organizations(
      implicit service: ModelService
  ): ModelAssociationAccess[OrganizationMembersTable, User, Organization] =
    service.associationAccess[OrganizationMembersTable, User, Organization](this)

  /**
    * Returns a [[ModelAccess]] of [[OrganizationUserRole]]s.
    *
    * @return OrganizationRoles
    */
  def organizationRoles(implicit service: ModelService): ModelAccess[OrganizationUserRole] =
    service.access[OrganizationUserRole](ModelFilter(_.userId === id.value))

  /**
    * Converts this User to an [[Organization]].
    *
    * @return Organization
    */
  def toMaybeOrganization(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, Organization] =
    Defined {
      OrganizationBase().get(this.id.value)
    }

  /**
    * Returns the [[Project]]s that this User is watching.
    *
    * @return Projects user is watching
    */
  def watching(implicit service: ModelService): ModelAssociationAccess[ProjectWatchersTable, User, Project] =
    service.associationAccess[ProjectWatchersTable, User, Project](this)

  /**
    * Sets the "watching" status on the specified project.
    *
    * @param project  Project to update status on
    * @param watching True if watching
    */
  def setWatching(
      project: Project,
      watching: Boolean
  )(implicit ec: ExecutionContext, service: ModelService): Future[Unit] = {
    checkNotNull(project, "null project", "")
    checkArgument(project.isDefined, "undefined project", "")
    val contains = this.watching.contains(project)
    contains.flatMap {
      case true  => if (!watching) this.watching.remove(project).void else Future.unit
      case false => if (watching) this.watching.add(project).void else Future.unit
    }
  }

  /**
    * Returns the [[Flag]]s submitted by this User.
    *
    * @return Flags submitted by user
    */
  def flags(implicit service: ModelService): ModelAccess[Flag] =
    service.access[Flag](ModelFilter(_.userId === id.value))

  /**
    * Returns true if the User has an unresolved [[Flag]] on the specified
    * [[Project]].
    *
    * @param project Project to check
    * @return True if has pending flag on Project
    */
  def hasUnresolvedFlagFor(project: Project)(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] = {
    checkNotNull(project, "null project", "")
    checkArgument(project.isDefined, "undefined project", "")
    this.flags.exists(f => f.projectId === project.id.value && !f.isResolved)
  }

  /**
    * Returns this User's notifications.
    *
    * @return User notifications
    */
  def notifications(implicit service: ModelService): ModelAccess[Notification] =
    service.access[Notification](ModelFilter(_.userId === id.value))

  /**
    * Sends a [[Notification]] to this user.
    *
    * @param notification Notification to send
    * @return Future result
    */
  def sendNotification(
      notification: Notification
  )(implicit ec: ExecutionContext, service: ModelService, config: OreConfig): Future[Notification] = {
    checkNotNull(notification, "null notification", "")
    config.debug("Sending notification: " + notification, -1)
    service.access[Notification]().add(notification.copy(userId = this.id.value))
  }

  /**
    * Marks a [[Prompt]] as read by this User.
    *
    * @param prompt Prompt to mark as read
    */
  def markPromptAsRead(prompt: Prompt)(implicit ec: ExecutionContext, service: ModelService): Future[User] = {
    checkNotNull(prompt, "null prompt", "")
    service.update(
      copy(
        readPrompts = readPrompts :+ prompt
      )
    )
  }

  override def userId: ObjectReference                                = this.id.value
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): User = this.copy(createdAt = theTime)
}

object User {

  implicit val query: ModelQuery[User] =
    ModelQuery.from[User](TableQuery[UserTable])

  implicit val assocStarredQuery: AssociationQuery[ProjectStarsTable, User, Project] =
    AssociationQuery.from(TableQuery[ProjectStarsTable])(_.userId, _.projectId)

  implicit val assocOrgMembersQuery: AssociationQuery[OrganizationMembersTable, User, Organization] =
    AssociationQuery.from(TableQuery[OrganizationMembersTable])(_.userId, _.organizationId)

  implicit val assocWatchingQuery: AssociationQuery[ProjectWatchersTable, User, Project] =
    AssociationQuery.from(TableQuery[ProjectWatchersTable])(_.userId, _.projectId)

  implicit val assocRolesQuery: AssociationQuery[UserGlobalRolesTable, User, DbRole] =
    AssociationQuery.from(TableQuery[UserGlobalRolesTable])(_.userId, _.roleId)

  def avatarUrl(name: String)(implicit config: OreConfig): String =
    config.security.get[String]("api.avatarUrl").format(name)

  /**
    * Create a new [[User]] from the specified [[SpongeUser]].
    *
    * @param toConvert User to convert
    * @return Ore user
    */
  def fromSponge(toConvert: SpongeUser): User =
    User().copyFromSponge(toConvert)
}
