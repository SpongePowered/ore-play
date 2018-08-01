package controllers

import java.sql.Timestamp
import java.time.Instant

import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.schema.ProjectSchema
import db.{ModelFilter, ModelService}
import form.OreForms
import javax.inject.Inject
import models.admin.Review
import models.project.{Tag, _}
import models.user.role._
import models.user.{LoggedAction, LoggedActionModel, User, UserActionLogger}
import models.viewhelper.{HeaderData, OrganizationData, ScopedOrganizationData}
import ore.Platforms.Platform
import ore.permission._
import ore.permission.role.{Role, RoleType}
import ore.permission.scope.GlobalScope
import ore.project.Categories.Category
import ore.project.{Categories, ProjectSortingStrategies}
import ore.{OreConfig, OreEnv, PlatformCategory, Platforms}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, ActionBuilder, AnyContent}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import util.DataHelper
import util.functional.OptionT
import util.instances.future._
import util.syntax._
import views.{html => views}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Main entry point for application.
  */
final class Application @Inject()(data: DataHelper,
                                  forms: OreForms)(
    implicit val ec: ExecutionContext,
    auth: SpongeAuthApi,
    bakery: Bakery,
    sso: SingleSignOnConsumer,
    messagesApi: MessagesApi,
    env: OreEnv,
    config: OreConfig,
    cache: AsyncCacheApi,
    service: ModelService
) extends OreBaseController {

  private def FlagAction = Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)

  /**
    * Show external link warning page.
    *
    * @return External link page
    */
  def linkOut(remoteUrl: String) = OreAction { implicit request =>
    implicit val headerData: HeaderData = request.data
    Ok(views.linkout(remoteUrl))
  }

  private def queryProjectRV = {
    val tableProject = TableQuery[ProjectTableMain]
    val tableVersion = TableQuery[VersionTable]
    val userTable = TableQuery[UserTable]

    for {
      p <- tableProject
      v <- tableVersion if p.recommendedVersionId === v.id
      u <- userTable if p.userId === u.id
    } yield {
      (p, u, v)
    }
  }

  /**
    * Display the home page.
    *
    * @return Home page
    */
  def showHome(categories: Option[String],
               query: Option[String],
               sort: Option[Int],
               page: Option[Int],
               platformCategory: Option[String],
               platform: Option[String]): Action[AnyContent] = OreAction async { implicit request =>
    // Get categories and sorting strategy

    val canHideProjects = request.data.globalPerm(HideProjects)
    val currentUserId = request.data.currentUser.map(_.id.value).getOrElse(-1)

    val ordering = sort.flatMap(ProjectSortingStrategies.withId).getOrElse(ProjectSortingStrategies.Default)
    val pcat = platformCategory.flatMap(p => PlatformCategory.getPlatformCategories.find(_.name.equalsIgnoreCase(p)))
    val pform = platform.flatMap(p => Platforms.values.find(_.name.equalsIgnoreCase(p)).map(_.asInstanceOf[Platform]))

    // get the categories being queried
    val categoryPlatformNames: List[String] = pcat.toList.flatMap(_.getPlatforms.map(_.name))
    val platformNames: List[String] = pform.map(_.name).toList ::: categoryPlatformNames map(_.toLowerCase)

    val categoryList: Seq[Category] = categories.fold(Categories.fromString(""))(s => Categories.fromString(s)).toSeq
    val q = query.fold("%")(qStr => s"%${qStr.toLowerCase}%")

    val pageSize = this.config.projects.get[Int]("init-load")
    val p = page.getOrElse(1)
    val offset = (p - 1) * pageSize

    for {
      tags <- service.DB.db.run(TableQuery[TagTable].filter(_.name.toLowerCase inSetBind platformNames).result)
      result <- {
        val versionIdsOnPlatform = tags.flatMap(_.versionIds.asInstanceOf[List[Long]]).map(_.toInt)

        val projectQuery = queryProjectRV.filter { case (p, u, v) =>
          (LiteralColumn(true) === canHideProjects) ||
            (p.visibility === VisibilityTypes.Public) ||
            (p.visibility === VisibilityTypes.New) ||
            ((p.userId === currentUserId) && (p.visibility =!= VisibilityTypes.SoftDelete))
        } filter { case (p, u, v) =>
          (LiteralColumn(0) === categoryList.length) || (p.category inSetBind categoryList)
        } filter { case (p, u, v) =>
          if (platformNames.isEmpty) LiteralColumn(true)
          else p.recommendedVersionId inSet versionIdsOnPlatform
        } filter { case (p, u, v) =>
          (p.name.toLowerCase like q) ||
            (p.description.toLowerCase like q) ||
            (p.ownerName.toLowerCase like q) ||
            (p.pluginId.toLowerCase like q)
        } sortBy { case (p, u, v) =>
          ordering.fn(p)
        } drop offset take pageSize

        def queryProjects(): Future[Seq[(Project, User, Version, List[Tag])]] = {
          for {
            projects <- service.DB.db.run(projectQuery.result)
            tags <- Future.sequence(projects.map(_._3.tags))
          } yield {
            projects zip tags map { case ((p, u, v), t) =>
              (p, u, v, t)
            }
          }
        }

        queryProjects() map { data =>
          val catList = if (categoryList.isEmpty || Categories.visible.toSet.equals(categoryList.toSet)) None else Some(categoryList)
          Ok(views.home(data, catList, query.find(_.nonEmpty), p, ordering, pcat, pform))
        }
      }
    } yield {
      result
    }
  }

  def showQueue(): Action[AnyContent] = showQueueWithPage(0)

  /**
    * Shows the moderation queue for unreviewed versions.
    *
    * @return View of unreviewed versions.
    */
  def showQueueWithPage(page: Int): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>
    // TODO: Pages
    val limit = 50
    val offset = page * limit

    val data = this.service.DB.db.run(queryQueue.result).flatMap { list =>
      service.DB.db.run(queryReviews(list.map(_._1.id.value)).result).map { reviewList =>
        reviewList.groupBy(_._1.versionId)
      } map { reviewsByVersion =>
        (list, reviewsByVersion)
      }
    } map { case (list, reviewsByVersion) =>
      val reviewData = reviewsByVersion.mapValues { reviews =>

        reviews.filter { case (review, _) =>
          review.endedAt.isEmpty
        }.sorted(Review.ordering).headOption.map { case (r, a) =>
          (r, true, a) // Unfinished Review
        } orElse reviews.sorted(Review.ordering).headOption.map { case (r, a) =>
          (r, false, a) // any review
        }
      }

      list.map { case (v, p, c, a, u) =>
        (v, p, c, a, u, reviewData.getOrElse(v.id.value, None))
      }
    }
    data map { list =>
      val lists = list.partition(_._6.isEmpty)
      val reviewList = lists._2.map { case (v, p, c, a, _, r) =>
        (p, v, c, a, r.get)
      }
      val unReviewList = lists._1.map { case (v, p, c, a, u, _) =>
        (p, v, c, a, u)
      }

      Ok(views.users.admin.queue(reviewList, unReviewList))
    }

  }

  private def queryReviews(versions: Seq[Int]) = {

    val reviewsTable = TableQuery[ReviewTable]
    val userTable = TableQuery[UserTable]
    for {
      r <- reviewsTable if r.versionId inSetBind versions
      u <- userTable if r.userId === u.id
    } yield {
      (r, u.name)
    }

  }

  private def queryQueue = {
    val versionTable = TableQuery[VersionTable]
    val channelTable = TableQuery[ChannelTable]
    val projectTable = TableQuery[ProjectTableMain]
    val userTable = TableQuery[UserTable]

    for {
      (v, u) <- versionTable joinLeft userTable on (_.authorId === _.id)
      c <- channelTable if v.channelId === c.id && v.isReviewed =!= true && v.isNonReviewed =!= true
      p <- projectTable if v.projectId === p.id && p.visibility =!= VisibilityTypes.SoftDelete
      ou <- userTable if p.userId === ou.id
    } yield {
      (v, p, c, u.map(_.name), ou)
    }

  }

  /**
    * Shows the overview page for flags.
    *
    * @return Flag overview
    */
  def showFlags(): Action[AnyContent] = FlagAction.async { implicit request =>
    for {
      flags <- this.service.access[Flag](classOf[Flag]).filterNot(_.isResolved)
      (users, projects) <- (Future.sequence(flags.map(_.user)), Future.sequence(flags.map(_.project))).parTupled
      perms <- Future.sequence(projects.map { project =>
        val perms = VisibilityTypes.values.map(_.permission).map { perm =>
          request.user can perm in project map (value => (perm, value))
        }
        Future.sequence(perms).map(_.toMap)
      })
    } yield {
      val data = flags zip users zip projects zip perms map { case ((((flag, user), project), perm)) =>
        (flag, user, project, perm)
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
  def setFlagResolved(flagId: Int, resolved: Boolean): Action[AnyContent] = FlagAction.async { implicit request =>
    this.service.access[Flag](classOf[Flag]).get(flagId).semiFlatMap { flag =>
      for {
        user        <- users.current.value
        _           <- flag.markResolved(resolved, user)
        flagCreator <- flag.user
        _           <- UserActionLogger.log(
          request,
          LoggedAction.ProjectFlagResolved,
          flag.projectId,
          s"Flag Resolved by ${user.fold("unknown")(_.name)}",
          s"Flagged by ${flagCreator.name}"
        )
      } yield Ok
    }.getOrElse(NotFound)
  }

  def showHealth(): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](ViewHealth)) async { implicit request =>
    (
      projects.filter(p => p.topicId === -1 || p.postId === -1),
      projects.filter(_.isTopicDirty),
      projects.stale,
      projects.filterNot(_.visibility === VisibilityTypes.Public),
      projects.missingFile.flatMap { v =>
        Future.sequence(v.map { v => v.project.map(p => (v, p)) })
      }
    ).parMapN { (noTopicProjects, topicDirtyProjects, staleProjects, notPublic, missingFileProjects) =>
      Ok(views.users.admin.health(noTopicProjects, topicDirtyProjects, staleProjects, notPublic, missingFileProjects))
    }
  }

  /**
    * Removes a trailing slash from a route.
    *
    * @param path Path with trailing slash
    * @return     Redirect to proper route
    */
  def removeTrail(path: String) = Action(MovedPermanently('/' + path))

  /**
    * Helper route to reset Ore.
    */
  def reset(): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](ResetOre)) { implicit request =>
    this.config.checkDebug()
    this.data.reset()
    cache.removeAll()
    Redirect(ShowHome).withNewSession
  }

  /**
    * Fills Ore with some dummy data.
    *
    * @return Redirect home
    */
  def seed(users: Int, projects: Int, versions: Int, channels: Int): Action[AnyContent] = {
    (Authenticated andThen PermissionAction[AuthRequest](SeedOre)) { implicit request =>
      this.config.checkDebug()
      this.data.seed(users, projects, versions, channels)
      cache.removeAll()
      Redirect(ShowHome).withNewSession
    }
  }

  /**
    * Performs miscellaneous migration actions for use in deployment.
    *
    * @return Redirect home
    */
  def migrate(): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](MigrateOre)) { implicit request =>
    this.data.migrate()
    Redirect(ShowHome)
  }

  /**
    * Show the activities page for a user
    */
  def showActivities(user: String): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) async { implicit request =>
    users.withName(user).semiFlatMap { u =>
      val id = u.id.value
      val reviews = this.service.access[Review](classOf[Review])
        .filter(_.userId === id)
        .map(_.take(20).map { review => review ->
          this.service.access[Version](classOf[Version]).filter(_.id === review.versionId).flatMap { version =>
            this.projects.find(_.id === version.head.projectId).value
          }
        })
      val flags = this.service.access[Flag](classOf[Flag])
        .filter(_.resolvedBy === id)
        .map(_.take(20).map(flag => flag -> this.projects.find(_.id === flag.projectId).value))

      val allActivities = reviews.flatMap(r => flags.map(_ ++ r))

      val activities = allActivities.flatMap(Future.traverse(_) {
        case (k, fv) => fv.map(k -> _)
      }.map(_.sortWith(sortActivities)))
      activities.map(a => Ok(views.users.admin.activity(u, a)))
    }.getOrElse(NotFound)
  }

  /**
    * Compares 2 activities (supply a [[Review]] or [[Flag]]) to decide which came first
    * @param o1 Review / Flag
    * @param o2 Review / Flag
    * @return Boolean
    */
  def sortActivities(o1: Object, o2: Object): Boolean = {
    val o1Time: Long = o1 match {
      case review: Review => review.endedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
      case _ => 0
    }
    val o2Time: Long = o2 match {
      case flag: Flag => flag.resolvedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
      case _ => 0
    }
    o1Time > o2Time
  }
  /**
    * Show stats
    * @return
    */
  def showStats(): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](ViewStats)).async { implicit request =>

    /**
      * Query to get a count where columnDate is equal to the date
      */
    def last10DaysCountQuery(table: String, columnDate: String): Future[Seq[(String,String)]] = {
      this.service.DB.db.run(sql"""
        SELECT
          (SELECT COUNT(*) FROM #$table WHERE CAST(#$columnDate AS DATE) = day),
          CAST(day AS DATE)
        FROM (SELECT current_date - (INTERVAL '1 day' * generate_series(0, 9)) AS day) dates
        ORDER BY day ASC""".as[(String, String)])
    }

    /**
      * Query to get a count of open record in last 10 days
      */
    def last10DaysTotalOpen(table: String, columnStartDate: String, columnEndDate: String): Future[Seq[(String,String)]] = {
      this.service.DB.db.run(sql"""
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

  def showLog(oPage: Option[Int], userFilter: Option[Int], projectFilter: Option[Int], versionFilter: Option[Int], pageFilter: Option[Int],
              actionFilter: Option[Int], subjectFilter: Option[Int]): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](ViewLogs)).async { implicit request =>
    val pageSize = 50
    val page = oPage.getOrElse(1)
    val offset = (page - 1) * pageSize

    val default = LiteralColumn(true)

    val logQuery = queryLog.filter { case (action) =>
      (action.userId === userFilter).getOrElse(default) &&
      (action.filterProject === projectFilter).getOrElse(default) &&
      (action.filterVersion === versionFilter).getOrElse(default) &&
      (action.filterPage === pageFilter).getOrElse(default) &&
      (action.filterAction === actionFilter).getOrElse(default) &&
      (action.filterSubject === subjectFilter).getOrElse(default)
    }.sortBy { case (action) =>
      action.id.desc
    }.drop(offset).take(pageSize)

    (
      service.DB.db.run(logQuery.result),
      service.access[LoggedActionModel](classOf[LoggedActionModel]).size,
      request.currentUser.get can ViewIp in GlobalScope
    ).parMapN { (actions, size, canViewIP) =>
      Ok(views.users.admin.log(actions, pageSize, offset, page, size, userFilter, projectFilter, versionFilter, pageFilter, actionFilter, subjectFilter, canViewIP))
    }
  }

  private def queryLog = {
    val tableLoggedAction = TableQuery[LoggedActionViewTable]

    for {
      (action) <- tableLoggedAction
    } yield {
      (action)
    }
  }

  def UserAdminAction: ActionBuilder[AuthRequest, AnyContent] = Authenticated andThen PermissionAction[AuthRequest](UserAdmin)

  def userAdmin(user: String): Action[AnyContent] = UserAdminAction.async { implicit request =>
    users.withName(user).semiFlatMap { u =>
      for {
        isOrga <- u.toMaybeOrganization.isDefined
        (projectRoles, orga) <- {
          if (isOrga)
            (Future.successful(Seq.empty), getOrga(request, user).value).parTupled
          else
            (u.projectRoles.all, Future.successful(None)).parTupled
        }
        (userData, projects, orgaData, scopedOrgaData) <- (
          getUserData(request, user).value,
          Future.sequence(projectRoles.map(_.project)),
          OrganizationData.of(orga).value,
          ScopedOrganizationData.of(Some(request.user), orga).value
        ).parTupled
      } yield {
        val pr = projects zip projectRoles
        Ok(views.users.admin.userAdmin(userData.get, orgaData, pr.toSeq))
      }
    }.getOrElse(notFound)
  }

  def updateUser(userName: String): Action[AnyContent] = UserAdminAction.async { implicit request =>
    users.withName(userName).map { user =>
      bindFormOptionT[Future](this.forms.UserAdminUpdate).flatMap { case (thing, action, data) =>
        import play.api.libs.json._
        val json = Json.parse(data)

        def updateRoleTable[M <: RoleModel](
          modelAccess: ModelAccess[M],
          allowedType: Class[_ <: Role],
          ownerType: RoleType,
          transferOwner: M => Future[M],
          setRoleType: (M, RoleType) => Future[M],
          setAccepted: (M, Boolean) => Future[M]
        ) = {
          val id = (json \ "id").as[Int]
          action match {
            case "setRole" => modelAccess.get(id).semiFlatMap { role =>
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
                .semiFlatMap(role => setAccepted(role, (json \ "accepted").as[Boolean]).as(Ok))
            case "deleteRole" =>
              modelAccess
                .get(id)
                .filter(_.roleType.isAssignable)
                .semiFlatMap(_.remove().as(Ok))
          }
        }

        def transferOrgOwner(r: OrganizationRole) = {
          r.organization
            .flatMap(orga => orga.transferOwner(orga.memberships.newMember(r.userId)))
            .as(r)
        }

        val isOrga = OptionT.liftF(user.toMaybeOrganization.isDefined)
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
                orga.memberships.roles,
                classOf[OrganizationRole],
                RoleType.OrganizationOwner,
                transferOrgOwner,
                (r, tpe) => orga.memberships.roles.update(r.copy(roleType = tpe)),
                (r, accepted) => orga.memberships.roles.update(r.copy(isAccepted = accepted))
              )
            }
          case "projectRole" =>
            OptionT.liftF(user.toMaybeOrganization.isEmpty).filter(identity).flatMap { _ =>
              updateRoleTable[ProjectRole](
                user.projectRoles,
                classOf[ProjectRole],
                RoleType.ProjectOwner,
                r => r.project.flatMap(p => p.transferOwner(p.memberships.newMember(r.userId))).as(r),
                (r, tpe) => user.projectRoles.update(r.copy(roleType = tpe)),
                (r, accepted) => user.projectRoles.update(r.copy(isAccepted = accepted))
              )
            }
          case _ => OptionT.none[Future, Status]
        }
      }
    }.semiFlatMap(_.getOrElse(BadRequest)).getOrElse(NotFound)
  }

  /**
    *
    * @return Show page
    */
  def showProjectVisibility(): Action[AnyContent] = (Authenticated andThen PermissionAction[AuthRequest](ReviewVisibility)) async { implicit request =>
    val projectSchema = this.service.getSchema(classOf[ProjectSchema])

    for {
      (projectApprovals, projectChanges) <- (
        projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsApproval).fn, ProjectSortingStrategies.Default, -1, 0),
        projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsChanges).fn, ProjectSortingStrategies.Default, -1, 0)
      ).parTupled
      (lastChangeRequests, lastVisibilityChanges, projectVisibilityChanges) <- (
        Future.sequence(projectApprovals.map(_.lastChangeRequest.value)),
        Future.sequence(projectApprovals.map(_.lastVisibilityChange.value)),
        Future.sequence(projectChanges.map(_.lastVisibilityChange.value))
      ).parTupled

      (perms, lastChangeRequesters, lastVisibilityChangers, projectChangeRequests, projectVisibilityChangers) <- (
        Future.sequence(projectApprovals.map { project =>
          val perms = VisibilityTypes.values.map(_.permission).map { perm =>
            request.user can perm in project map (value => (perm, value))
          }
          Future.sequence(perms).map(_.toMap)
        }),
        Future.sequence(lastChangeRequests.map {
          case None => Future.successful(None)
          case Some(lcr) => lcr.created.value
        }),
        Future.sequence(lastVisibilityChanges.map {
          case None => Future.successful(None)
          case Some(lcr) => lcr.created.value
        }),
        Future.sequence(projectChanges.map(_.lastChangeRequest.value)),
        Future.sequence(projectVisibilityChanges.map {
          case None => Future.successful(None)
          case Some(lcr) => lcr.created.value
        })
      ).parTupled
    }
    yield {
      val needsApproval = projectApprovals zip perms zip lastChangeRequests zip lastChangeRequesters zip lastVisibilityChanges zip lastVisibilityChangers map { case (((((a,b),c),d),e),f) =>
        (a,b,c,d.fold("Unknown")(_.name),e,f.fold("Unknown")(_.name))
      }
      val waitingProjects = projectChanges zip projectChangeRequests zip projectVisibilityChanges zip projectVisibilityChangers map { case (((a,b), c), d) =>
        (a,b,c,d.fold("Unknown")(_.name))
      }

      Ok(views.users.admin.visibility(needsApproval, waitingProjects))
    }
  }
}
