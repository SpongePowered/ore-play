package controllers.project

import javax.inject.Inject

import controllers.OreBaseController
import controllers.sugar.Bakery
import controllers.sugar.Requests.OreRequest
import db.impl.OrePostgresDriver.api._
import db.{ModelFilter, ModelService}
import form.OreForms
import models.project.{Page, Project}
import ore.permission.EditPages
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import util.StringUtils._
import views.html.projects.{pages => views}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Controller for handling Page related actions.
  */
class Pages @Inject()(forms: OreForms,
                      stats: StatTracker,
                      implicit override val bakery: Bakery,
                      implicit override val cache: AsyncCacheApi,
                      implicit override val sso: SingleSignOnConsumer,
                      implicit override val messagesApi: MessagesApi,
                      implicit override val env: OreEnv,
                      implicit override val config: OreConfig,
                      implicit override val service: ModelService)
                      extends OreBaseController {

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
  def withPage(project: Project, page: String): Future[(Option[Page], Boolean)] = {
    val parts = page.split("/")
    if (parts.size == 2) {
      project.pages.find(equalsIgnoreCase(_.slug, parts(0))).map {
        _.flatMap(_.id).getOrElse(-1)
      } flatMap { parentId =>
        project.pages.filter(equalsIgnoreCase(_.slug, parts(1))).map(seq => seq.find(_.parentId == parentId)).map((_, false))
      }
    } else {
      project.pages.find((ModelFilter[Page](_.slug === parts(0)) +&& ModelFilter[Page](_.parentId === -1)).fn).flatMap {
        case Some(r) => Future { (Some(r), false) }
        case None => project.pages.find(ModelFilter[Page](_.slug === parts(0)).fn).map((_, true))
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
  def show(author: String, slug: String, page: String) = ProjectAction(author, slug).async { request =>
    val project = request.project
    implicit val r = request.request

    withPage(project.project, page).flatMap {
      case (None, b) => Future.successful(notFound)
      case (Some(p), b) =>
        projects.queryProjectPages(project.project) flatMap { pages =>
          val pageCount = pages.size + pages.values.map(_.size).sum
          val parentPage = if (pages.contains(p)) None
          else pages.collectFirst { case (pp, page) if page.contains(p) => pp }
          this.stats.projectViewed(_ => Ok(views.view(project, pages, p, parentPage, pageCount, b)))(request)

        }
    }
  }

  /**
    * Displays the documentation page editor for the specified project and page
    * name.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @param pageName   Page name
    * @return Page editor
    */
  def showEditor(author: String, slug: String, pageName: String) = PageEditAction(author, slug).async { request =>
    implicit val r = request.request
    val project = request.project
    val parts = pageName.split("/")
    val p = parts.size match {
      case 2 => project.project.pages.find(equalsIgnoreCase(_.slug, parts(0))).map(_.flatMap(_.id).getOrElse(-1))
        .map((parts(1), _))
      case _ => Future{(parts(0), -1)}
    }
    p.flatMap {
      case (name, parentId) =>
        project.project.pages.find(equalsIgnoreCase(_.slug, name)).flatMap {
          case Some(page) => Future.successful(page)
          case None => project.project.getOrCreatePage(name, parentId)
        }
    } flatMap { p =>
      projects.queryProjectPages(project.project) map { pages =>
        val pageCount = pages.size + pages.values.map(_.size).sum
        val parentPage = if (pages.contains(p)) None
        else pages.collectFirst { case (pp, page) if page.contains(p) => pp }
        Ok(views.view(project, pages, p, parentPage, pageCount, editorOpen = true))
      }
    }
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
  def save(author: String, slug: String, page: String) = PageEditAction(author, slug).async { implicit request =>
    this.forms.PageEdit.bindFromRequest().fold(
      hasErrors =>
        Future.successful(Redirect(self.show(author, slug, page)).withError(hasErrors.errors.head.message)),
      pageData => {
        val project = request.project
        val parentId = pageData.parentId.getOrElse(-1)
        //noinspection ComparingUnrelatedTypes
        project.project.rootPages.flatMap { rootPages =>
          if (parentId != -1 && !rootPages.filterNot(_.name.equals(Page.HomeName)).exists(_.id.get == parentId)) {
            Future.successful(BadRequest("Invalid parent ID."))
          } else {
            val content = pageData.content
            if (page.equals(Page.HomeName) && (content.isEmpty || content.get.length < Page.MinLength)) {
              Future.successful(Redirect(self.show(author, slug, page)).withError("error.minLength"))
            } else {
              val parts = page.split("/")

              val created = if (parts.size == 2) {
                project.project.pages.find(equalsIgnoreCase(_.slug, parts(0))).flatMap { parent =>
                  val parentId = parent.flatMap(_.id).getOrElse(-1)
                  val pageName = pageData.name.getOrElse(parts(1))
                  project.project.getOrCreatePage(pageName, parentId, pageData.content)
                }
              } else {
                val pageName = pageData.name.getOrElse(parts(0))
                project.project.getOrCreatePage(pageName, parentId, pageData.content)
              }
              created.map { _ =>
                Redirect(self.show(author, slug, page))
              }
            }
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
  def delete(author: String, slug: String, page: String) = PageEditAction(author, slug).async { implicit request =>
    val project = request.project
    for {
      optionPage <- withPage(project.project, page)
    } yield {
      if (optionPage._1.isDefined)
        this.service.access[Page](classOf[Page]).remove(optionPage._1.get)

      Redirect(routes.Projects.show(author, slug))
    }
  }

}
