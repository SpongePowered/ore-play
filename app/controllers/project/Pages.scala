package controllers.project

import javax.inject.Inject

import controllers.BaseController
import controllers.sugar.Bakery
import db.ModelService
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

  private def hasPagesLeft(project: Project) = project.pages.size < config.projects.getInt("max-pages").getOrElse(10)

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
    project.pages.find(equalsIgnoreCase(_.name, page)) match {
      case None => notFound
      case Some(p) => this.stats.projectViewed(implicit request => Ok(views.view(project, p)))
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
    val pageOpt = project.getPage(page)
    pageOpt.fold(notFound)(p => Ok(views.view(project, p, editorOpen = true)))
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
        val parentId = pageData.parentId.getOrElse(-1)

        //noinspection ComparingUnrelatedTypes
        if (parentId != -1 && !project.rootPages.filterNot(_.name.equals(Page.HomeName)).exists(_.id.get == parentId)) {
          BadRequest("Invalid parent ID.")
        } else {
          val content = pageData.content
          if (page.equals(Page.HomeName) && (content.isEmpty || content.get.length < Page.MinLength)) {
            Redirect(self.show(author, slug, page)).withError("error.minLength")
          } else if (project.pageExists(page) || hasPagesLeft(project)) {
            val pageModel = project.getOrCreatePage(page, parentId)
            pageData.content.map(pageModel.contents = _)
            Redirect(self.show(author, slug, page))
          } else {
            Redirect(self.show(author, slug, Page.HomeName)).withError("error.tooManyPages")
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
    this.service.access[Page](classOf[Page]).remove(project.pages.find(equalsIgnoreCase(_.name, page)).get)
    Redirect(routes.Projects.show(author, slug))
  }

}
