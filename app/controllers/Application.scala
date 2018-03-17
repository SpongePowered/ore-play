package controllers

import java.sql.Timestamp
import java.time.Instant
import javax.inject.Inject

import controllers.sugar.Bakery
import controllers.sugar.Requests.AuthRequest
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.schema.{ProjectSchema, ReviewSchema, VersionSchema}
import db.{ModelFilter, ModelSchema, ModelService}
import form.OreForms
import models.admin.Review
import models.project._
import models.user.role._
import models.viewhelper.HeaderData
import ore.Platforms.Platform
import ore.permission._
import ore.permission.role.{Role, RoleTypes}
import ore.permission.scope.GlobalScope
import ore.project.Categories.Category
import ore.project.{Categories, ProjectSortingStrategies}
import ore.{OreConfig, OreEnv, Platforms}
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import util.DataHelper
import views.{html => views}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Main entry point for application.
  */
final class Application @Inject()(data: DataHelper,
                                  forms: OreForms,
                                  implicit override val bakery: Bakery,
                                  implicit override val sso: SingleSignOnConsumer,
                                  implicit override val messagesApi: MessagesApi,
                                  implicit override val env: OreEnv,
                                  implicit override val config: OreConfig,
                                  implicit override val service: ModelService)
                                  extends OreBaseController {

  private def FlagAction = Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)

  /**
    * Show external link warning page.
    *
    * @return External link page
    */
  def linkOut(remoteUrl: String) = OreAction { implicit request =>
    implicit val headerData = request.data
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
               platform: Option[String]) = OreAction async { implicit request =>
    // Get categories and sorting strategy
    val ordering = sort.flatMap(ProjectSortingStrategies.withId).getOrElse(ProjectSortingStrategies.Default)
    val actions = this.service.getSchema(classOf[ProjectSchema])

    val canHideProjects = request.data(HideProjects)
    val currentUserId = request.data.currentUser.flatMap(_.id).getOrElse(-1)

    // TODO platform filter is not implemented
    val pform = platform.flatMap(p => Platforms.values.find(_.name.equalsIgnoreCase(p)).map(_.asInstanceOf[Platform]))
    val platformFilter = pform.map(actions.platformFilter).getOrElse(ModelFilter.Empty)
    var categoryArray: Array[Category] = categories.map(Categories.fromString).orNull
    val q = query.map(queryString => s"%${queryString.toLowerCase}%").getOrElse("%")

    val pageSize = this.config.projects.get[Int]("init-load")
    val p = page.getOrElse(1)
    val offset = (p - 1) * pageSize

    val projectQuery = queryProjectRV filter { case (p, u, v) =>
      (LiteralColumn(true) === canHideProjects) ||
        (p.visibility === VisibilityTypes.Public) ||
        (p.visibility === VisibilityTypes.New) ||
        ((p.userId === currentUserId) && (p.visibility =!= VisibilityTypes.SoftDelete))
    } filter { case (p, u, v) =>
      (LiteralColumn(0) === categoryArray.length) || (p.category inSetBind categoryArray)
    } filter {  case (p, u, v) =>
      (p.name.toLowerCase like q) ||
      (p.description.toLowerCase like q) ||
      (p.ownerName.toLowerCase like q) ||
      (p.pluginId.toLowerCase like q)
    } drop offset take pageSize

    // Get projects
    for {
      projects <- service.DB.db.run(projectQuery.result)
      tags <- Future.sequence(projects.map(_._3.tags))
    } yield {
      val data = projects zip tags map { case ((p, u, v), tags) =>
        (p,u,v,tags)
      }

      if (categoryArray != null && Categories.visible.toSet.equals(categoryArray.toSet))
        categoryArray = null

      Ok(views.home(data, Option(categoryArray), query.find(_.nonEmpty), p, ordering, pform))
    }

   }

  /**
    * Shows the moderation queue for unreviewed versions.
    *
    * @return View of unreviewed versions.
    */
  def showQueue() = (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)).async { implicit request =>

    val data = this.service.DB.db.run(queryQueue.result).flatMap { list =>
      service.DB.db.run(queryReviews(list.map(_._1.id.get)).result).map { reviewList =>
        reviewList.groupBy(_._1.versionId)
      } map { reviewsByVersion =>
        (list, reviewsByVersion)
      }
    } map { case (list, reviewsByVersion) =>
      val reviewData = reviewsByVersion.mapValues { reviews =>

        reviews.filter { case (review, _) =>
          review.createdAt.isDefined && review.endedAt.isEmpty
        }.sorted(Review.ordering).headOption.map { case (r, a) =>
          (r, true, a) // Unfinished Review
        } orElse reviews.sorted(Review.ordering).headOption.map { case (r, a) =>
          (r, false, a) // any review
        }
      }

      list.map { case (v, p, c, a, u) =>
        (v, p, c, a, u, reviewData.getOrElse(v.id.get, None))
      }
    }
    data map { list =>
      val lists = list.partition(_._6.isEmpty)
      val headerData: HeaderData = null // TODO headerdata
      val reviewList = lists._1.map { case (v, p, c, a, _, r) =>
        (p, v, c, a, r.get)
      }
      val unReviewList = lists._2.map { case (v, p, c, a, u, _) =>
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
      c <- channelTable if v.channelId === c.id && v.isReviewed =!= true
      p <- projectTable if v.projectId === p.id
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
  def showFlags() = FlagAction.async { implicit request =>
    for {
      flags <- this.service.access[Flag](classOf[Flag]).filterNot(_.isResolved)
      users <- Future.sequence(flags.map(_.user))
      projects <- Future.sequence(flags.map(_.project))
      perms <- Future.sequence(projects.map { project =>
        val perms =VisibilityTypes.values.map(_.permission).map { perm =>
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
  def setFlagResolved(flagId: Int, resolved: Boolean) = FlagAction.async { implicit request =>
    this.service.access[Flag](classOf[Flag]).get(flagId) flatMap {
      case None => Future.successful(NotFound)
      case Some(flag) =>
        users.current.map { user =>
          flag.setResolved(resolved, user)
          Ok
        }
    }
  }

  def showHealth() = (Authenticated andThen PermissionAction[AuthRequest](ViewHealth)) async { implicit request =>

    for {
      noTopicProjects <- projects.filter(p => p.topicId === -1 || p.postId === -1)
      topicDirtyProjects <- projects.filter(_.isTopicDirty)
      staleProjects <- projects.stale
      notPublic <- projects.filterNot(_.visibility === VisibilityTypes.Public)
      missingFileProjects <- projects.missingFile.flatMap { v =>
        Future.sequence(v.map { v => v.project.map(p => (v, p)) })
      }
    } yield {
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
  def reset() = (Authenticated andThen PermissionAction[AuthRequest](ResetOre)) { implicit request =>
    this.config.checkDebug()
    this.data.reset
    Redirect(ShowHome).withNewSession
  }

  /**
    * Fills Ore with some dummy data.
    *
    * @return Redirect home
    */
  def seed(users: Int, projects: Int, versions: Int, channels: Int) = {
    (Authenticated andThen PermissionAction[AuthRequest](SeedOre)) { implicit request =>
      this.config.checkDebug()
      this.data.seed(users, projects, versions, channels)
      Redirect(ShowHome).withNewSession
    }
  }

  /**
    * Performs miscellaneous migration actions for use in deployment.
    *
    * @return Redirect home
    */
  def migrate() = (Authenticated andThen PermissionAction[AuthRequest](MigrateOre)) { implicit request =>
    this.data.migrate()
    Redirect(ShowHome)
  }

  /**
    * Show the activities page for a user
    */
  def showActivities(user: String) = (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) async { implicit request =>
    this.users.withName(user).flatMap {
      case None => Future.successful(NotFound)
      case Some(u) =>
        val activities: Future[Seq[(Object, Option[Project])]] = u.id match {
          case None => Future{Seq.empty}
          case Some(id) =>
            val reviews = this.service.access[Review](classOf[Review])
              .filter(_.userId === id)
              .map(_.take(20).map { review => { review ->
                this.service.access[Version](classOf[Version]).filter(_.id === review.versionId).flatMap { version =>
                  this.projects.find(_.id === version.head.projectId)
                }
              }})
            val flags = this.service.access[Flag](classOf[Flag])
              .filter(_.resolvedBy === id)
              .map(_.take(20).map(flag => flag -> this.projects.find(_.id === flag.projectId)))

            val allActivities = reviews.flatMap(r => flags.map(_ ++ r))

            allActivities.flatMap(Future.traverse(_) {
              case (k, fv) => fv.map(k -> _)
            }.map(_.sortWith(sortActivities)))
        }
        activities.map(a => Ok(views.users.admin.activity(u, a)))
    }
  }

  /**
    * Compares 2 activities (supply a [[Review]] or [[Flag]]) to decide which came first
    * @param o1 Review / Flag
    * @param o2 Review / Flag
    * @return Boolean
    */
  def sortActivities(o1: Object, o2: Object): Boolean = {
    var o1Time: Long = 0
    var o2Time: Long = 0
    if (o1.isInstanceOf[Review]) {
      o1Time = o1.asInstanceOf[Review].endedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
    }
    if (o2.isInstanceOf[Flag]) {
      o2Time = o2.asInstanceOf[Flag].resolvedAt.getOrElse(Timestamp.from(Instant.EPOCH)).getTime
    }
    o1Time > o2Time
  }
  /**
    * Show stats
    * @return
    */
  def showStats() = (Authenticated andThen PermissionAction[AuthRequest](ViewStats)).async { implicit request =>

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

    for {
      reviews         <- last10DaysCountQuery("project_version_reviews", "ended_at")
      uploads         <- last10DaysCountQuery("project_versions", "created_at")
      totalDownloads  <- last10DaysCountQuery("project_version_downloads", "created_at")
      unsafeDownloads <- last10DaysCountQuery("project_version_unsafe_downloads", "created_at")
      flagsOpen       <- last10DaysTotalOpen("project_flags", "created_at", "resolved_at")
      flagsClosed     <- last10DaysCountQuery("project_flags", "resolved_at")
    }
    yield {
      Ok(views.users.admin.stats(reviews, uploads, totalDownloads, unsafeDownloads, flagsOpen, flagsClosed))
    }
  }

  def UserAdminAction = Authenticated andThen PermissionAction[AuthRequest](UserAdmin)

  def userAdmin(user: String) = UserAdminAction.async { implicit request =>
    this.users.withName(user).flatMap {
      case None => Future.successful(notFound)
      case Some(u) =>
        for {
          userData <- getUserData(request, user)
          isOrga <- u.isOrganization
          projectRoles <- if (isOrga) Future.successful(Seq.empty) else u.projectRoles.all
          projects <- Future.sequence(projectRoles.map(_.project))
          orgaData <- if (isOrga) getOrganizationData(request, user) else Future.successful(None)
        } yield {
          val pr = projects zip projectRoles
          Ok(views.users.admin.userAdmin(userData.get, orgaData, pr.toSeq))
        }
    }
  }

  def updateUser(userName: String) = UserAdminAction.async { implicit request =>
    this.users.withName(userName).flatMap {
      case None => Future.successful(NotFound)
      case Some(user) => {
        this.forms.UserAdminUpdate.bindFromRequest.fold(
          _ => Future.successful(BadRequest),
          { case (thing, action, data) =>

            import play.api.libs.json._
            val json = Json.parse(data)

            def updateRoleTable[M <: RoleModel](modelAccess: ModelAccess[M], allowedType: Class[_ <: Role], ownerType: RoleTypes.RoleType, transferOwner: M => Future[Unit]) = {
              val id = (json \ "id").as[Int]
              val asd = action match {
                case "setRole" => modelAccess.get(id).map {
                  case None => BadRequest
                  case Some(role) =>
                    val roleType = RoleTypes.withId((json \ "role").as[Int])
                    if (roleType == ownerType) {
                      transferOwner(role)
                      Ok
                    } else if (roleType.roleClass == allowedType && roleType.isAssignable) {
                      role.setRoleType(roleType)
                      Ok
                    } else BadRequest
                }
                case "setAccepted" => modelAccess.get(id).map {
                  case None => BadRequest
                  case Some(role) =>
                    role.setAccepted((json \ "accepted").as[Boolean])
                    Ok
                }
                case "deleteRole" => modelAccess.get(id).map {
                  case None => BadRequest
                  case Some(role) =>
                    if (role.roleType.isAssignable) {
                      role.remove()
                      Ok
                    } else BadRequest
                }
              }
              asd
            }

            def transferOrgOwner(r: OrganizationRole) = {
              r.organization.flatMap { orga =>
                orga.transferOwner(orga.memberships.newMember(r.userId))
              }
            }

            val isOrga = user.isOrganization
            thing match {
              case "orgRole" =>
                isOrga.flatMap {
                  case true => Future.successful(BadRequest)
                  case false => updateRoleTable(user.organizationRoles, classOf[OrganizationRole], RoleTypes.OrganizationOwner, transferOrgOwner)
                }
              case "memberRole" =>
                isOrga.flatMap {
                  case false => Future.successful(BadRequest)
                  case true =>
                    user.toOrganization.flatMap { orga =>
                      updateRoleTable(orga.memberships.roles, classOf[OrganizationRole], RoleTypes.OrganizationOwner, transferOrgOwner)
                    }
                }
              case "projectRole" =>
                isOrga.flatMap {
                  case true => Future.successful(BadRequest)
                  case false => updateRoleTable(user.projectRoles, classOf[ProjectRole], RoleTypes.ProjectOwner,
                    (r: ProjectRole) => r.project.flatMap(p => p.transferOwner(p.memberships.newMember(r.userId))))
                }
              case _ => Future.successful(BadRequest)
            }

          })
      }
    }
  }

  /**
    *
    * @return Show page
    */
  def showProjectVisibility() = (Authenticated andThen PermissionAction[AuthRequest](ReviewVisibility)) async { implicit request =>
    val projectSchema = this.service.getSchema(classOf[ProjectSchema])

    for {
      projectApprovals <- projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsApproval).fn, ProjectSortingStrategies.Default, -1, 0)
      perms <- Future.sequence(projectApprovals.map { project =>
        val perms = VisibilityTypes.values.map(_.permission).map { perm =>
          request.user can perm in project map (value => (perm, value))
        }
        Future.sequence(perms).map(_.toMap)
      })
      lastChangeRequests <- Future.sequence(projectApprovals.map(_.lastChangeRequest))
      lastChangeRequesters <- Future.sequence(lastChangeRequests.map {
                                case None => Future.successful(None)
                                case Some(lcr) => lcr.created
                              })
      lastVisibilityChanges <- Future.sequence(projectApprovals.map(_.lastVisibilityChange))
      lastVisibilityChangers <- Future.sequence(lastVisibilityChanges.map {
                                  case None => Future.successful(None)
                                  case Some(lcr) => lcr.created
                                })

      projectChanges <- projectSchema.collect(ModelFilter[Project](_.visibility === VisibilityTypes.NeedsChanges).fn, ProjectSortingStrategies.Default, -1, 0)
      projectChangeRequests <- Future.sequence(projectChanges.map(_.lastChangeRequest))
      projectVisibilityChanges <- Future.sequence(projectChanges.map(_.lastVisibilityChange))
      projectVisibilityChangers <- Future.sequence(projectVisibilityChanges.map {
        case None => Future.successful(None)
        case Some(lcr) => lcr.created
      })

    }
    yield {
      val needsApproval = projectApprovals zip perms zip lastChangeRequests zip lastChangeRequesters zip lastVisibilityChanges zip lastVisibilityChangers map { case (((((a,b),c),d),e),f) =>
        (a,b,c,d.map(_.name),e,f.map(_.name))
      }
      val waitingProjects = projectChanges zip projectChangeRequests zip projectVisibilityChanges zip projectVisibilityChangers map { case (((a,b), c), d) =>
        (a,b,c,d.map(_.name))
      }

      Ok(views.users.admin.visibility(needsApproval, waitingProjects))
    }
  }
}
