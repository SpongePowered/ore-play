package controllers.project

import javax.inject.Inject

import controllers.BaseController
import controllers.sugar.Bakery
import db.impl.OrePostgresDriver.api._
import db.{ModelFilter, ModelService}
import form.OreForms
import models.project.{Page, Project}
import ore.permission.EditPages
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.i18n.MessagesApi
import play.api.mvc.Action
import security.spauth.SingleSignOnConsumer
import views.html.projects.{pages => views}
import util.StringUtils._

/**
  * Controller for handling Page related actions.
  */
class Pages @Inject()(forms: OreForms,
                      stats: StatTracker,
                      implicit override val bakery: Bakery,
                      implicit override val sso: SingleSignOnConsumer,
                      implicit override val messagesApi: MessagesApi,
                      implicit override val env: OreEnv,
                      implicit override val config: OreConfig,
                      implicit override val service: ModelService)
                      extends BaseController {

  private val self = controllers.project.routes.Pages

  private def PageEditAction(author: String, slug: String)
  = AuthedProjectAction(author, slug, requireUnlock = true) andThen ProjectPermissionAction(EditPages)

  /**
    * Return the best guess of the page
    *
    * @param project
    * @param page
    * @return Tuple: Optional Page, true if using legacy fallback
    */
  def withPage(project: Project, page: String): (Option[Page], Boolean) = {
    val parts = page.split("/")
    if (parts.size == 2) {
      val parentId = project.pages.find(equalsIgnoreCase(_.slug, parts(0))).map(_.id.getOrElse(-1)).getOrElse(-1)
      val pages: Seq[Page] = project.pages.filter(equalsIgnoreCase(_.slug, parts(1))).seq
      (pages.find(_.parentId == parentId), false)
    } else {
      val result = project.pages.find((ModelFilter[Page](_.slug === parts(0)) +&& ModelFilter[Page](_.parentId === -1)).fn)
      if (result.isEmpty) {
        (project.pages.find(ModelFilter[Page](_.slug === parts(0)).fn), true)
      } else {
        (result, false)
      }
    }
  }

  /**
    * Displays the specified page.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @param page   Page name
    * @return View of page
    */
  def show(author: String, slug: String, page: String) = ProjectAction(author, slug) { implicit request =>
    val project = request.project
    val optionPage = withPage(project, page)
    if (optionPage._1.isDefined) {
      this.stats.projectViewed(implicit request => Ok(views.view(project, optionPage._1.get, optionPage._2)))
    } else {
      notFound
    }
  }

  /**
    * Displays the documentation page editor for the specified project and page
    * name.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @param page   Page name
    * @return Page editor
    */
  def showEditor(author: String, slug: String, page: String) = PageEditAction(author, slug) { implicit request =>
    val project = request.project
    val parts = page.split("/")
    var pageName = parts(0)
    var parentId = -1
    if (parts.size == 2) {
      pageName = parts(1)
      parentId = project.pages.find(equalsIgnoreCase(_.slug, parts(0))).map(_.id.getOrElse(-1)).getOrElse(-1)
    }
    val optionPage = project.pages.find(equalsIgnoreCase(_.slug, pageName))
    val pageModel = optionPage.getOrElse(project.getOrCreatePage(pageName, parentId))
    Ok(views.view(project, pageModel, editorOpen = true))
  }

  /**
    * Renders the submitted page content and returns the result.
    *
    * @return Rendered content
    */
  def showPreview() = Action { implicit request =>
    Ok(Page.Render((request.body.asJson.get \ "raw").as[String]))
  }

  /**
    * Saves changes made on a documentation page.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @param page   Page name
    * @return Project home
    */
  def save(author: String, slug: String, page: String) = PageEditAction(author, slug) { implicit request =>
    this.forms.PageEdit.bindFromRequest().fold(
      hasErrors =>
        Redirect(self.show(author, slug, page)).withError(hasErrors.errors.head.message),
      pageData => {
        val project = request.project
        var parentId = pageData.parentId.getOrElse(-1)
        //noinspection ComparingUnrelatedTypes
        if (parentId != -1 && !project.rootPages.filterNot(_.name.equals(Page.HomeName)).exists(_.id.get == parentId)) {
          BadRequest("Invalid parent ID.")
        } else {
          val content = pageData.content
          if (page.equals(Page.HomeName) && (content.isEmpty || content.get.length < Page.MinLength)) {
            Redirect(self.show(author, slug, page)).withError("error.minLength")
          } else {
            val parts = page.split("/")
            var pageName = pageData.name.getOrElse(parts(0))
            if (parts.size == 2) {
              pageName = pageData.name.getOrElse(parts(1))
              parentId = project.pages.find(equalsIgnoreCase(_.slug, parts(0))).map(_.id.getOrElse(-1)).getOrElse(-1)
            }
            val pageModel = project.getOrCreatePage(pageName, parentId)
            pageData.content.map(pageModel.contents = _)
            Redirect(self.show(author, slug, page))
          }
        }
      }
    )
  }

  /**
    * Irreversibly deletes the specified Page from the specified Project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @param page   Page name
    * @return Redirect to Project homepage
    */
  def delete(author: String, slug: String, page: String) = PageEditAction(author, slug) { implicit request =>
    val project = request.project
    val optionPage = withPage(project, page)
    if (optionPage._1.isDefined)
      this.service.access[Page](classOf[Page]).remove(optionPage._1.get)

    Redirect(routes.Projects.show(author, slug))
  }

}
