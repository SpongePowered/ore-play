package controllers

import java.sql.Timestamp
import java.time.Instant
import javax.inject.Inject

import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl._
import form.OreForms
import models.admin.{Message, Review}
import models.project.{Project, Version}
import models.user.{Notification, User}
import models.viewhelper.HeaderData
import ore.permission.ReviewProjects
import ore.permission.role.Lifted
import ore.permission.role.RoleTypes.RoleType
import ore.user.notification.NotificationTypes
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.{I18nSupport, MessagesApi}
import security.spauth.SingleSignOnConsumer
import slick.lifted.{Rep, TableQuery}
import util.DataHelper
import views.{html => views}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Controller for handling Review related actions.
  */
final class Reviews @Inject()(data: DataHelper,
                              forms: OreForms,
                              implicit override val bakery: Bakery,
                              implicit override val sso: SingleSignOnConsumer,
                              implicit override val messagesApi: MessagesApi,
                              implicit override val env: OreEnv,
                              implicit override val cache: AsyncCacheApi,
                              implicit override val config: OreConfig,
                              implicit override val service: ModelService)
                              extends OreBaseController {

  def showReviews(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects) andThen ProjectAction(author, slug)).async { request =>
      implicit val r = request.request
      implicit val data = request.data
      implicit val p = data.project

      withVersionAsync(versionString) { implicit version =>
          version.mostRecentReviews.flatMap { reviews =>
            val unfinished = reviews.filter(r => r.createdAt.isDefined && r.endedAt.isEmpty).sorted(Review.ordering2).headOption
            Future.sequence(reviews.map { r =>
              r.userBase.get(r.userId).map { u =>
                (r, u.map(_.name))
              }
            }) map { rv =>
              //implicit val m = messagesApi.preferred(request)
              Ok(views.users.admin.reviews(unfinished, rv))
            }
          }
        }
      }
  }

  def createReview(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) async { implicit request =>
      withProjectAsync(author, slug) { implicit project =>
        withVersionAsync(versionString) { implicit version =>
          for {
            currentUser <- users.current
          } yield {
            val review = new Review(Some(1), Some(Timestamp.from(Instant.now())), version.id.get, currentUser.get.id.get, None, "")
            this.service.insert(review)
            Redirect(routes.Reviews.showReviews(author, slug, versionString))
          }
        }
      }
    }
  }

  def stopReview(author: String, slug: String, versionString: String) = {
    Authenticated andThen PermissionAction[AuthRequest](ReviewProjects) async { implicit request =>
      withProjectAsync(author, slug) { implicit project =>
        withVersionAsync(versionString) { version =>
          version.mostRecentUnfinishedReview.map {
            case None => NotFound
            case Some(review) =>
              review.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim, System.currentTimeMillis(), "stop"))
              review.setEnded(Timestamp.from(Instant.now()))
              Redirect(routes.Reviews.showReviews(author, slug, versionString))
          }
        }
      }
    }
  }

  def approveReview(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) async { implicit request =>
      withProjectAsync(author, slug) { implicit project =>
        withVersionAsync(versionString) { version =>
          version.mostRecentUnfinishedReview.flatMap {
            case None => Future.successful(NotFound)
            case Some(review) =>
              for {
                _ <- review.setEnded(Timestamp.from(Instant.now()))
                // send notification that review happened
                _ <- sendReviewNotification(project, version, request.user)
              } yield {
                Redirect(routes.Reviews.showReviews(author, slug, versionString))
              }
          }
        }
      }
    }
  }

  private def queryNotificationUsers(projectId: Rep[Int], userId: Rep[Int], noRole: Rep[Option[RoleType]])
    : Query[(Rep[Int], Rep[Option[RoleType]]), (Int, Option[RoleType]), Seq] = {

    val orgTable = TableQuery[OrganizationTable]
    val orgMemberTable = TableQuery[OrganizationMembersTable]
    val orgRolesTable = TableQuery[OrganizationRoleTable]
    val userTable = TableQuery[UserTable]

    // Query Orga Members
    val q1: Query[(Rep[Int], Rep[Option[RoleType]]), (Int, Option[RoleType]), scala.Seq]  = for {
      org <- orgTable if org.id === projectId
      members <- orgMemberTable if org.id === members.organizationId
      roles <- orgRolesTable if members.userId === roles.userId // TODO roletype lvl in database?
      users <- userTable if members.userId === users.id
    } yield {
      (users.id, roles.roleType.?)
    }

    // Query version author
    val q2: Query[(Rep[Int], Rep[Option[RoleType]]), (Int, Option[RoleType]), scala.Seq]  = for {
      user <- userTable if user.id === userId
    } yield {
      (user.id, noRole)
    }

    q1 ++ q2 // Union
  }

  private lazy val notificationUsersQuery = Compiled(queryNotificationUsers _)

  private def sendReviewNotification(project: Project, version: Version, requestUser: User): Future[_] = {

    val users: Future[Seq[Int]] = this.service.DB.db.run(notificationUsersQuery(project.id.get, version.authorId, None).result).map { list =>
      list.filter {
        case (_, Some(level)) => level.trust.level >= Lifted.level
        case (_, None) => true
      }.map(_._1)
    }

    val notificationTable = TableQuery[NotificationTable]

    users.map { list =>
      list.map { id =>
        Notification(
          userId = id,
          originId = requestUser.id.get,
          notificationType = NotificationTypes.VersionReviewed,
          message = messagesApi("notification.project.reviewed", project.slug, version.versionString)
        )
      }
    } map (notificationTable ++= _) flatMap (service.DB.db.run(_)) // Batch insert all notifications
  }

  def takeoverReview(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      withProjectAsync(author, slug) { implicit project =>
        withVersionAsync(versionString) { version =>
          // Close old review
          val closeOldReview = version.mostRecentUnfinishedReview.flatMap {
            case None => Future.successful()
            case Some(oldreview) =>
              for {
                _ <- oldreview.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim, System.currentTimeMillis(), "takeover"))
                _ <- oldreview.setEnded(Timestamp.from(Instant.now()))
              } yield {}
          }

          // Then make new one
          for {
            _ <- closeOldReview
            currentUser <- users.current
            _ <- this.service.insert(Review(Some(1), Some(Timestamp.from(Instant.now())), version.id.get, currentUser.get.id.get, None, ""))
          } yield {
            Redirect(routes.Reviews.showReviews(author, slug, versionString))
          }
        }
      }
    }
  }

  def editReview(author: String, slug: String, versionString: String, reviewId: Int) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      withProjectAsync(author, slug) { implicit project =>
          withVersionAsync(versionString) { version =>
            version.reviewById(reviewId).map {
              case None => NotFound
              case Some(review) =>
                review.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim))
                Ok("Review" + review)
            }
        }
      }
    }
  }

  def addMessage(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      withProjectAsync(author, slug) { implicit project =>
        withVersionAsync(versionString) { version =>
          version.mostRecentUnfinishedReview.flatMap {
            case None => Future.successful(Ok("Review"))
            case Some(recentReview) =>
              users.current.flatMap {
                case Some(currentUser) =>
                  if (recentReview.userId == currentUser.userId) {
                    recentReview.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim))
                  } else Future.successful()
              }.map( _ => Ok("Review"))
          }
        }
      }
    }
  }
}
