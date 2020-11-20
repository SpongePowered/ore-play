package db.impl.access

import scala.concurrent.duration._
import scala.jdk.DurationConverters._

import ore.OreConfig
import ore.auth.{AuthUser, SpongeAuthApi}
import ore.data.user.notification.NotificationType
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{DbRef, Model, ModelService, ObjId}
import ore.models.organization.Organization
import ore.models.user.role.OrganizationUserRole
import ore.models.user.{Notification, User}
import ore.permission.role.Role
import ore.util.OreMDC
import util.syntax._

import cats.data.NonEmptyList
import cats.syntax.all._
import com.typesafe.scalalogging
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.{IO, Schedule, UIO, ZIO}

trait OrganizationBase {

  /**
    * Creates a new [[Organization]]. This method creates a new user on the
    * forums to represent the Organization.
    *
    * @param name     Organization name
    * @param ownerId  User ID of the organization owner
    * @return         New organization if successful, None otherwise
    */
  def create(
      name: String,
      ownerId: DbRef[User],
      members: Set[OrganizationUserRole]
  )(implicit mdc: OreMDC): ZIO[Clock with Blocking, List[String], Model[Organization]]

  /**
    * Returns an [[Organization]] with the specified name if it exists.
    *
    * @param name Organization name
    * @return     Organization with name if exists, None otherwise
    */
  def withName(name: String): IO[Option[Nothing], Model[Organization]]
}

object OrganizationBase {

  /**
    * Default live implementation of [[OrganizationBase]]
    */
  class OrganizationBaseF(
      implicit val service: ModelService[UIO],
      config: OreConfig,
      auth: SpongeAuthApi
  ) extends OrganizationBase {

    private val Logger    = scalalogging.Logger("Organizations")
    private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

    def create(
        name: String,
        ownerId: DbRef[User],
        members: Set[OrganizationUserRole]
    )(implicit mdc: OreMDC): ZIO[Clock with Blocking, List[String], Model[Organization]] = {
      val logging = ZIO.effectTotal {
        MDCLogger.debug("Creating Organization...")
        MDCLogger.debug("Name     : " + name)
        MDCLogger.debug("Owner ID : " + ownerId)
        MDCLogger.debug("Members  : " + members.size)

        // Create the organization as a User on SpongeAuth. This will reserve the
        // name so that no new users or organizations can create an account with
        // that name. We will give the organization a dummy email for continuity.
        // By default we use "<org>@ore.spongepowered.org".
        MDCLogger.debug("Creating on SpongeAuth...")
      }

      // Replace all invalid characters to not throw invalid email error when trying to create org with invalid username
      val dummyEmail   = name.replaceAll("[^a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]", "") + '@' + config.ore.orgs.dummyEmailDomain
      val spongeResult = logging *> auth.createDummyUser(name, dummyEmail)

      def waitTilUserExists(authUser: AuthUser) = {
        val hasUser = ModelView.now(User).get(authUser.id).isDefined

        //Do a quick check first if the user is already present
        hasUser.flatMap {
          case true => ZIO.succeed(true)
          case false =>
            val untilHasUser = Schedule.recurUntilM[Any, Any](_ => hasUser)
            val sleepWhileWaiting =
              Schedule.exponential(50.millis.toJava).fold(0.seconds)(_ + _.toScala).untilOutput(_ > 20.seconds)

            ZIO.unit.repeat(untilHasUser && sleepWhileWaiting) *> hasUser
        }
      }

      // Check for error
      spongeResult
        .flatMapError(err => ZIO.effectTotal(MDCLogger.debug("<FAILURE> " + err)).as(err))
        .flatMap { spongeUser =>
          waitTilUserExists(spongeUser).flatMap {
            case true => ZIO.succeed(spongeUser)
            // Exit early if we never got the user
            case false => ZIO.fail(List("Timed out while waiting for SSO sync"))
          }
        }
        .flatMap { spongeUser =>
          MDCLogger.debug("<SUCCESS> " + spongeUser)
          // Next we will create the Organization on Ore itself. This contains a
          // reference to the Sponge user ID, the organization's username and a
          // reference to the User owner of the organization.
          MDCLogger.info("Creating on Ore...")
          service.insert(
            Organization(
              id = ObjId(spongeUser.id),
              userId = spongeUser.id,
              ownerId = ownerId,
              name = spongeUser.username
            )
          )
        }
        .flatMap { org =>
          // Every organization model has a regular User companion. Organizations
          // are just normal users with additional information. Adding the
          // Organization global role signifies that this User is an Organization
          // and should be treated as such.
          for {
            userOrg <- org.toUser.value.someOrElseM(ZIO.die(new IllegalStateException("User not created")))
            _       <- userOrg.globalRoles[UIO].addAssoc(Role.Organization.toDbRole.id.value)
            _ <- // Add the owner
            org.memberships.addRole(org)(
              ownerId,
              OrganizationUserRole(
                userId = ownerId,
                organizationId = org.id,
                role = Role.OrganizationOwner,
                isAccepted = true
              )
            )
            _ <- {
              // Invite the User members that the owner selected during creation.
              MDCLogger.debug("Inviting members...")

              members.toVector.parTraverse { role =>
                // TODO remove role.user db access we really only need the userid we already have for notifications
                org.memberships.addRole(org)(role.userId, role.copy(organizationId = org.id)).flatMap { _ =>
                  service.insert(
                    Notification(
                      userId = role.userId,
                      originId = Some(org.id),
                      notificationType = NotificationType.OrganizationInvite,
                      messageArgs = NonEmptyList.of("notification.organization.invite", role.role.title, userOrg.name)
                    )
                  )
                }
              }
            }
          } yield {
            MDCLogger.debug("<SUCCESS> " + org)
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
    def withName(name: String): IO[Option[Nothing], Model[Organization]] =
      ModelView.now(Organization).find(_.name.toLowerCase === name.toLowerCase).value.get
  }
}
