package controllers

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate}
import java.util.Date
import javax.inject.{Inject, Singleton}

import scala.util.Try

import play.api.mvc.{Action, ActionBuilder, AnyContent}

import controllers.sugar.Requests.AuthRequest
import db.impl.query.AppQueries
import form.OreForms
import models.querymodels.{FlagActivity, ReviewActivity}
import models.viewhelper.OrganizationData
import ore.data.project.Category
import ore.data.{Platform, PlatformCategory}
import ore.db._
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.ProjectTableMain
import ore.markdown.MarkdownRenderer
import ore.member.MembershipDossier
import ore.models.organization.Organization
import ore.models.project.io.ProjectFiles
import ore.models.project.{ProjectSortingStrategy, _}
import ore.models.user._
import ore.models.user.role._
import ore.permission._
import ore.permission.role.{Role, RoleCategory}
import util.UserActionLogger
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
@Singleton
final class Application @Inject()(forms: OreForms)(
    implicit oreComponents: OreControllerComponents[IO],
    renderer: MarkdownRenderer,
    projectFiles: ProjectFiles
) extends OreBaseController {

  private def FlagAction = Authenticated.andThen(PermissionAction[AuthRequest](Permission.ModNotesAndFlags))

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
      categories: Seq[String],
      query: Option[String],
      sort: Option[String],
      page: Option[Int],
      platformCategory: Option[String],
      platform: Option[String],
      orderWithRelevance: Option[Boolean]
  ): Action[AnyContent] = OreAction.asyncF { implicit request =>
    import cats.instances.list._
    import cats.instances.option._

    val canSeeHidden  = request.headerData.globalPerm(Permission.SeeHidden)
    val currentUserId = request.headerData.currentUser.map(_.id.value)

    // Get categories and sorting strategy
    val withRelevance = orderWithRelevance.getOrElse(true)
    val ordering =
      sort.flatMap(s => ProjectSortingStrategy.values.find(_.apiName == s)).getOrElse(ProjectSortingStrategy.Default)
    val pcat  = platformCategory.flatMap(p => PlatformCategory.getPlatformCategories.find(_.name.equalsIgnoreCase(p)))
    val pform = platform.flatMap(p => Platform.values.find(_.name.equalsIgnoreCase(p)))

    // get the categories being queried
    val categoryPlatformNames = pcat.toList.flatMap(_.getPlatforms.map(_.name))
    val platformNames         = (pform.map(_.name).toList ::: categoryPlatformNames).map(_.toLowerCase)

    val categoryList = categories.toList.traverse(Category.fromApiName).getOrElse(Nil)

    val pageSize = this.config.ore.projects.initLoad
    val pageNum  = math.max(page.getOrElse(1), 1)
    val offset   = (pageNum - 1) * pageSize

    val projectNumQ = TableQuery[ProjectTableMain].filter(_.visibility === (Visibility.Public: Visibility)).size

    val projectListF = service
      .runDbCon(
        AppQueries
          .getHomeProjects(
            currentUserId,
            canSeeHidden,
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
    val projectNumF = service.runDBIO(projectNumQ.result)

    (projectListF, projectNumF).parMapN { (data, projectNum) =>
      val catList =
        if (categoryList.isEmpty || Category.visible.toSet.equals(categoryList.toSet)) None else Some(categoryList)
      Ok(views.home(data, catList, query.filter(_.nonEmpty), pageNum, ordering, pcat, pform, withRelevance, projectNum))
    }
  }

  /**
    * Shows the moderation queue for unreviewed versions.
    *
    * @return View of unreviewed versions.
    */
  def showQueue(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(Permission.Reviewer)).asyncF { implicit request =>
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
          .flags(request.user.id)
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
  def setFlagResolved(flagId: DbRef[Flag], resolved: Boolean): Action[AnyContent] =
    FlagAction.asyncF { implicit request =>
      ModelView
        .now(Flag)
        .get(flagId)
        .semiflatMap { flag =>
          for {
            user        <- users.current.value
            _           <- flag.markResolved(resolved, user)
            flagCreator <- flag.user
            _ <- UserActionLogger.log(
              request,
              LoggedAction.ProjectFlagResolved,
              flag.projectId,
              s"Flag Resolved by ${user.fold("unknown")(_.name)}",
              s"Flagged by ${flagCreator.name}"
            )
          } yield Ok
        }
        .getOrElse(NotFound)
    }

  def showHealth(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction[AuthRequest](Permission.ViewHealth)).asyncF { implicit request =>
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
        Ok(
          views.users.admin.health(
            noTopicProjects,
            topicDirtyProjects,
            staleProjects,
            notPublic,
            Model.unwrapNested(missingFileProjects)
          )
        )
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
    Authenticated.andThen(PermissionAction(Permission.Reviewer)).asyncF { implicit request =>
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
      case Right(review) => review.endedAt.getOrElse(Instant.EPOCH).toEpochMilli
      case _             => 0
    }
    val o2Time: Long = o2 match {
      case Left(flag) => flag.resolvedAt.getOrElse(Instant.EPOCH).toEpochMilli
      case _          => 0
    }
    o1Time > o2Time
  }

  /**
    * Show stats
    * @return
    */
  def showStats(from: Option[String], to: Option[String]): Action[AnyContent] =
    Authenticated.andThen(PermissionAction[AuthRequest](Permission.ViewStats)).asyncF { implicit request =>
      def parseTime(time: Option[String], default: LocalDate) =
        time.map(s => Try(LocalDate.parse(s)).toOption).getOrElse(Some(default))

      val res = for {
        fromTime <- parseTime(from, LocalDate.now().minus(10, ChronoUnit.DAYS))
        toTime   <- parseTime(to, LocalDate.now())
        if fromTime.isBefore(toTime)
      } yield {
        service.runDbCon(AppQueries.getStats(fromTime, toTime).to[List]).map { stats =>
          Ok(views.users.admin.stats(stats, fromTime, toTime))
        }
      }

      res.getOrElse(IO.pure(BadRequest))
    }

  def showLog(
      oPage: Option[Int],
      userFilter: Option[DbRef[User]],
      projectFilter: Option[DbRef[Project]],
      versionFilter: Option[DbRef[Version]],
      pageFilter: Option[DbRef[Page]],
      actionFilter: Option[Int],
      subjectFilter: Option[DbRef[_]]
  ): Action[AnyContent] = Authenticated.andThen(PermissionAction(Permission.ViewLogs)).asyncF { implicit request =>
    val pageSize = 50
    val page     = oPage.getOrElse(1)
    val offset   = (page - 1) * pageSize

    (
      service.runDbCon(
        AppQueries
          .getLog(oPage, userFilter, projectFilter, versionFilter, pageFilter, actionFilter, subjectFilter)
          .to[Vector]
      ),
      ModelView.now(LoggedActionModel).size
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
          request.headerData.globalPerm(Permission.ViewIp)
        )
      )
    }
  }

  def UserAdminAction: ActionBuilder[AuthRequest, AnyContent] =
    Authenticated.andThen(PermissionAction(Permission.EditAllUserSettings))

  def userAdmin(user: String): Action[AnyContent] = UserAdminAction.asyncF { implicit request =>
    users
      .withName(user)
      .semiflatMap { u =>
        for {
          orga <- u.toMaybeOrganization(ModelView.now(Organization)).value
          projectRoles <- orga.fold(
            service.runDBIO(u.projectRoles(ModelView.raw(ProjectUserRole)).result)
          )(orga => IO.pure(Nil))
          t2 <- (
            getUserData(request, user).value,
            projectRoles.toVector.parTraverse(_.project),
            OrganizationData.of(orga).value
          ).parTupled
          (userData, projects, orgaData) = t2
        } yield {
          val pr = projects.zip(projectRoles)
          Ok(views.users.admin.userAdmin(userData.get, orgaData, pr.map(t => t._1.obj -> t._2)))
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
          val orgDossier = MembershipDossier.organizationHasMemberships

          def updateRoleTable[M0 <: UserRoleModel[M0]: ModelQuery](model: ModelCompanion[M0])(
              modelAccess: ModelView.Now[IO, model.T, Model[M0]],
              allowedCategory: RoleCategory,
              ownerType: Role,
              transferOwner: Model[M0] => IO[Model[M0]]
          ) = {
            val id = (json \ "id").as[DbRef[M0]]
            action match {
              case "setRole" =>
                modelAccess.get(id).semiflatMap { role =>
                  val roleType = Role.withValue((json \ "role").as[String])

                  if (roleType == ownerType)
                    transferOwner(role).as(Ok)
                  else if (roleType.category == allowedCategory && roleType.isAssignable)
                    service.update(role)(_.withRole(roleType)).as(Ok)
                  else
                    IO.pure(BadRequest)
                }
              case "setAccepted" =>
                modelAccess
                  .get(id)
                  .semiflatMap(role => service.update(role)(_.withAccepted((json \ "accepted").as[Boolean])).as(Ok))
              case "deleteRole" =>
                modelAccess
                  .get(id)
                  .filter(_.role.isAssignable)
                  .semiflatMap(service.delete(_).as(Ok))
            }
          }

          def transferOrgOwner(r: Model[OrganizationUserRole]) =
            r.organization
              .flatMap(_.transferOwner(r.userId))
              .as(r)

          thing match {
            case "orgRole" =>
              OptionT.liftF(user.toMaybeOrganization(ModelView.now(Organization)).isEmpty).filter(identity).flatMap {
                _ =>
                  updateRoleTable(OrganizationUserRole)(
                    user.organizationRoles(ModelView.now(OrganizationUserRole)),
                    RoleCategory.Organization,
                    Role.OrganizationOwner,
                    transferOrgOwner,
                  )
              }
            case "memberRole" =>
              user.toMaybeOrganization(ModelView.now(Organization)).flatMap { orga =>
                updateRoleTable(OrganizationUserRole)(
                  orgDossier.roles(orga),
                  RoleCategory.Organization,
                  Role.OrganizationOwner,
                  transferOrgOwner,
                )
              }
            case "projectRole" =>
              OptionT.liftF(user.toMaybeOrganization(ModelView.now(Organization)).isEmpty).filter(identity).flatMap {
                _ =>
                  updateRoleTable(ProjectUserRole)(
                    user.projectRoles(ModelView.now(ProjectUserRole)),
                    RoleCategory.Project,
                    Role.ProjectOwner,
                    r => r.project.flatMap(_.transferOwner(r.userId)).as(r),
                  )
              }
            case _ => OptionT.none[IO, Status]
          }
        }
        .semiflatMap(_.getOrElse(BadRequest))
        .getOrElse(NotFound)
    }

  def showProjectVisibility(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction[AuthRequest](Permission.Reviewer)).asyncF { implicit request =>
      (
        service.runDbCon(AppQueries.getVisibilityNeedsApproval.to[Vector]),
        service.runDbCon(AppQueries.getVisibilityWaitingProject.to[Vector])
      ).mapN { (needsApproval, waitingProject) =>
        Ok(views.users.admin.visibility(needsApproval, waitingProject))
      }
    }

  def swagger(): Action[AnyContent] = OreAction { implicit request =>
    Ok(views.swagger())
  }
}
