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
import util.instances.future._
import scala.concurrent.{ExecutionContext, Future}

import util.functional.{EitherT, OptionT}

class OrganizationBase(override val service: ModelService,
                       forums: OreDiscourseApi,
                       auth: SpongeAuthApi,
                       config: OreConfig,
                       messages: MessagesApi,
                       implicit val users: UserBase)
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
  def create(name: String, ownerId: Int, members: Set[OrganizationRole])(implicit cache: AsyncCacheApi, ec: ExecutionContext): EitherT[Future, String, Organization] = {
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
    spongeResult.leftMap { err =>
      Logger.info("<FAILURE> " + err)
      err
    }.semiFlatMap { spongeUser =>
      Logger.info("<SUCCESS> " + spongeUser)
      // Next we will create the Organization on Ore itself. This contains a
      // reference to the Sponge user ID, the organization's username and a
      // reference to the User owner of the organization.
      Logger.info("Creating on Ore...")
      this.add(Organization(id = Some(spongeUser.id), username = name, _ownerId = ownerId))
    }.semiFlatMap { org =>
      // Every organization model has a regular User companion. Organizations
      // are just normal users with additional information. Adding the
      // Organization global role signifies that this User is an Organization
      // and should be treated as such.
      for {
        userOrg <- org.toUser.getOrElse(throw new IllegalStateException("User not created"))
        _ <- userOrg.pullForumData()
        _ <- userOrg.pullSpongeData()
        _ = userOrg.setGlobalRoles(userOrg.globalRoles + RoleTypes.Organization)
        _ <- // Add the owner
          org.memberships.addRole(OrganizationRole(
            userId = ownerId,
            organizationId = org.id.get,
            _roleType = RoleTypes.OrganizationOwner,
            _isAccepted = true))
        _ <- {
          // Invite the User members that the owner selected during creation.
          Logger.info("Inviting members...")

          Future.sequence(members.map { role =>
            // TODO remove role.user db access we really only need the userid we already have for notifications
            org.memberships.addRole(role.copy(organizationId = org.id.get)).flatMap(_ => role.user).flatMap { user =>
              user.sendNotification(Notification(
                originId = org.id.get,
                notificationType = NotificationTypes.OrganizationInvite,
                messageArgs = List("notification.organization.invite", role.roleType.title, org.username)
              ))
            }
          })
        }
      } yield {
        Logger.info("<SUCCESS> " + org)
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
