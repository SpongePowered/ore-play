package controllers

import java.sql.Timestamp
import java.time.Instant
import java.util.Date
import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.i18n.Lang
import play.api.mvc.{Action, ActionBuilder, AnyContent}
import play.api.routing.JavaScriptReverseRouter

import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.schema._
import db.query.AppQueries
import models.project._
import models.querymodels.{FlagActivity, ReviewActivity}
import db.{DbRef, ModelQuery, ModelService}
import form.OreForms
import models.admin.Review
import models.user.role._
import models.user.{LoggedAction, LoggedActionModel, User, UserActionLogger}
import models.viewhelper.OrganizationData
import ore.permission._
import ore.permission.role.{Role, RoleCategory}
import ore.permission.scope.GlobalScope
import ore.project.{Category, ProjectSortingStrategy}
import ore.user.MembershipDossier
import ore.{OreConfig, OreEnv, Platform, PlatformCategory}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.syntax._
import views.{html => views}

import cats.Order
import cats.data.OptionT
import cats.effect.IO
import cats.instances.vector._
import cats.syntax.all._

/**
  * Main entry point for application.
  */
final class Application @Inject()(forms: OreForms)(
    implicit val ec: ExecutionContext,
    auth: SpongeAuthApi,
    bakery: Bakery,
    sso: SingleSignOnConsumer,
    env: OreEnv,
    config: OreConfig,
    cache: AsyncCacheApi,
    service: ModelService
) extends OreBaseController {

  private def FlagAction = Authenticated.andThen(PermissionAction[AuthRequest](ReviewFlags))

  def javascriptRoutes: Action[AnyContent] = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.Application.showHome
      )
    ).as("text/javascript")
  }

  /**
    * Show external link warning page.
    *
    * @return External link page
    */
  def linkOut(remoteUrl: String): Action[AnyContent] = OreAction { implicit request =>
    Ok(views.linkout(remoteUrl))
  }

  /**
    * Display the home page.
    *
    * @return Home page
    */
  def showHome(
      categories: Option[String],
      query: Option[String],
      sort: Option[Int],
      page: Option[Int],
      platformCategory: Option[String],
      platform: Option[String],
      orderWithRelevance: Option[Boolean]
  ): Action[AnyContent] = OreAction.asyncF { implicit request =>
    // Get categories and sorting strategy

    val canHideProjects = request.headerData.globalPerm(HideProjects)
    val currentUserId   = request.headerData.currentUser.map(_.id.value)

    val withRelevance = orderWithRelevance.getOrElse(true)
    val ordering      = sort.flatMap(ProjectSortingStrategy.withValueOpt).getOrElse(ProjectSortingStrategy.Default)
    val pcat          = platformCategory.flatMap(p => PlatformCategory.getPlatformCategories.find(_.name.equalsIgnoreCase(p)))
    val pform         = platform.flatMap(p => Platform.values.find(_.name.equalsIgnoreCase(p)))

    // get the categories being queried
    val categoryPlatformNames = pcat.toList.flatMap(_.getPlatforms.map(_.name))
    val platformNames         = (pform.map(_.name).toList ::: categoryPlatformNames).map(_.toLowerCase)

    val categoryList = categories.fold(Category.fromString(""))(s => Category.fromString(s)).toList

    val pageSize = this.config.ore.projects.initLoad
    val pageNum  = math.max(page.getOrElse(1), 1)
    val offset   = (pageNum - 1) * pageSize

    service
      .runDbCon(
        AppQueries
          .getHomeProjects(
            currentUserId,
            canHideProjects,
            platformNames,
            categoryList,
            query.filter(_.nonEmpty),
            ordering,
            offset,
            pageSize,
            withRelevance
          )
          .to[Vector]
      )
      .map { data =>
        Ok(views.home(data, categoryList, query.filter(_.nonEmpty), pageNum, ordering, pcat, pform, withRelevance))
      }
  }

  /**
    * Shows the moderation queue for unreviewed versions.
    *
    * @return View of unreviewed versions.
    */
  def showQueue(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(ReviewProjects)).asyncF { implicit request =>
      // TODO: Pages
      service.runDbCon(AppQueries.getQueue.to[Vector]).map { queueEntries =>
        val (started, notStarted) = queueEntries.partitionEither(_.sort)
        Ok(views.users.admin.queue(started, notStarted))
      }
    }

  /**
    * Shows the overview page for flags.
    *
    * @return Flag overview
    */
  def showFlags(): Action[AnyContent] = FlagAction.asyncF { implicit request =>
    service
      .runDbCon(
        AppQueries
          .flags(request.user.id.value)
          .to[Vector]
      )
      .map(flagSeq => Ok(views.users.admin.flags(flagSeq)))
  }

  /**
    * Sets the resolved state of the specified flag.
    *
    * @param flagId   Flag to set
    * @param resolved Resolved state
    * @return         Ok
    */
  def setFlagResolved(flagId: DbRef[Flag]): Action[AnyContent] =
    FlagAction.asyncF { implicit request =>
      implicit val lang: Lang = request.lang

      this.service
        .access[Flag]()
        .get(flagId)
        .semiflatMap { flag =>
          for {
            user        <- users.current.value
            _           <- flag.resolvedBy(user)
            flagCreator <- flag.user
            _ <- UserActionLogger.log(
              request,
              LoggedAction.ProjectFlagResolved,
              flag.projectId,
              s"Flag Resolved by ${user.fold("unknown")(_.name)}",
              s"Flagged by ${flagCreator.name}"
            )
          } yield Redirect(routes.Application.showFlags()).withSuccess(messagesApi.apply("user.flags.resolved"))
        }
        .getOrElse(Redirect(routes.Application.showFlags()).withError("Error!"))
    }

  def showHealth(): Action[AnyContent] = Authenticated.andThen(PermissionAction[AuthRequest](ViewHealth)).asyncF {
    implicit request =>
      implicit val timestampOrder: Order[Timestamp] = Order.from[Timestamp](_.compareTo(_))

      (
        service.runDbCon(AppQueries.getUnhealtyProjects(config.ore.projects.staleAge).to[Vector]),
        projects.missingFile.flatMap { versions =>
          versions.toVector.traverse(v => v.project.tupleLeft(v))
        }
      ).parMapN { (unhealtyProjects, missingFileProjects) =>
        val noTopicProjects    = unhealtyProjects.filter(p => p.topicId.isEmpty || p.postId.isEmpty)
        val topicDirtyProjects = unhealtyProjects.filter(_.isTopicDirty)
        val staleProjects = unhealtyProjects
          .filter(_.lastUpdated > new Timestamp(new Date().getTime - config.ore.projects.staleAge.toMillis))
        val notPublic = unhealtyProjects.filter(_.visibility != Visibility.Public)
        Ok(views.users.admin.health(noTopicProjects, topicDirtyProjects, staleProjects, notPublic, missingFileProjects))
      }
  }

  /**
    * Removes a trailing slash from a route.
    *
    * @param path Path with trailing slash
    * @return     Redirect to proper route
    */
  def removeTrail(path: String): Action[AnyContent] = Action(MovedPermanently(s"/$path"))

  /**
    * Show the activities page for a user
    */
  def showActivities(user: String): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(ReviewProjects)).asyncF { implicit request =>
      val dbProgram = for {
        reviewActibity <- AppQueries.getReviewActivity(user).to[Vector]
        flagActivity   <- AppQueries.getFlagActivity(user).to[Vector]
      } yield (reviewActibity, flagActivity)

      service.runDbCon(dbProgram).map {
        case (reviewActivity, flagActivity) =>
          val activities       = reviewActivity.map(_.asRight[FlagActivity]) ++ flagActivity.map(_.asLeft[ReviewActivity])
          val sortedActivities = activities.sortWith(sortActivities)
          Ok(views.users.admin.activity(user, sortedActivities))
      }
    }

  /**
    * Compares 2 activities (supply a [[Review]] or [[Flag]]) to decide which came first
    * @param o1 Review / Flag
    * @param o2 Review / Flag
    * @return Boolean
    */
  def sortActivities(
      o1: Either[FlagActivity, ReviewActivity],
      o2: Either[FlagActivity, ReviewActivity]
  ): Boolean = {
    val o1Time: Long = o1 match {
      case Right(review) => review.endedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
      case _             => 0
    }
    val o2Time: Long = o2 match {
      case Left(flag) => flag.resolvedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
      case _          => 0
    }
    o1Time > o2Time
  }

  /**
    * Show stats
    * @return
    */
  def showStats(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction[AuthRequest](ViewStats)).asyncF { implicit request =>
      service.runDbCon(AppQueries.getStats(0, 10).to[List]).map { stats =>
        Ok(views.users.admin.stats(stats))
      }
    }

  def showLog(
      oPage: Option[Int],
      userFilter: Option[DbRef[User]],
      projectFilter: Option[DbRef[Project]],
      versionFilter: Option[DbRef[Version]],
      pageFilter: Option[DbRef[Page]],
      actionFilter: Option[Int],
      subjectFilter: Option[DbRef[_]]
  ): Action[AnyContent] = Authenticated.andThen(PermissionAction(ViewLogs)).asyncF { implicit request =>
    val pageSize = 50
    val page     = oPage.getOrElse(1)
    val offset   = (page - 1) * pageSize

    (
      service.runDbCon(
        AppQueries
          .getLog(oPage, userFilter, projectFilter, versionFilter, pageFilter, actionFilter, subjectFilter)
          .to[Vector]
      ),
      service.access[LoggedActionModel[Any]]().size
    ).parMapN { (actions, size) =>
      Ok(
        views.users.admin.log(
          actions,
          pageSize,
          offset,
          page,
          size,
          userFilter,
          projectFilter,
          versionFilter,
          pageFilter,
          actionFilter,
          subjectFilter,
          request.headerData.globalPerm(ViewIp)
        )
      )
    }
  }

  def UserAdminAction: ActionBuilder[AuthRequest, AnyContent] =
    Authenticated.andThen(PermissionAction(UserAdmin))

  def userAdmin(user: String): Action[AnyContent] = UserAdminAction.asyncF { implicit request =>
    users
      .withName(user)
      .semiflatMap { u =>
        for {
          isOrga <- u.toMaybeOrganization.isDefined
          t1 <- {
            if (isOrga)
              (IO.pure(Nil), getOrga(user).value).tupled
            else
              (u.projectRoles.all, IO.pure(None)).tupled
          }
          (projectRoles, orga) = t1
          t2 <- (
            getUserData(request, user).value,
            projectRoles.toVector.parTraverse(_.project),
            OrganizationData.of(orga).value
          ).parTupled
          (userData, projects, orgaData) = t2
        } yield {
          val pr = projects.zip(projectRoles)
          Ok(views.users.admin.userAdmin(userData.get, orgaData, pr))
        }
      }
      .getOrElse(notFound)
  }

  def updateUser(userName: String): Action[(String, String, String)] =
    UserAdminAction.asyncF(parse.form(forms.UserAdminUpdate)) { implicit request =>
      users
        .withName(userName)
        .map { user =>
          //TODO: Make the form take json directly
          val (thing, action, data) = request.body
          import play.api.libs.json._
          val json       = Json.parse(data)
          val orgDossier = MembershipDossier.organization

          def updateRoleTable[M0 <: UserRoleModel { type M = M0 }: ModelQuery](
              modelAccess: ModelAccess[M0],
              allowedCategory: RoleCategory,
              ownerType: Role,
              transferOwner: M0 => IO[M0],
              setRoleType: (M0, Role) => IO[M0],
              setAccepted: (M0, Boolean) => IO[M0]
          ) = {
            val id = (json \ "id").as[DbRef[M0]]
            action match {
              case "setRole" =>
                modelAccess.get(id).semiflatMap { role =>
                  val roleType = Role.withValue((json \ "role").as[String])

                  if (roleType == ownerType)
                    transferOwner(role).as(Ok)
                  else if (roleType.category == allowedCategory && roleType.isAssignable)
                    setRoleType(role, roleType).as(Ok)
                  else
                    IO.pure(BadRequest)
                }
              case "setAccepted" =>
                modelAccess
                  .get(id)
                  .semiflatMap(role => setAccepted(role, (json \ "accepted").as[Boolean]).as(Ok))
              case "deleteRole" =>
                modelAccess
                  .get(id)
                  .filter(_.role.isAssignable)
                  .semiflatMap(service.delete(_).as(Ok))
            }
          }

          def transferOrgOwner(r: OrganizationUserRole) =
            r.organization
              .flatMap(orga => orga.transferOwner(orgDossier.newMember(orga, r.userId)))
              .as(r)

          thing match {
            case "orgRole" =>
              OptionT.liftF(user.toMaybeOrganization.isEmpty).filter(identity).flatMap { _ =>
                updateRoleTable[OrganizationUserRole](
                  user.organizationRoles,
                  RoleCategory.Organization,
                  Role.OrganizationOwner,
                  transferOrgOwner,
                  (r, tpe) => user.organizationRoles.update(r.copy(role = tpe)),
                  (r, accepted) => user.organizationRoles.update(r.copy(isAccepted = accepted))
                )
              }
            case "memberRole" =>
              user.toMaybeOrganization.flatMap { orga =>
                updateRoleTable[OrganizationUserRole](
                  orgDossier.roles(orga),
                  RoleCategory.Organization,
                  Role.OrganizationOwner,
                  transferOrgOwner,
                  (r, tpe) => orgDossier.roles(orga).update(r.copy(role = tpe)),
                  (r, accepted) => orgDossier.roles(orga).update(r.copy(isAccepted = accepted))
                )
              }
            case "projectRole" =>
              OptionT.liftF(user.toMaybeOrganization.isEmpty).filter(identity).flatMap { _ =>
                updateRoleTable[ProjectUserRole](
                  user.projectRoles,
                  RoleCategory.Project,
                  Role.ProjectOwner,
                  r => r.project.flatMap(p => p.transferOwner(p.memberships.newMember(p, r.userId))).as(r),
                  (r, tpe) => user.projectRoles.update(r.copy(role = tpe)),
                  (r, accepted) => user.projectRoles.update(r.copy(isAccepted = accepted))
                )
              }
            case _ => OptionT.none[IO, Status]
          }
        }
        .semiflatMap(_.getOrElse(BadRequest))
        .getOrElse(NotFound)
    }

  def showProjectVisibility(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction[AuthRequest](ReviewVisibility)).asyncF { implicit request =>
      (
        service.runDbCon(AppQueries.getVisibilityNeedsApproval.to[Vector]),
        service.runDbCon(AppQueries.getVisibilityWaitingProject.to[Vector])
      ).mapN { (needsApproval, waitingProject) =>
        Ok(views.users.admin.visibility(needsApproval, waitingProject))
      }
    }
}
