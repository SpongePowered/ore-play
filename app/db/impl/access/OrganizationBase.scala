package db.impl.access

import db.{ModelBase, ModelService, ObjectId, ObjectReference}
import models.user.role.OrganizationRole
import models.user.{Notification, Organization}
import ore.OreConfig
import ore.permission.role.RoleType
import ore.user.notification.NotificationTypes
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, MessagesApi}
import security.spauth.SpongeAuthApi
import util.StringUtils
import cats.instances.future._
import scala.concurrent.{ExecutionContext, Future}

import cats.data.{EitherT, NonEmptyList, OptionT}

class OrganizationBase(implicit val service: ModelService,
                       config: OreConfig,
                       messages: MessagesApi)
                       extends ModelBase[Organization] {

  override val modelClass: Class[Organization] = classOf[Organization]

  val Logger = play.api.Logger("Organizations")

  /**
    * Creates a new [[Organization]]. This method creates a new user on the
    * forums to represent the Organization.
    *
    * @param name     Organization name
    * @param ownerId  User ID of the organization owner
    * @return         New organization if successful, None otherwise
    */
  def create(name: String, ownerId: ObjectReference, members: Set[OrganizationRole])(implicit cache: AsyncCacheApi, ec: ExecutionContext, auth: SpongeAuthApi): EitherT[Future, String, Organization] = {
    Logger.debug("Creating Organization...")
    Logger.debug("Name     : " + name)
    Logger.debug("Owner ID : " + ownerId)
    Logger.debug("Members  : " + members.size)

    // Create the organization as a User on SpongeAuth. This will reserve the
    // name so that no new users or organizations can create an account with
    // that name. We will give the organization a dummy email for continuity.
    // By default we use "<org>@ore.spongepowered.org".
    Logger.debug("Creating on SpongeAuth...")
    val dummyEmail = name + '@' + this.config.orgs.get[String]("dummyEmailDomain")
    val spongeResult = auth.createDummyUser(name, dummyEmail, verified = true)

    // Check for error
    spongeResult.leftMap { err =>
      Logger.debug("<FAILURE> " + err)
      err
    }.semiflatMap { spongeUser =>
      Logger.debug("<SUCCESS> " + spongeUser)
      // Next we will create the Organization on Ore itself. This contains a
      // reference to the Sponge user ID, the organization's username and a
      // reference to the User owner of the organization.
      Logger.info("Creating on Ore...")
      this.add(Organization(id = ObjectId(spongeUser.id), username = name, ownerId = ownerId))
    }.semiflatMap { org =>
      // Every organization model has a regular User companion. Organizations
      // are just normal users with additional information. Adding the
      // Organization global role signifies that this User is an Organization
      // and should be treated as such.
      for {
        userOrg <- org.toUser.getOrElse(throw new IllegalStateException("User not created"))
        _ <- service.update(userOrg.copy(globalRoles = RoleType.Organization :: userOrg.globalRoles))
        _ <- // Add the owner
          org.memberships.addRole(OrganizationRole(
            userId = ownerId,
            organizationId = org.id.value,
            roleType = RoleType.OrganizationOwner,
            isAccepted = true))
        _ <- {
          // Invite the User members that the owner selected during creation.
          Logger.debug("Inviting members...")

          Future.sequence(members.map { role =>
            // TODO remove role.user db access we really only need the userid we already have for notifications
            org.memberships.addRole(role.copy(organizationId = org.id.value)).flatMap(_ => role.user).flatMap { user =>
              user.sendNotification(Notification(
                userId = user.id.value,
                originId = org.id.value,
                notificationType = NotificationTypes.OrganizationInvite,
                messageArgs = NonEmptyList.of("notification.organization.invite", role.roleType.title, org.username)
              ))
            }
          })
        }
      } yield {
        Logger.debug("<SUCCESS> " + org)
        org
      }
    }
  }

  /**
    * Returns an [[Organization]] with the specified name if it exists.
    *
    * @param name Organization name
    * @return     Organization with name if exists, None otherwise
    */
  def withName(name: String)(implicit ec: ExecutionContext): OptionT[Future, Organization] = this.find(StringUtils.equalsIgnoreCase(_.name, name))

}
object OrganizationBase {
  def apply()(implicit organizationBase: OrganizationBase): OrganizationBase = organizationBase

  implicit def fromService(implicit service: ModelService): OrganizationBase = service.getModelBase(classOf[OrganizationBase])
}
