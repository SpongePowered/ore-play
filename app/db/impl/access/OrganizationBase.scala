package db.impl.access

import db.{ModelBase, ModelService}
import discourse.OreDiscourseApi
import models.user.role.OrganizationRole
import models.user.{Notification, Organization}
import ore.OreConfig
import ore.permission.role.RoleTypes
import ore.user.notification.NotificationTypes
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, MessagesApi}
import security.spauth.SpongeAuthApi
import util.StringUtils

import scala.concurrent.{ExecutionContext, Future}

class OrganizationBase(override val service: ModelService,
                       forums: OreDiscourseApi,
                       auth: SpongeAuthApi,
                       config: OreConfig,
                       messages: MessagesApi,
                       implicit val users: UserBase)
                       extends ModelBase[Organization] {

  override val modelClass = classOf[Organization]
  implicit val lang = Lang.defaultLang

  val Logger = play.api.Logger("Organizations")

  /**
    * Creates a new [[Organization]]. This method creates a new user on the
    * forums to represent the Organization.
    *
    * @param name     Organization name
    * @param ownerId  User ID of the organization owner
    * @return         New organization if successful, None otherwise
    */
  def create(name: String, ownerId: Int, members: Set[OrganizationRole])(implicit cache: AsyncCacheApi, ec: ExecutionContext): Future[Either[String, Organization]] = {
    Logger.info("Creating Organization...")
    Logger.info("Name     : " + name)
    Logger.info("Owner ID : " + ownerId)
    Logger.info("Members  : " + members.size)

    // Create the organization as a User on SpongeAuth. This will reserve the
    // name so that no new users or organizations can create an account with
    // that name. We will give the organization a dummy email for continuity.
    // By default we use "<org>@ore.spongepowered.org".
    Logger.info("Creating on SpongeAuth...")
    val dummyEmail = name + '@' + this.config.orgs.get[String]("dummyEmailDomain")
    val spongeResult = this.auth.createDummyUser(name, dummyEmail, verified = true)
    // Check for error
    spongeResult flatMap {
      case Left(err) =>
        Logger.info("<FAILURE> " + err)
        Future.successful(Left(err))
      case Right(spongeUser) =>
        Logger.info("<SUCCESS> " + spongeUser)
        // Next we will create the Organization on Ore itself. This contains a
        // reference to the Sponge user ID, the organization's username and a
        // reference to the User owner of the organization.
        Logger.info("Creating on Ore...")
        this.add(Organization(id = Some(spongeUser.id), username = name, _ownerId = ownerId)).map(Right(_))
    } flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(org) =>
        // Every organization model has a regular User companion. Organizations
        // are just normal users with additional information. Adding the
        // Organization global role signifies that this User is an Organization
        // and should be treated as such.
        org.toUser.map {
          case None => throw new IllegalStateException("User not created")
          case Some(userOrg) => userOrg.pullForumData().flatMap(_.pullSpongeData())
            userOrg.setGlobalRoles(userOrg.globalRoles + RoleTypes.Organization)
            userOrg
        } flatMap { owner =>
          // Add the owner
          org.memberships.addRole(OrganizationRole(
            userId = owner.id.get,
            organizationId = org.id.get,
            _roleType = RoleTypes.OrganizationOwner,
            _isAccepted = true))
        } flatMap { _ =>
          // Invite the User members that the owner selected during creation.
          Logger.info("Inviting members...")

          Future.sequence(members.map { role =>
            // TODO remove role.user db access we really only need the userid we already have for notifications
            org.memberships.addRole(role.copy(organizationId = org.id.get)).flatMap(_ => role.user).flatMap { user =>
              user.sendNotification(Notification(
                originId = org.id.get,
                notificationType = NotificationTypes.OrganizationInvite,
                message = this.messages("notification.organization.invite", role.roleType.title, org.username)
              ))
            }
          }
          )
        } map { _ =>
          Logger.info("<SUCCESS> " + org)
          Right(org)
        }
    }
  }

  /**
    * Returns an [[Organization]] with the specified name if it exists.
    *
    * @param name Organization name
    * @return     Organization with name if exists, None otherwise
    */
  def withName(name: String): Future[Option[Organization]] = this.find(StringUtils.equalsIgnoreCase(_.name, name))

}
