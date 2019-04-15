package controllers

import java.sql.Timestamp
import java.time.Instant
import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, Result}

import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{OrganizationMembersTable, OrganizationRoleTable, OrganizationTable, UserTable}
import db.{DbRef, Model, ModelService}
import form.OreForms
import models.admin.{Message, Review}
import models.project.{Project, ReviewState, Version}
import models.user.{LoggedAction, Notification, User, UserActionLogger}
import ore.markdown.MarkdownRenderer
import ore.permission.Permission
import ore.permission.role.Role
import ore.user.notification.NotificationType
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.{html => views}

import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO
import cats.syntax.all._
import cats.instances.option._
import slick.lifted.{Rep, TableQuery}

/**
  * Controller for handling Review related actions.
  */
final class Reviews @Inject()(forms: OreForms)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    env: OreEnv,
    cache: AsyncCacheApi,
    config: OreConfig,
    service: ModelService,
    renderer: MarkdownRenderer
) extends OreBaseController {

  def showReviews(author: String, slug: String, versionString: String): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(Permission.Reviewer)).andThen(ProjectAction(author, slug)).asyncEitherT {
      implicit request =>
        for {
          version <- getVersion(request.project, versionString)
          dbio = version
            .mostRecentReviews(ModelView.raw(Review))
            .joinLeft(TableQuery[UserTable])
            .on(_.userId === _.id)
            .map(t => t._1 -> t._2.map(_.name))
            .result
          rv <- EitherT.right[Result](service.runDBIO(dbio))
        } yield {
          val unfinished = rv.map(_._1).filter(_.endedAt.isEmpty).sorted(Review.ordering2).headOption
          Ok(views.users.admin.reviews(Model.unwrapNested(unfinished), rv, request.project, version))
        }
    }

  def createReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated.andThen(PermissionAction(Permission.Reviewer)).asyncEitherT { implicit request =>
      getProjectVersion(author, slug, versionString).semiflatMap { version =>
        val review = Review(
          version.id,
          request.user.id,
          None,
          JsObject.empty
        )
        this.service.insert(review).as(Redirect(routes.Reviews.showReviews(author, slug, versionString)))
      }
    }
  }

  def reopenReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated.andThen(PermissionAction(Permission.Reviewer)).asyncEitherT { implicit request =>
      for {
        version <- getProjectVersion(author, slug, versionString)
        review  <- version.mostRecentReviews(ModelView.now(Review)).one.toRight(notFound)
        _ <- EitherT.right[Result](
          service.update(version)(
            _.copy(
              reviewState = ReviewState.Unreviewed,
              approvedAt = None,
              reviewerId = None
            )
          )
        )
        _ <- EitherT.right[Result](
          service
            .update(review)(_.copy(endedAt = None))
            .flatMap(_.addMessage(Message("Reopened the review", System.currentTimeMillis(), "start")))
        )
      } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))
    }
  }

  def stopReview(author: String, slug: String, versionString: String): Action[String] = {
    Authenticated
      .andThen(PermissionAction(Permission.Reviewer))
      .asyncEitherT(parse.form(forms.ReviewDescription)) { implicit request =>
        for {
          version <- getProjectVersion(author, slug, versionString)
          review  <- version.mostRecentUnfinishedReview(ModelView.now(Review)).toRight(notFound)
          _ <- EitherT.right[Result](
            service
              .update(review)(_.copy(endedAt = Some(Timestamp.from(Instant.now()))))
              .flatMap(_.addMessage(Message(request.body.trim, System.currentTimeMillis(), "stop")))
          )
        } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))
      }
  }

  def approveReview(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated.andThen(PermissionAction(Permission.Reviewer)).asyncEitherT { implicit request =>
      for {
        project <- getProject(author, slug)
        version <- getVersion(project, versionString)
        review  <- version.mostRecentUnfinishedReview(ModelView.now(Review)).toRight(notFound)
        _ <- EitherT.right[Result](
          (
            service.update(review)(_.copy(endedAt = Some(Timestamp.from(Instant.now())))),
            // send notification that review happened
            sendReviewNotification(project, version, request.user)
          ).parTupled
        )
      } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))
    }
  }

  private def queryNotificationUsers(
      projectId: Rep[DbRef[Project]],
      userId: Rep[DbRef[User]]
  ): Query[(Rep[DbRef[User]], Rep[Option[Role]]), (DbRef[User], Option[Role]), Seq] = {
    // Query Orga Members
    val q1 = for {
      org     <- TableQuery[OrganizationTable] if org.id === projectId
      members <- TableQuery[OrganizationMembersTable] if org.id === members.organizationId
      roles   <- TableQuery[OrganizationRoleTable] if members.userId === roles.userId // TODO roletype lvl in database?
      users   <- TableQuery[UserTable] if members.userId === users.id
    } yield (users.id, roles.roleType.?)

    // Query version author
    val q2 = for {
      user <- TableQuery[UserTable] if user.id === userId
    } yield (user.id, None: Rep[Option[Role]])

    q1 ++ q2 // Union
  }

  private lazy val notificationUsersQuery = Compiled(queryNotificationUsers _)

  private def sendReviewNotification(
      project: Model[Project],
      version: Version,
      requestUser: Model[User]
  ): IO[Unit] = {
    val usersF =
      service.runDBIO(notificationUsersQuery((project.id, version.authorId)).result).map { list =>
        list.collect {
          case (res, Some(role)) if role.permissions.has(Permission.EditVersion) => res
          case (res, None)                                                       => res
        }
      }

    usersF
      .map { users =>
        users.map { userId =>
          Notification(
            userId = userId,
            originId = requestUser.id,
            notificationType = NotificationType.VersionReviewed,
            messageArgs = NonEmptyList.of("notification.project.reviewed", project.slug, version.versionString)
          )
        }
      }
      .flatMap(service.bulkInsert(_))
      .void
  }

  def takeoverReview(author: String, slug: String, versionString: String): Action[String] = {
    Authenticated
      .andThen(PermissionAction(Permission.Reviewer))
      .asyncEitherT(parse.form(forms.ReviewDescription)) { implicit request =>
        for {
          version <- getProjectVersion(author, slug, versionString)
          _ <- {
            // Close old review
            val closeOldReview = version
              .mostRecentUnfinishedReview(ModelView.now(Review))
              .semiflatMap { oldreview =>
                (
                  oldreview.addMessage(Message(request.body.trim, System.currentTimeMillis(), "takeover")),
                  service.update(oldreview)(_.copy(endedAt = Some(Timestamp.from(Instant.now())))),
                ).parTupled.void
              }
              .getOrElse(())

            // Then make new one
            val result = (
              closeOldReview,
              this.service.insert(
                Review(
                  version.id,
                  request.user.id,
                  None,
                  JsObject.empty
                )
              )
            ).parTupled
            EitherT.right[Result](result)
          }
        } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))
      }
  }

  def editReview(author: String, slug: String, versionString: String, reviewId: DbRef[Review]): Action[String] = {
    Authenticated
      .andThen(PermissionAction(Permission.Reviewer))
      .asyncEitherT(parse.form(forms.ReviewDescription)) { implicit request =>
        for {
          version <- getProjectVersion(author, slug, versionString)
          review  <- version.reviewById(reviewId).toRight(notFound)
          _       <- EitherT.right[Result](review.addMessage(Message(request.body.trim)))
        } yield Ok("Review" + review)
      }
  }

  def addMessage(author: String, slug: String, versionString: String): Action[String] = {
    Authenticated.andThen(PermissionAction(Permission.Reviewer)).asyncEitherT(parse.form(forms.ReviewDescription)) {
      implicit request =>
        for {
          version      <- getProjectVersion(author, slug, versionString)
          recentReview <- version.mostRecentUnfinishedReview(ModelView.now(Review)).toRight(Ok("Review"))
          currentUser  <- users.current.toRight(Ok("Review"))
          _ <- {
            if (recentReview.userId == currentUser.id.value) {
              EitherT.right[Result](recentReview.addMessage(Message(request.body.trim)))
            } else EitherT.rightT[IO, Result](0)
          }
        } yield Ok("Review")
    }
  }

  def backlogToggle(author: String, slug: String, versionString: String): Action[AnyContent] = {
    Authenticated.andThen(PermissionAction[AuthRequest](Permission.Reviewer)).asyncEitherT { implicit request =>
      for {
        version <- getProjectVersion(author, slug, versionString)
        oldState <- EitherT.cond[IO](
          Seq(ReviewState.Backlog, ReviewState.Unreviewed).contains(version.reviewState),
          version.reviewState,
          BadRequest("Invalid state for toggle backlog")
        )
        newState = oldState match {
          case ReviewState.Unreviewed => ReviewState.Backlog
          case ReviewState.Backlog    => ReviewState.Unreviewed
          case _                      => oldState
        }
        _ <- EitherT.right[Result](
          UserActionLogger.log(
            request,
            LoggedAction.VersionReviewStateChanged,
            version.id,
            newState.toString,
            oldState.toString,
          )
        )
        _ <- EitherT.right[Result](service.update(version)(_.copy(reviewState = newState)))
      } yield Redirect(routes.Reviews.showReviews(author, slug, versionString))
    }
  }
}
