package controllers

import javax.inject.Inject

import controllers.Requests.AuthRequest
import controllers.routes.{Application => self}
import db.ModelService
import db.action.ModelFilter
import db.impl.OrePostgresDriver.api._
import db.impl.action.ProjectActions
import db.impl.{FlagTable, ProjectTable}
import forums.DiscourseApi
import models.project.{Flag, Project, Version}
import ore.permission._
import ore.permission.scope.GlobalScope
import ore.project.Categories.Category
import ore.project.{Categories, ProjectSortingStrategies}
import play.api.i18n.MessagesApi
import play.api.mvc._
import util.{DataHelper, OreConfig}
import views.{html => views}

/**
  * Main entry point for application.
  */
class Application @Inject()(data: DataHelper,
                            implicit override val messagesApi: MessagesApi,
                            implicit override val config: OreConfig,
                            implicit override val forums: DiscourseApi,
                            implicit override val service: ModelService)
                            extends BaseController {

  private def FlagAction = Authenticated andThen PermissionAction[AuthRequest](ReviewFlags)

  /**
    * Display the home page.
    *
    * @return Home page
    */
  def showHome(categories: Option[String], query: Option[String], sort: Option[Int], page: Option[Int]) = {
    Action { implicit request =>
      // Get categories and sorting strategy
      var categoryArray: Array[Category] = categories.map(Categories.fromString).orNull
      val ordering = sort.map(ProjectSortingStrategies.withId(_).get).getOrElse(ProjectSortingStrategies.Default)

      // Determine filter
      val actions = this.service.getActions(classOf[ProjectActions])
      val canHideProjects = this.users.current.isDefined && (this.users.current.get can HideProjects in GlobalScope)
      var filter: ProjectTable => Rep[Boolean] = query.map { q =>
        // Search filter + visible
        var f  = actions.searchFilter(q)
        if (!canHideProjects) f = f && (_.isVisible)
        f
      }.orNull[ModelFilter[ProjectTable, Project]]
      if (filter == null && !canHideProjects) filter = _.isVisible

      // Get projects
      val projectsPerPage = this.config.projects.getInt("init-load").get
      val p = page.getOrElse(1)
      val offset = (p - 1) * projectsPerPage
      val future = actions.collect(filter, categoryArray, projectsPerPage, offset, ordering)
      val projects = this.service.await(future).get

      val total = this.service.await(this.service.getActions(classOf[ProjectActions]).count()).get

      if (categoryArray != null && Categories.visible.toSet.equals(categoryArray.toSet)) categoryArray = null

      Ok(views.home(projects, total, Option(categoryArray), query, p, ordering))
    }
  }

  /**
    * Shows the moderation queue for unreviewed versions.
    *
    * @return View of unreviewed versions.
    */
  def showQueue() = {
    (Authenticated andThen PermissionAction[AuthRequest](ReviewProjects)) { implicit request =>
      Ok(views.admin.queue(Version.notReviewed.map(v => (v.project, v))))
    }
  }

  /**
    * Shows the overview page for flags.
    *
    * @return Flag overview
    */
  def showFlags() = FlagAction { implicit request =>
    Ok(views.admin.flags(Flag.unresolved))
  }

  /**
    * Sets the resolved state of the specified flag.
    *
    * @param flagId   Flag to set
    * @param resolved Resolved state
    * @return         Ok
    */
  def setFlagResolved(flagId: Int, resolved: Boolean) = FlagAction { implicit request =>
    this.service.access[FlagTable, Flag](classOf[Flag]).get(flagId) match {
      case None => NotFound
      case Some(flag) =>
        flag.setResolved(resolved)
        Ok
    }
  }

  /**
    * Removes a trailing slash from a route.
    *
    * @param path Path with trailing slash
    * @return     Redirect to proper route
    */
  def removeTrail(path: String) = Action {
    MovedPermanently('/' + path)
  }

  /**
    * Helper route to reset Ore.
    */
  def reset = (Authenticated andThen PermissionAction[AuthRequest](ResetOre)) { implicit request =>
    this.config.checkDebug()
    this.data.reset()
    Redirect(self.showHome(None, None, None, None)).withNewSession
  }

  /**
    * Fills Ore with some dummy data.
    *
    * @return Redirect home
    */
  def seed(users: Option[Int], versions: Option[Int], channels: Option[Int]) = {
    (Authenticated andThen PermissionAction[AuthRequest](SeedOre)) { implicit request =>
      this.config.checkDebug()
      this.data.seed(users.getOrElse(200), versions.getOrElse(0), channels.getOrElse(1))
      Redirect(self.showHome(None, None, None, None)).withNewSession
    }
  }

  def migrate() = (Authenticated andThen PermissionAction[AuthRequest](MigrateOre)) { implicit request =>
    this.data.migrate()
    Redirect(self.showHome(None, None, None, None))
  }

}
