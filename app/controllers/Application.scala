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
  ChannelTable,
  FlagTable,
  LoggedActionViewTable,
  ProjectTableMain,
  ReviewTable,
  TagTable,
  UserTable,
  VersionTable
}
import db.{ModelQuery, ModelService, ObjectReference}
import form.OreForms
import models.admin.Review
import models.project.{Tag, _}
import models.user.role._
import models.user.{LoggedAction, LoggedActionModel, User, UserActionLogger}
import models.viewhelper.OrganizationData
import ore.permission._
import ore.permission.role.{Role, RoleType}
import ore.permission.scope.GlobalScope
import ore.project.{Category, ProjectSortingStrategies}
import ore.user.MembershipDossier
import ore.{OreConfig, OreEnv, Platform, PlatformCategory}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.{html => views}

import cats.data.OptionT
import cats.instances.future._
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
  ): Action[AnyContent] = OreAction.async { implicit request =>
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

    val pageSize = this.config.projects.get[Int]("init-load")
    val pageNum  = page.getOrElse(1)
    val offset   = (pageNum - 1) * pageSize

    val versionIdsOnPlatform =
      TableQuery[TagTable].filter(_.name.toLowerCase.inSetBind(platformNames)).map(_.versionIds.unnest)

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

    def queryProjects: Future[Seq[(Project, User, Version, List[Tag])]] = {
      for {
        projects <- service.runDBIO(projectQuery.result)
        tags     <- Future.sequence(projects.map(_._3.tags))
      } yield {
        projects.zip(tags).map {
          case ((p, u, v), t) =>
            (p, u, v, t)
        }
      }
    }

    queryProjects.map { data =>
      val catList =
        if (categoryList.isEmpty || Category.visible.toSet.equals(categoryList.toSet)) None else Some(categoryList)
      Ok(views.home(data, catList, query.find(_.nonEmpty), pageNum, ordering, pcat, pform))
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

  private def queryReviews(versions: Seq[ObjectReference]) =
    for {
      r <- TableQuery[ReviewTable] if r.versionId.inSetBind(versions)
      u <- TableQuery[UserTable] if r.userId === u.id
    } yield (r, u.name)

  private def queryQueue =
    for {
      (v, u) <- TableQuery[VersionTable].joinLeft(TableQuery[UserTable]).on(_.authorId === _.id)
      c      <- TableQuery[ChannelTable] if v.channelId === c.id && v.isReviewed =!= true && v.isNonReviewed =!= true
      p      <- TableQuery[ProjectTableMain] if v.projectId === p.id && p.visibility =!= (Visibility.SoftDelete: Visibility)
      ou     <- TableQuery[UserTable] if p.userId === ou.id
    } yield (v, p, c, u.map(_.name), ou)

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
      seq <- service.runDBIO(query.result)
      perms <- Future.traverse(seq.map(_._2)) { project =>
        request.user
          .trustIn(project)
          .map(request.user.can.asMap(_)(Visibility.values.map(_.permission): _*))
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

  def showHealth(): Action[AnyContent] = Authenticated.andThen(PermissionAction[AuthRequest](ViewHealth)).async {
    implicit request =>
      val projectTable = TableQuery[ProjectTableMain]
      val query = for {
        noTopicProject    <- projectTable if noTopicProject.topicId.isEmpty || noTopicProject.postId.?.isEmpty
        dirtyTopicProject <- projectTable if dirtyTopicProject.isTopicDirty
        staleProject      <- projectTable
        if staleProject.lastUpdated > new Timestamp(new Date().getTime - this.config.projects.get[Int]("staleAge"))
        notPublicProject <- projectTable if notPublicProject.visibility =!= (Visibility.Public: Visibility)
      } yield (noTopicProject, dirtyTopicProject, staleProject, notPublicProject)

      (
        service.runDBIO(query.result),
        projects.missingFile.flatMap { versions =>
          Future.traverse(versions)(v => v.project.tupleLeft(v))
        }
      ).mapN { (queryRes, missingFileProjects) =>
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
  def removeTrail(path: String) = Action(MovedPermanently(s"/$path"))

  /**
    * Show the activities page for a user
    */
  def showActivities(user: String): Action[AnyContent] =
    Authenticated.andThen(PermissionAction(ReviewProjects)).async { implicit request =>
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
            Future.traverse(seq) {
              case (review, version) =>
                projects.find(_.id === version.projectId).value.tupleLeft(review.asRight[Flag])
            }
          }

          val flags = service.runDBIO(flagQuery.result).flatMap { seq =>
            Future.traverse(seq) { flag =>
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
  def showStats(): Action[AnyContent] = Authenticated.andThen(PermissionAction[AuthRequest](ViewStats)).async {
    implicit request =>
      /**
        * Query to get a count where columnDate is equal to the date
        */
      def last10DaysCountQuery(table: String, columnDate: String): Future[Seq[(String, String)]] = {
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
        : Future[Seq[(String, String)]] = {
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
      ).mapN { (reviews, uploads, totalDownloads, unsafeDownloads, flagsOpen, flagsClosed) =>
        Ok(views.users.admin.stats(reviews, uploads, totalDownloads, unsafeDownloads, flagsOpen, flagsClosed))
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
      service.runDBIO(logQuery.result),
      service.access[LoggedActionModel]().size,
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
          val json       = Json.parse(data)
          val orgDossier = MembershipDossier.organization

          def updateRoleTable[M0 <: RoleModel { type M = M0 }: ModelQuery](
              modelAccess: ModelAccess[M0],
              allowedType: Class[_ <: Role],
              ownerType: RoleType,
              transferOwner: M0 => Future[M0],
              setRoleType: (M0, RoleType) => Future[M0],
              setAccepted: (M0, Boolean) => Future[M0]
          ) = {
            val id = (json \ "id").as[ObjectReference]
            action match {
              case "setRole" =>
                modelAccess.get(id).semiflatMap { role =>
                  val roleType = RoleType.withValue((json \ "role").as[String])

                  if (roleType == ownerType)
                    transferOwner(role).as(Ok)
                  else if (roleType.roleClass == allowedType && roleType.isAssignable)
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
                  .filter(_.roleType.isAssignable)
                  .semiflatMap(service.delete(_).as(Ok))
            }
          }

          def transferOrgOwner(r: OrganizationRole) =
            r.organization
              .flatMap(orga => orga.transferOwner(orgDossier.newMember(orga, r.userId)))
              .as(r)

          thing match {
            case "orgRole" =>
              OptionT.liftF(user.toMaybeOrganization.isEmpty).filter(identity).flatMap { _ =>
                updateRoleTable[OrganizationRole](
                  user.organizationRoles,
                  classOf[OrganizationRole],
                  RoleType.OrganizationOwner,
                  transferOrgOwner,
                  (r, tpe) => user.organizationRoles.update(r.copy(roleType = tpe)),
                  (r, accepted) => user.organizationRoles.update(r.copy(isAccepted = accepted))
                )
              }
            case "memberRole" =>
              user.toMaybeOrganization.flatMap { orga =>
                updateRoleTable[OrganizationRole](
                  orgDossier.roles(orga),
                  classOf[OrganizationRole],
                  RoleType.OrganizationOwner,
                  transferOrgOwner,
                  (r, tpe) => orgDossier.roles(orga).update(r.copy(roleType = tpe)),
                  (r, accepted) => orgDossier.roles(orga).update(r.copy(isAccepted = accepted))
                )
              }
            case "projectRole" =>
              OptionT.liftF(user.toMaybeOrganization.isEmpty).filter(identity).flatMap { _ =>
                updateRoleTable[ProjectRole](
                  user.projectRoles,
                  classOf[ProjectRole],
                  RoleType.ProjectOwner,
                  r => r.project.flatMap(p => p.transferOwner(p.memberships.newMember(p, r.userId))).as(r),
                  (r, tpe) => user.projectRoles.update(r.copy(roleType = tpe)),
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
      for {
        (projectApprovals, projectChanges) <- (
          service.collect[Project](
            _.visibility === (Visibility.NeedsApproval: Visibility),
            ProjectSortingStrategies.Default.fn,
          ),
          service.collect[Project](
            _.visibility === (Visibility.NeedsChanges: Visibility),
            ProjectSortingStrategies.Default.fn,
          )
        ).tupled
        (lastChangeRequests, projectChangeRequests, lastVisibilityChanges, projectVisibilityChanges) <- (
          Future.traverse(projectApprovals)(_.lastChangeRequest.value),
          Future.traverse(projectChanges)(_.lastChangeRequest.value),
          Future.traverse(projectApprovals)(_.lastVisibilityChange.value),
          Future.traverse(projectChanges)(_.lastVisibilityChange.value)
        ).tupled

        (perms, lastChangeRequesters, lastVisibilityChangers, projectVisibilityChangers) <- (
          Future.traverse(projectApprovals) { project =>
            val perms = Visibility.values.map(_.permission).map { perm =>
              (request.user.can(perm) in project).map(value => (perm, value))
            }
            Future.sequence(perms).map(_.toMap)
          },
          Future.traverse(lastChangeRequests) {
            case None      => Future.successful(None)
            case Some(lcr) => lcr.created.value
          },
          Future.traverse(lastVisibilityChanges) {
            case None      => Future.successful(None)
            case Some(lcr) => lcr.created.value
          },
          Future.traverse(projectVisibilityChanges) {
            case None      => Future.successful(None)
            case Some(lcr) => lcr.created.value
          }
        ).tupled
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
