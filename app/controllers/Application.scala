package controllers

import java.sql.Timestamp
import java.time.Instant
import java.util.Date
import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}

import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{
  ChannelTable,
  FlagTable,
  LoggedActionViewTable,
  ProjectTableMain,
  ReviewTable,
  UserTable,
  VersionTable,
  VersionTagTable
}
import db.{DbRef, ModelQuery, ModelService}
import form.OreForms
import models.admin.Review
import models.project.{VersionTag, _}
import models.user.role._
import models.user.{LoggedAction, LoggedActionModel, User, UserActionLogger}
import models.viewhelper.OrganizationData
import ore.permission._
import ore.permission.role.{Role, RoleCategory}
import ore.permission.scope.GlobalScope
import ore.project.{Category, ProjectSortingStrategies}
import ore.user.MembershipDossier
import ore.{OreConfig, OreEnv, Platform, PlatformCategory}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.syntax._
import views.{html => views}

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

  /**
    * Show external link warning page.
    *
    * @return External link page
    */
  def linkOut(remoteUrl: String): Action[AnyContent] = OreAction { implicit request =>
    Ok(views.linkout(remoteUrl))
  }

  private val queryProjectRV = {
    val tableProject = TableQuery[ProjectTableMain]
    val tableVersion = TableQuery[VersionTable]
    val userTable    = TableQuery[UserTable]

    for {
      p <- tableProject
      v <- tableVersion if p.recommendedVersionId === v.id
      u <- userTable if p.userId === u.id
    } yield (p, u, v)
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
  ): Action[AnyContent] = OreAction.asyncF { implicit request =>
    // Get categories and sorting strategy

    val canHideProjects = request.headerData.globalPerm(HideProjects)
    val currentUserId   = request.headerData.currentUser.map(_.id.value).getOrElse(-1L)

    val ordering = sort.flatMap(ProjectSortingStrategies.withId).getOrElse(ProjectSortingStrategies.Default)
    val pcat     = platformCategory.flatMap(p => PlatformCategory.getPlatformCategories.find(_.name.equalsIgnoreCase(p)))
    val pform    = platform.flatMap(p => Platform.values.find(_.name.equalsIgnoreCase(p)))

    // get the categories being queried
    val categoryPlatformNames: List[String] = pcat.toList.flatMap(_.getPlatforms.map(_.name))
    val platformNames: List[String]         = (pform.map(_.name).toList ::: categoryPlatformNames).map(_.toLowerCase)

    val categoryList: Seq[Category] = categories.fold(Category.fromString(""))(s => Category.fromString(s)).toSeq
    val q                           = query.fold("%")(qStr => s"%${qStr.toLowerCase}%")

    val pageSize = this.config.ore.projects.initLoad
    val pageNum  = page.getOrElse(1)
    val offset   = (pageNum - 1) * pageSize

    val versionIdsOnPlatform =
      TableQuery[VersionTagTable].filter(_.name.toLowerCase.inSetBind(platformNames)).map(_.versionId)

    //noinspection ScalaUnnecessaryParentheses
    val dbQueryRaw = for {
      (project, user, version) <- queryProjectRV
      if canHideProjects.bind ||
        (project.visibility === (Visibility.Public: Visibility)) ||
        (project.visibility === (Visibility.New: Visibility)) ||
        ((project.userId === currentUserId) && (project.visibility =!= (Visibility.SoftDelete: Visibility)))
      if (if (platformNames.isEmpty) true.bind else project.recommendedVersionId in versionIdsOnPlatform)
      if (if (categoryList.isEmpty) true.bind else project.category.inSetBind(categoryList))
      if (project.name.toLowerCase.like(q)) ||
        (project.description.toLowerCase.like(q)) ||
        (project.ownerName.toLowerCase.like(q)) ||
        (project.pluginId.toLowerCase.like(q))
    } yield (project, user, version)
    val projectQuery = dbQueryRaw
      .sortBy(t => ordering.fn(t._1))
      .drop(offset)
      .take(pageSize)

    def queryProjects: IO[Seq[(Project, User, Version, List[VersionTag])]] = {
      for {
        projects <- service.runDBIO(projectQuery.result)
        tags     <- projects.map(_._3.tags).toVector.parSequence
      } yield {
        projects.zip(tags).map {
          case ((p, u, v), t) => (p, u, v, t)
        }
      }
    }

    queryProjects.map { data =>
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
    Authenticated.andThen(PermissionAction(ReviewProjects)).asyncF { implicit request =>
      // TODO: Pages
      val data = this.service
        .runDBIO(queryQueue.result)
        .flatMap { list =>
          service
            .runDBIO(queryReviews(list.map(_._1.id.value)).result)
            .map(_.groupBy(_._1.versionId))
            .tupleLeft(list)
        }
        .map {
          case (list, reviewsByVersion) =>
            val reviewData = reviewsByVersion.mapValues { reviews =>
              val sortedReviews = reviews.sorted(Review.ordering)
              sortedReviews
                .find { case (review, _) => review.endedAt.isEmpty }
                .map { case (r, a) => (r, true, a) } // Unfinished Review
                .orElse(sortedReviews.headOption.map { case (r, a) => (r, false, a) }) // any review
            }

            list.map {
              case (v, p, c, a, u) => (v, p, c, a, u, reviewData.getOrElse(v.id.value, None))
            }
        }
      data.map { list =>
        val lists = list.partition(_._6.isEmpty)
        val reviewList = lists._2.map {
          case (v, p, c, a, _, r) => (p, v, c, a, r.get)
        }
        val unReviewList = lists._1.map {
          case (v, p, c, a, u, _) => (p, v, c, a, u)
        }

        Ok(views.users.admin.queue(reviewList, unReviewList))
      }

    }

  private def queryReviews(versions: Seq[DbRef[Version]]) =
    for {
      r <- TableQuery[ReviewTable] if r.versionId.inSetBind(versions)
      u <- TableQuery[UserTable] if r.userId === u.id
    } yield (r, u.name)

  private def queryQueue =
    for {
      (v, u) <- TableQuery[VersionTable].joinLeft(TableQuery[UserTable]).on(_.authorId === _.id)
      c      <- TableQuery[ChannelTable] if v.channelId === c.id && v.reviewStatus === (ReviewState.Unreviewed: ReviewState)
      p      <- TableQuery[ProjectTableMain] if p.id === v.projectId && p.visibility =!= (Visibility.SoftDelete: Visibility)
      ou     <- TableQuery[UserTable] if p.userId === ou.id
    } yield (v, p, c, u.map(_.name), ou)

  /**
    * Shows the overview page for flags.
    *
    * @return Flag overview
    */
  def showFlags(): Action[AnyContent] = FlagAction.asyncF { implicit request =>
    val query = for {
      flag    <- TableQuery[FlagTable] if !flag.isResolved
      project <- TableQuery[ProjectTableMain] if flag.projectId === project.id
      user    <- TableQuery[UserTable] if flag.userId === user.id
    } yield (flag, project, user)

    for {
      seq <- service.runDBIO(query.result)
      perms <- seq.map(_._2).toVector.parTraverse { project =>
        request.user
          .trustIn(project)
          .map2(request.user.globalRoles.allFromParent(request.user)) { (t, r) =>
            request.user.can.asMap(t, r.toSet)(Visibility.values.map(_.permission): _*)
          }
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
  def setFlagResolved(flagId: DbRef[Flag], resolved: Boolean): Action[AnyContent] =
    FlagAction.asyncF { implicit request =>
      this.service
        .access[Flag]()
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

  def showHealth(): Action[AnyContent] = Authenticated.andThen(PermissionAction[AuthRequest](ViewHealth)).asyncF {
    implicit request =>
      val projectTable = TableQuery[ProjectTableMain]
      val query = for {
        noTopicProject    <- projectTable if noTopicProject.topicId.isEmpty || noTopicProject.postId.?.isEmpty
        dirtyTopicProject <- projectTable if dirtyTopicProject.isTopicDirty
        staleProject      <- projectTable
        if staleProject.lastUpdated > new Timestamp(new Date().getTime - this.config.ore.projects.staleAge.toMillis)
        notPublicProject <- projectTable if notPublicProject.visibility =!= (Visibility.Public: Visibility)
      } yield (noTopicProject, dirtyTopicProject, staleProject, notPublicProject)

      (
        service.runDBIO(query.result),
        projects.missingFile.flatMap { versions =>
          versions.toVector.parTraverse(v => v.project.tupleLeft(v))
        }
      ).parMapN { (queryRes, missingFileProjects) =>
        val (queryRes1, queryRes2)                = queryRes.map(t => (t._1 -> t._2) -> (t._3 -> t._4)).unzip
        val (noTopicProjects, topicDirtyProjects) = queryRes1.unzip
        val (staleProjects, notPublic)            = queryRes2.unzip
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
      users
        .withName(user)
        .semiflatMap { u =>
          val id = u.id.value

          val reviewQuery = for {
            review  <- TableQuery[ReviewTable] if review.userId === id
            version <- TableQuery[VersionTable] if version.id === review.versionId
          } yield (review, version)

          val flagQuery = TableQuery[FlagTable].withFilter(flag => flag.resolvedBy === id).take(20)

          val reviews = service.runDBIO(reviewQuery.take(20).result).flatMap { seq =>
            seq.toVector.parTraverse {
              case (review, version) =>
                projects.find(_.id === version.projectId).value.tupleLeft(review.asRight[Flag])
            }
          }

          val flags = service.runDBIO(flagQuery.result).flatMap { seq =>
            seq.toVector.parTraverse { flag =>
              projects.find(_.id === flag.projectId).value.tupleLeft(flag.asLeft[Review])
            }
          }

          val activities = reviews.map2(flags)(_ ++ _).map(_.sortWith(sortActivities))

          activities.map(a => Ok(views.users.admin.activity(u, a)))
        }
        .getOrElse(NotFound)
    }

  /**
    * Compares 2 activities (supply a [[Review]] or [[Flag]]) to decide which came first
    * @param o1 Review / Flag
    * @param o2 Review / Flag
    * @return Boolean
    */
  def sortActivities(
      o1: (Either[Flag, Review], Option[Project]),
      o2: (Either[Flag, Review], Option[Project])
  ): Boolean = {
    val o1Time: Long = o1 match {
      case (Right(review), _) => review.endedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
      case _                  => 0
    }
    val o2Time: Long = o2 match {
      case (Left(flag), _) => flag.resolvedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
      case _               => 0
    }
    o1Time > o2Time
  }

  /**
    * Show stats
    * @return
    */
  def showStats(): Action[AnyContent] = Authenticated.andThen(PermissionAction[AuthRequest](ViewStats)).asyncF {
    implicit request =>
      /**
        * Query to get a count where columnDate is equal to the date
        */
      def last10DaysCountQuery(table: String, columnDate: String): IO[Seq[(String, String)]] = {
        this.service.runDBIO(sql"""
        SELECT
          (SELECT COUNT(*) FROM #$table WHERE CAST(#$columnDate AS DATE) = day),
          CAST(day AS DATE)
        FROM (SELECT current_date - (INTERVAL '1 day' * generate_series(0, 9)) AS day) dates
        ORDER BY day ASC""".as[(String, String)])
      }

      /**
        * Query to get a count of open record in last 10 days
        */
      def last10DaysTotalOpen(table: String, columnStartDate: String, columnEndDate: String)
        : IO[Seq[(String, String)]] = {
        this.service.runDBIO(sql"""
        SELECT
          (SELECT COUNT(*) FROM #$table WHERE CAST(#$columnStartDate AS DATE) <= date.when AND (CAST(#$columnEndDate AS DATE) >= date.when OR #$columnEndDate IS NULL)) count,
          CAST(date.when AS DATE) AS days
        FROM (SELECT current_date - (INTERVAL '1 day' * generate_series(0, 9)) AS when) date
        ORDER BY days ASC""".as[(String, String)])
      }

      (
        last10DaysCountQuery("project_version_reviews", "ended_at"),
        last10DaysCountQuery("project_versions", "created_at"),
        last10DaysCountQuery("project_version_downloads", "created_at"),
        last10DaysCountQuery("project_version_unsafe_downloads", "created_at"),
        last10DaysTotalOpen("project_flags", "created_at", "resolved_at"),
        last10DaysCountQuery("project_flags", "resolved_at")
      ).parMapN { (reviews, uploads, totalDownloads, unsafeDownloads, flagsOpen, flagsClosed) =>
        Ok(views.users.admin.stats(reviews, uploads, totalDownloads, unsafeDownloads, flagsOpen, flagsClosed))
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

    val default = LiteralColumn(true)

    val logQuery = TableQuery[LoggedActionViewTable[Any]]
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
      service.runDBIO(logQuery.result),
      service.access[LoggedActionModel[Any]]().size,
      request.currentUser.get.can(ViewIp).in(GlobalScope)
    ).parMapN { (actions, size, canViewIP) =>
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
      for {
        t1 <- (
          service
            .collect[Project](
              _.visibility === (Visibility.NeedsApproval: Visibility),
              ProjectSortingStrategies.Default.fn,
            )
            .map(_.toVector),
          service
            .collect[Project](
              _.visibility === (Visibility.NeedsChanges: Visibility),
              ProjectSortingStrategies.Default.fn,
            )
            .map(_.toVector)
        ).parTupled
        (projectApprovals, projectChanges) = t1
        t2 <- (
          projectApprovals.parTraverse(_.lastChangeRequest.value),
          projectChanges.parTraverse(_.lastChangeRequest.value),
          projectApprovals.parTraverse(_.lastVisibilityChange.value),
          projectChanges.parTraverse(_.lastVisibilityChange.value)
        ).parTupled
        (lastChangeRequests, projectChangeRequests, lastVisibilityChanges, projectVisibilityChanges) = t2
        t3 <- (
          projectApprovals.parTraverse { project =>
            val perms = Visibility.values.map(_.permission).map { perm =>
              (request.user.can(perm) in project).map(value => (perm, value))
            }
            perms.toVector.parSequence.map(_.toMap)
          },
          lastChangeRequests.parTraverse {
            case None      => IO.pure(None)
            case Some(lcr) => lcr.created.value
          },
          lastVisibilityChanges.parTraverse {
            case None      => IO.pure(None)
            case Some(lcr) => lcr.created.value
          },
          projectVisibilityChanges.parTraverse {
            case None      => IO.pure(None)
            case Some(lcr) => lcr.created.value
          }
        ).parTupled
        (perms, lastChangeRequesters, lastVisibilityChangers, projectVisibilityChangers) = t3
      } yield {
        val needsApproval = projectApprovals
          .zip(perms)
          .zip(lastChangeRequests)
          .zip(lastChangeRequesters)
          .zip(lastVisibilityChanges)
          .zip(lastVisibilityChangers)
          .map {
            case (((((a, b), c), d), e), f) =>
              (a, b, c, d.fold("Unknown")(_.name), e, f.fold("Unknown")(_.name))
          }
        val waitingProjects =
          projectChanges.zip(projectChangeRequests).zip(projectVisibilityChanges).zip(projectVisibilityChangers).map {
            case (((a, b), c), d) =>
              (a, b, c, d.fold("Unknown")(_.name))
          }

        Ok(views.users.admin.visibility(needsApproval, waitingProjects))
      }
    }
}
