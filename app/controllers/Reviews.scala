package controllers

import java.sql.Timestamp
import java.time.Instant

import controllers.sugar.{Bakery, Requests}
import controllers.sugar.Requests.AuthRequest
import db.{ModelService, ObjectId, ObjectTimestamp}
import db.impl.OrePostgresDriver.api._
import db.impl._
import form.OreForms
import javax.inject.Inject
import models.admin.{Message, Review}
import models.project.{Project, Version}
import models.user.{LoggedAction, Notification, User, UserActionLogger}
import ore.permission.ReviewProjects
import ore.permission.role.Lifted
import ore.permission.role.RoleType
import ore.user.notification.NotificationTypes
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import security.spauth.SingleSignOnConsumer
import slick.lifted.{Rep, TableQuery}
import util.DataHelper
import views.{html => views}
import util.instances.future._
import util.syntax._
import util.functional.EitherT

import scala.concurrent.{ExecutionContext, Future}

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
                              implicit override val service: ModelService)(implicit val ec: ExecutionContext)
                              extends OreBaseController {

  def showReviews(author: String, slug: String, versionString: String): Action[AnyContent] =
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects) andThen ProjectAction(author, slug)).async { request =>
      implicit val r: Requests.OreRequest[AnyContent] = request.request
      implicit val p: Project = request.data.project

      val res = for {
        version <- getVersion(p, versionString)
        reviews <- EitherT.right[Result](version.mostRecentReviews)
        rv <- EitherT.right[Result](
          Future.traverse(reviews)(r => r.userBase.get(r.userId).map(_.name).value.tupleLeft(r))
        )
      } yield {
        val unfinished = reviews.filter(_.endedAt.isEmpty).sorted(Review.ordering2).headOption
        implicit val v: Version = version
        Ok(views.users.admin.reviews(unfinished, rv))
      }

      res.merge
  }

  def createReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) async { implicit request =>
      getProjectVersion(author, slug, versionString).map { version =>
        val review = new Review(ObjectId.Uninitialized, ObjectTimestamp(Timestamp.from(Instant.now())), version.id.value, request.user.id.value, None, "")
        this.service.insert(review)
        Redirect(routes.Reviews.showReviews(author, slug, versionString))
      }.merge
    }
  }

  def reopenReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) async { implicit request =>
      val res = for {
        version <- getProjectVersion(author, slug, versionString)
        review <- EitherT.fromOptionF(version.mostRecentReviews.map(_.headOption), notFound)
      } yield {
        version.setReviewed(false)
        version.setApprovedAt(null)
        version.setReviewerId(-1)
        review.setEnded(None)
        review.addMessage(Message("Reopened the review", System.currentTimeMillis(), "start"))
        Redirect(routes.Reviews.showReviews(author, slug, versionString))
      }

      res.merge
    }
  }

  def stopReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated andThen PermissionAction[AuthRequest](ReviewProjects) async { implicit request =>
      val res = for {
        version <- getProjectVersion(author, slug, versionString)
        review <- version.mostRecentUnfinishedReview.toRight(notFound)
        message <- bindFormEitherT[Future](this.forms.ReviewDescription)(_ => BadRequest: Result)
      } yield {
        review.addMessage(Message(message.trim, System.currentTimeMillis(), "stop"))
        review.setEnded(Some(Timestamp.from(Instant.now())))
        Redirect(routes.Reviews.showReviews(author, slug, versionString))
      }

      res.merge
    }
  }

  def approveReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) async { implicit request =>
      val ret = for {
        project <- getProject(author, slug)
        version <- getVersion(project, versionString)
        review <- version.mostRecentUnfinishedReview.toRight(notFound)
        _ <- EitherT.right[Result](
          (
            review.setEnded(Some(Timestamp.from(Instant.now()))),
            // send notification that review happened
            sendReviewNotification(project, version, request.user)
          ).parTupled
        )
      } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))

      ret.merge
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

    val futUsers: Future[Seq[Int]] = this.service.DB.db.run(notificationUsersQuery(project.id.value, version.authorId, None).result).map { list =>
      list.filter {
        case (_, Some(level)) => level.trust.level >= Lifted.level
        case (_, None) => true
      }.map(_._1)
    }

    val notificationTable = TableQuery[NotificationTable]

    futUsers.map { users =>
      users.map { userId =>
        Notification(
          userId = userId,
          createdAt = ObjectTimestamp(Timestamp.from(Instant.now())),
          originId = requestUser.id.value,
          notificationType = NotificationTypes.VersionReviewed,
          messageArgs = List("notification.project.reviewed", project.slug, version.versionString)
        )
      }
    } map (notificationTable ++= _) flatMap (service.DB.db.run(_)) // Batch insert all notifications
  }

  def takeoverReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      val ret = for {
        version <- getProjectVersion(author, slug, versionString)
        message <- bindFormEitherT[Future](this.forms.ReviewDescription)(_ => BadRequest)
        _ <- {
          // Close old review
          val closeOldReview = version.mostRecentUnfinishedReview.semiFlatMap { oldreview =>
            (
              oldreview.addMessage(Message(message.trim, System.currentTimeMillis(), "takeover")),
              oldreview.setEnded(Some(Timestamp.from(Instant.now()))),
              this.service.insert(Review(ObjectId.Uninitialized, ObjectTimestamp(Timestamp.from(Instant.now())), version.id.value, request.user.id.value, None, ""))
            ).parMapN { (_, _, _) => () }
          }.getOrElse(())

          // Then make new one
          val result = (
            closeOldReview,
            this.service.insert(Review(ObjectId.Uninitialized, ObjectTimestamp(Timestamp.from(Instant.now())), version.id.value, request.user.id.value, None, ""))
          ).parTupled
          EitherT.right[Result](result)
        }
      } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))

      ret.merge
    }
  }

  def editReview(author: String, slug: String, versionString: String, reviewId: Int): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      val res = for {
        version <- getProjectVersion(author, slug, versionString)
        review  <- version.reviewById(reviewId).toRight(notFound)
        message <- bindFormEitherT[Future](this.forms.ReviewDescription)(_ => BadRequest: Result)
      } yield {
        review.addMessage(Message(message.trim))
        Ok("Review" + review)
      }

      res.merge
    }
  }

  def addMessage(author: String, slug: String, versionString: String): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
      val ret = for {
        version <- getProjectVersion(author, slug, versionString)
        recentReview <- version.mostRecentUnfinishedReview.toRight(Ok("Review"))
        currentUser <- users.current.toRight(Ok("Review"))
        message <- bindFormEitherT[Future](this.forms.ReviewDescription)(_ => BadRequest)
        _ <- {
          if (recentReview.userId == currentUser.userId) {
            EitherT.right[Result](recentReview.addMessage(Message(message.trim)))
          } else EitherT.rightT[Future, Result](0)
        }
      } yield Ok("Review")

      ret.merge
    }
  }

  def shouldReviewToggle(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated andThen PermissionAction[AuthRequest](ReviewProjects) async { implicit request =>
      val res = for {
        version <- getProjectVersion(author, slug, versionString)
      } yield {

        UserActionLogger.log(request, LoggedAction.VersionNonReviewChanged, version.id.value, s"In review queue: ${version.isNonReviewed}", s"In review queue: ${!version.isNonReviewed}")
        version.setIsNonReviewed(!version.isNonReviewed)

        Redirect(routes.Reviews.showReviews(author, slug, versionString))
      }
      res.merge
    }
  }
}
