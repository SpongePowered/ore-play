package controllers

import java.sql.Timestamp
import java.time.Instant

import javax.inject.Inject
import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.ModelService
import form.OreForms
import models.admin.{Message, Review}
import models.user.{Notification, User}
import ore.permission.ReviewProjects
import ore.permission.role.Lifted
import ore.user.notification.NotificationTypes
import ore.{OreConfig, OreEnv}
import play.api.i18n.{Lang, MessagesApi}
import security.spauth.SingleSignOnConsumer
import util.DataHelper
import views.{html => views}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Controller for handling Review related actions.
  */
final class Reviews @Inject()(data: DataHelper,
                              forms: OreForms,
                              implicit override val bakery: Bakery,
                              implicit override val sso: SingleSignOnConsumer,
                              implicit override val messagesApi: MessagesApi,
                              implicit override val env: OreEnv,
                              implicit override val config: OreConfig,
                              implicit override val service: ModelService)
                              extends OreBaseController {

  def showReviews(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      withProject(author, slug) { implicit project =>
        withVersion(versionString) { implicit version =>
          Ok(views.users.admin.reviews(version.mostRecentReviews))
        }
      }
    }
  }

  def createReview(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      withProject(author, slug) { implicit project =>
        withVersion(versionString) { implicit version =>
          val review = new Review(Some(1), Some(Timestamp.from(Instant.now())), version.id.get, users.current.get.id.get, None, "")
          this.service.insert(review)
          Redirect(routes.Reviews.showReviews(author, slug, versionString))
        }
      }
    }
  }

  def stopReview(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      withProject(author, slug) { implicit project =>
        withVersion(versionString) { version =>
          val review = version.mostRecentUnfinishedReview.get
          review.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim, System.currentTimeMillis(), "stop"))
          review.setEnded(Timestamp.from(Instant.now()))
          Redirect(routes.Reviews.showReviews(author, slug, versionString))
        }
      }
    }
  }

  def approveReview(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      withProject(author, slug) { implicit project =>
        withVersion(versionString) { version =>
          val review = version.mostRecentUnfinishedReview.get
          review.setEnded(Timestamp.from(Instant.now()))

          // send notification that review happened
          val organization = this.organizations.get(project.ownerId)

          if (organization.isDefined) {
            val users: List[User] = organization.get.memberships.members.toList.filter(_.headRole.roleType.trust.level >= Lifted.level).map(_.user)

            if (!users.contains(version.author.get))
              users :+ version.author.get

            users.foreach(user => user.sendNotification(Notification(
              originId = request.user.id.get,
              notificationType = NotificationTypes.ProjectInvite,
              message = messagesApi("notification.project.reviewed", slug, versionString)
            )))
          } else {
            version.author.get.sendNotification(Notification(
              originId = request.user.id.get,
              notificationType = NotificationTypes.ProjectInvite,
              message = messagesApi("notification.project.reviewed", slug, versionString)
            ))
          }

          Redirect(routes.Reviews.showReviews(author, slug, versionString))
        }
      }
    }
  }

  def takeoverReview(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      withProject(author, slug) { implicit project =>
        withVersion(versionString) { version =>
          // Close old review
          val oldreview = version.mostRecentUnfinishedReview.get
          oldreview.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim, System.currentTimeMillis(), "takeover"))
          oldreview.setEnded(Timestamp.from(Instant.now()))
          // Make new one
          val review = new Review(Some(1), Some(Timestamp.from(Instant.now())), version.id.get, users.current.get.id.get, None, "")
          this.service.insert(review)
          Redirect(routes.Reviews.showReviews(author, slug, versionString))
        }
      }
    }
  }

  def editReview(author: String, slug: String, versionString: String, reviewId: Int) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      withProject(author, slug) { implicit project =>
        withVersion(versionString) { version =>
          val review = version.reviewById(reviewId)
          review.get.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim))
          Ok("Review"  + review)
        }
      }
    }
  }

  def addMessage(author: String, slug: String, versionString: String) = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      withProject(author, slug) { implicit project =>
        withVersion(versionString) { version =>
          val recentReview = version.mostRecentUnfinishedReview
          if (recentReview.isDefined) {
            val currentUser = users.current
            if (recentReview.get.userId == currentUser.get.userId) {
              recentReview.get.addMessage(Message(this.forms.ReviewDescription.bindFromRequest.get.trim))
            }
          }
          Ok("Review")
        }
      }
    }
  }
}
