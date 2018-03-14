package db.impl.access

import db.{ModelBase, ModelService}
import discourse.OreDiscourseApi
import models.user.role.OrganizationRole
import models.user.{Notification, Organization, User}
import ore.OreConfig
import ore.permission.role.RoleTypes
import ore.user.notification.NotificationTypes
import play.api.i18n.{Lang, MessagesApi}
import security.spauth.SpongeAuthApi
import util.StringUtils

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
  def create(name: String, ownerId: Int, members: Set[OrganizationRole]): Either[String, Organization] = {
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
    if (spongeResult.isLeft) {
      val error = spongeResult.left.get
      Logger.info("<FAILURE> " + error)
      return Left(error)
    }
    val spongeUser = spongeResult.right.get
    Logger.info("<SUCCESS> " + spongeUser)

    // Next we will create the Organization on Ore itself. This contains a
    // reference to the Sponge user ID, the organization's username and a
    // reference to the User owner of the organization.
    Logger.info("Creating on Ore...")
    val org = this.add(Organization(
      id = Some(spongeUser.id),
      username = name,
      ownerId = ownerId))

    // Every organization model has a regular User companion. Organizations
    // are just normal users with additional information. Adding the
    // Organization global role signifies that this User is an Organization
    // and should be treated as such.
    val userOrg = org.toUser.pullForumData().pullSpongeData()
    userOrg.globalRoles = userOrg.globalRoles + RoleTypes.Organization

    // Add the owner
    val owner: User = org.owner
    val dossier = org.memberships
    dossier.addRole(OrganizationRole(
      userId = owner.id.get,
      organizationId = org.id.get,
      _roleType = RoleTypes.OrganizationOwner,
      _isAccepted = true))

    // Invite the User members that the owner selected during creation.
    Logger.info("Inviting members...")
    for (role <- members) {
      dossier.addRole(role.copy(organizationId = org.id.get))
      role.user.sendNotification(Notification(
        originId = org.id.get,
        notificationType = NotificationTypes.OrganizationInvite,
        message = this.messages("notification.organization.invite", role.roleType.title, org.username)
      ))
    }

    val result = userOrg.toOrganization
    Logger.info("<SUCCESS> " + result)
    Right(userOrg.toOrganization)
  }

  /**
    * Returns an [[Organization]] with the specified name if it exists.
    *
    * @param name Organization name
    * @return     Organization with name if exists, None otherwise
    */
  def withName(name: String): Option[Organization] = this.find(StringUtils.equalsIgnoreCase(_.name, name))

}
