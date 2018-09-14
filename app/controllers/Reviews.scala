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
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import slick.lifted.{Rep, TableQuery}
import util.DataHelper
import views.{html => views}
import cats.instances.future._
import cats.syntax.all._
import cats.data.{EitherT, NonEmptyList}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Controller for handling Review related actions.
  */
final class Reviews @Inject()(data: DataHelper,
                              forms: OreForms)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    messagesApi: MessagesApi,
    env: OreEnv,
    cache: AsyncCacheApi,
    config: OreConfig,
    service: ModelService
) extends OreBaseController {

  def showReviews(author: String, slug: String, versionString: String): Action[AnyContent] =
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects) andThen ProjectAction(author, slug)).async { request =>
      implicit val r: Requests.OreRequest[AnyContent] = request.request
      implicit val p: Project = request.data.project

      val res = for {
        version <- getVersion(p, versionString)
        reviews <- EitherT.right[Result](version.mostRecentReviews)
        rv <- EitherT.right[Result](
          Future.traverse(reviews)(r => users.get(r.userId).map(_.name).value.tupleLeft(r))
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
        _ <- EitherT.right[Result](
          service.update(
            version.copy(
              isReviewed = false,
              approvedAt = None,
              reviewerId = -1
            )
          )
        )
        _ <- EitherT.right[Result](
          service
            .update(review.copy(endedAt = None))
            .flatMap(_.addMessage(Message("Reopened the review", System.currentTimeMillis(), "start")))
        )
      } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))

      res.merge
    }
  }

  def stopReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated andThen PermissionAction[AuthRequest](ReviewProjects) async { implicit request =>
      val res = for {
        version <- getProjectVersion(author, slug, versionString)
        review <- version.mostRecentUnfinishedReview.toRight(notFound)
        message <- bindFormEitherT[Future](this.forms.ReviewDescription)(_ => BadRequest: Result)
        _ <- EitherT.right[Result](
          service
            .update(review.copy(endedAt = Some(Timestamp.from(Instant.now()))))
            .flatMap(_.addMessage(Message(message.trim, System.currentTimeMillis(), "stop")))
        )
      } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))

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
            service.update(review.copy(endedAt = Some(Timestamp.from(Instant.now())))),
            // send notification that review happened
            sendReviewNotification(project, version, request.user)
          ).tupled
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
          messageArgs = NonEmptyList.of("notification.project.reviewed", project.slug, version.versionString)
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
          val closeOldReview = version.mostRecentUnfinishedReview.semiflatMap { oldreview =>
            (
              oldreview.addMessage(Message(message.trim, System.currentTimeMillis(), "takeover")),
              service.update(oldreview.copy(endedAt = Some(Timestamp.from(Instant.now())))),
            ).mapN { (_, _) => () }
          }.getOrElse(())

          // Then make new one
          val result = (
            closeOldReview,
            this.service.insert(Review(ObjectId.Uninitialized, ObjectTimestamp(Timestamp.from(Instant.now())), version.id.value, request.user.id.value, None, ""))
          ).tupled
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
        service.update(version.copy(isNonReviewed = !version.isNonReviewed))

        Redirect(routes.Reviews.showReviews(author, slug, versionString))
      }
      res.merge
    }
  }
}
