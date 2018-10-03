package controllers

import java.sql.Timestamp
import java.time.Instant
import java.util.Date
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import play.api.cache.AsyncCacheApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}

import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{
  FlagTable,
  LoggedActionViewTable,
  ProjectTableMain,
  UserTable,
}
import db.query.AppQueries
import db.{ModelService, ObjectReference}
import form.OreForms
import models.admin.Review
import models.project._
import models.querymodels.{FlagActivity, ReviewActivity}
import models.user.role._
import models.user.{LoggedAction, LoggedActionModel, UserActionLogger}
import models.viewhelper.OrganizationData
import ore.permission._
import ore.permission.role.{Role, RoleCategory}
import ore.permission.scope.GlobalScope
import ore.project.{Category, ProjectSortingStrategy}
import ore.{OreConfig, OreEnv, Platform, PlatformCategory}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.{html => views}

import cats.Order
import cats.data.OptionT
import cats.instances.future._
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

  /**
    * Show external link warning page.
    *
    * @return External link page
    */
  def linkOut(remoteUrl: String) = OreAction { implicit request =>
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
      platform: Option[String]
  ): Action[AnyContent] = OreAction.async { implicit request =>
    // Get categories and sorting strategy

    val canHideProjects = request.headerData.globalPerm(HideProjects)
    val currentUserId   = request.headerData.currentUser.map(_.id.value).getOrElse(-1L)

    val ordering = sort.flatMap(ProjectSortingStrategy.withValueOpt).getOrElse(ProjectSortingStrategy.Default)
    val pcat     = platformCategory.flatMap(p => PlatformCategory.getPlatformCategories.find(_.name.equalsIgnoreCase(p)))
    val pform    = platform.flatMap(p => Platform.values.find(_.name.equalsIgnoreCase(p)))

    // get the categories being queried
    val categoryPlatformNames = pcat.toList.flatMap(_.getPlatforms.map(_.name))
    val platformNames         = (pform.map(_.name).toList ::: categoryPlatformNames).map(_.toLowerCase)

    val categoryList = categories.fold(Category.fromString(""))(s => Category.fromString(s)).toList
    val q            = query.fold("%")(qStr => s"%${qStr.toLowerCase}%")

    val pageSize = this.config.projects.get[Int]("init-load")
    val pageNum  = page.getOrElse(1)
    val offset   = (pageNum - 1) * pageSize

    runDbProgram(
      AppQueries
        .getHomeProjects(currentUserId, canHideProjects, platformNames, categoryList, q, ordering, offset, pageSize)
        .to[Vector]
    ).map { data =>
      val catList =
        if (categoryList.isEmpty || Category.visible.toSet.equals(categoryList.toSet)) None else Some(categoryList)
      Ok(views.home(data, catList, query.filter(_.nonEmpty), pageNum, ordering, pcat, pform))
    }
  }

  /**
    * Shows the moderation queue for unreviewed versions.
    *
    * @return View of unreviewed versions.
    */
  def showQueue(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(ReviewProjects)).async { implicit request =>
      // TODO: Pages
      runDbProgram(AppQueries.getQueue.to[Vector]).map { queueEntries =>
        val (started, notStarted) = queueEntries.partitionEither(_.sort)
        Ok(views.users.admin.queue(started, notStarted))
      }
    }

  /**
    * Shows the overview page for flags.
    *
    * @return Flag overview
    */
  def showFlags(): Action[AnyContent] = FlagAction.async { implicit request =>
    val query = for {
      flag    <- TableQuery[FlagTable] if !flag.isResolved
      project <- TableQuery[ProjectTableMain] if flag.projectId === project.id
      user    <- TableQuery[UserTable] if flag.userId === user.id
    } yield (flag, project, user)

    for {
      seq         <- service.doAction(query.result)
      globalRoles <- request.user.globalRoles.all
      perms <- Future.traverse(seq.map(_._2)) { project =>
        request.user
          .trustIn(project)
          .map(request.user.can.asMap(_, globalRoles)(Visibility.values.map(_.permission): _*))
      }
    } yield {
      val data = seq.zip(perms).map {
        case ((flag, project, user), perm) => (flag, user, project, perm)
      }
      Ok(views.users.admin.flags(data))
    }
  }

  /**
    * Sets the resolved state of the specified flag.
    *
    * @param flagId   Flag to set
    * @param resolved Resolved state
    * @return         Ok
    */
  def setFlagResolved(flagId: ObjectReference, resolved: Boolean): Action[AnyContent] =
    FlagAction.async { implicit request =>
      this.service
        .access[Flag](classOf[Flag])
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

  def showHealth(): Action[AnyContent] = Authenticated.andThen(PermissionAction[AuthRequest](ViewHealth)).async {
    implicit request =>
      implicit val timestampOrder: Order[Timestamp] = Order.from[Timestamp](_.compareTo(_))

      (
        runDbProgram(AppQueries.getUnhealtyProjects.to[Vector]),
        projects.missingFile.flatMap { versions =>
          Future.traverse(versions)(v => v.project.tupleLeft(v))
        }
      ).mapN { (unhealthyProjects, missingFileProjects) =>
        val noTopicProjects    = unhealthyProjects.filter(p => p.topicId.isEmpty || p.postId.isEmpty)
        val topicDirtyProjects = unhealthyProjects.filter(_.isTopicDirty)
        val staleProjects = unhealthyProjects
          .filter(_.lastUpdated > new Timestamp(new Date().getTime - this.config.projects.get[Int]("staleAge")))
        val notPublicProjects = unhealthyProjects.filter(_.visibility != Visibility.Public)

        Ok(
          views.users.admin
            .health(noTopicProjects, topicDirtyProjects, staleProjects, notPublicProjects, missingFileProjects)
        )
      }
  }

  /**
    * Removes a trailing slash from a route.
    *
    * @param path Path with trailing slash
    * @return     Redirect to proper route
    */
  def removeTrail(path: String) = Action(MovedPermanently(s"/$path"))

  /**
    * Show the activities page for a user
    */
  def showActivities(user: String): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(ReviewProjects)).async { implicit request =>
      runDbProgram(AppQueries.getReviewActivity(user).to[Vector])
        .map2(runDbProgram(AppQueries.getFlagActivity(user).to[Vector])) { (reviewActivity, flagActivity) =>
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
    Authenticated.andThen(PermissionAction[AuthRequest](ViewStats)).async { implicit request =>
      runDbProgram(AppQueries.getStats(0, 10).to[List]).map { stats =>
        Ok(views.users.admin.stats(stats))
      }
    }

  def showLog(
      oPage: Option[Int],
      userFilter: Option[ObjectReference],
      projectFilter: Option[ObjectReference],
      versionFilter: Option[ObjectReference],
      pageFilter: Option[ObjectReference],
      actionFilter: Option[Int],
      subjectFilter: Option[ObjectReference]
  ): Action[AnyContent] = Authenticated.andThen(PermissionAction(ViewLogs)).async { implicit request =>
    val pageSize = 50
    val page     = oPage.getOrElse(1)
    val offset   = (page - 1) * pageSize

    val default = LiteralColumn(true)

    val logQuery = TableQuery[LoggedActionViewTable]
      .filter { action =>
        (action.userId === userFilter).getOrElse(default) &&
        (action.filterProject === projectFilter).getOrElse(default) &&
        (action.filterVersion === versionFilter).getOrElse(default) &&
        (action.filterPage === pageFilter).getOrElse(default) &&
        (action.filterAction === actionFilter).getOrElse(default) &&
        (action.filterSubject === subjectFilter).getOrElse(default)
      }
      .sortBy(_.id.desc)
      .drop(offset)
      .take(pageSize)

    (
      service.doAction(logQuery.result),
      service.access[LoggedActionModel](classOf[LoggedActionModel]).size,
      request.currentUser.get.can(ViewIp).in(GlobalScope)
    ).mapN { (actions, size, canViewIP) =>
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
          canViewIP
        )
      )
    }
  }

  def UserAdminAction: ActionBuilder[AuthRequest, AnyContent] =
    Authenticated.andThen(PermissionAction(UserAdmin))

  def userAdmin(user: String): Action[AnyContent] = UserAdminAction.async { implicit request =>
    users
      .withName(user)
      .semiflatMap { u =>
        for {
          isOrga <- u.toMaybeOrganization.isDefined
          (projectRoles, orga) <- {
            if (isOrga)
              (Future.successful(Seq.empty), getOrga(user).value).tupled
            else
              (u.projectRoles.all, Future.successful(None)).tupled
          }
          (userData, projects, orgaData) <- (
            getUserData(request, user).value,
            Future.sequence(projectRoles.map(_.project)),
            OrganizationData.of(orga).value
          ).tupled
        } yield {
          val pr = projects.zip(projectRoles)
          Ok(views.users.admin.userAdmin(userData.get, orgaData, pr.toSeq))
        }
      }
      .getOrElse(notFound)
  }

  def updateUser(userName: String): Action[(String, String, String)] =
    UserAdminAction.async(parse.form(forms.UserAdminUpdate)) { implicit request =>
      users
        .withName(userName)
        .map { user =>
          //TODO: Make the form take json directly
          val (thing, action, data) = request.body
          import play.api.libs.json._
          val json = Json.parse(data)

          def updateRoleTable[M <: UserRoleModel](
              modelAccess: ModelAccess[M],
              allowedCategory: RoleCategory,
              ownerType: Role,
              transferOwner: M => Future[M],
              setRoleType: (M, Role) => Future[M],
              setAccepted: (M, Boolean) => Future[M]
          ) = {
            val id = (json \ "id").as[ObjectReference]
            action match {
              case "setRole" =>
                modelAccess.get(id).semiflatMap { role =>
                  val roleType = Role.withValue((json \ "role").as[String])

                  if (roleType == ownerType)
                    transferOwner(role).as(Ok)
                  else if (roleType.category == allowedCategory && roleType.isAssignable)
                    setRoleType(role, roleType).as(Ok)
                  else
                    Future.successful(BadRequest)
                }
              case "setAccepted" =>
                modelAccess
                  .get(id)
                  .semiflatMap(role => setAccepted(role, (json \ "accepted").as[Boolean]).as(Ok))
              case "deleteRole" =>
                modelAccess
                  .get(id)
                  .filter(_.role.isAssignable)
                  .semiflatMap(_.remove().as(Ok))
            }
          }

          def transferOrgOwner(r: OrganizationUserRole) =
            r.organization
              .flatMap(orga => orga.transferOwner(orga.memberships.newMember(r.userId)))
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
                  orga.memberships.roles,
                  RoleCategory.Organization,
                  Role.OrganizationOwner,
                  transferOrgOwner,
                  (r, tpe) => orga.memberships.roles.update(r.copy(role = tpe)),
                  (r, accepted) => orga.memberships.roles.update(r.copy(isAccepted = accepted))
                )
              }
            case "projectRole" =>
              OptionT.liftF(user.toMaybeOrganization.isEmpty).filter(identity).flatMap { _ =>
                updateRoleTable[ProjectUserRole](
                  user.projectRoles,
                  RoleCategory.Project,
                  Role.ProjectOwner,
                  r => r.project.flatMap(p => p.transferOwner(p.memberships.newMember(r.userId))).as(r),
                  (r, tpe) => user.projectRoles.update(r.copy(role = tpe)),
                  (r, accepted) => user.projectRoles.update(r.copy(isAccepted = accepted))
                )
              }
            case _ => OptionT.none[Future, Status]
          }
        }
        .semiflatMap(_.getOrElse(BadRequest))
        .getOrElse(NotFound)
    }

  def showProjectVisibility(): Action[AnyContent] =
    Authenticated.andThen(PermissionAction[AuthRequest](ReviewVisibility)).async { implicit request =>
      (
        runDbProgram(AppQueries.getVisibilityNeedsApproval.to[Vector]),
        runDbProgram(AppQueries.getVisibilityWaitingProject.to[Vector])
      ).mapN { (needsApproval, waitingProject) =>
        val getRoles = Future.traverse(needsApproval) { project =>
          val perms = Visibility.values.map(_.permission)

          request.user.trustIn(project).map2(request.user.globalRoles.all) {
            request.user.can.asMap(_, _)(perms: _*)
          }
        }

        getRoles.map(perms => (needsApproval.zip(perms), waitingProject))
      }.flatten.map { case (needsApproval, waitingProjects) =>
        Ok(views.users.admin.visibility(needsApproval, waitingProjects))
      }
    }
}
