package controllers.project

import controllers.OreBaseController
import controllers.sugar.Bakery
import db.impl.OrePostgresDriver.api._
import db.{ModelFilter, ModelService}
import form.OreForms
import javax.inject.Inject
import models.project.{Page, Project}
import models.user.{LoggedActionContext, UserActionLogger, LoggedAction}
import ore.permission.EditPages
import ore.{OreConfig, OreEnv, StatTracker}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import util.StringUtils._
import views.html.projects.{pages => views}
import util.instances.future._
import util.syntax._
import scala.concurrent.{ExecutionContext, Future}

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
                      implicit override val service: ModelService)(implicit val ec: ExecutionContext)
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
    //TODO: Can the return type here be changed to OptionT[Future (Page, Boolean)]?
    val parts = page.split("/")
    if (parts.size == 2) {
      project.pages
        .find(equalsIgnoreCase(_.slug, parts(0)))
        .subflatMap(_.id)
        .getOrElse(-1)
        .flatMap { parentId =>
          project.pages.filter(equalsIgnoreCase(_.slug, parts(1))).map(seq => seq.find(_.parentId == parentId)).map((_, false))
        }
    } else {
      project.pages.find((ModelFilter[Page](_.slug === parts(0)) +&& ModelFilter[Page](_.parentId === -1)).fn).value.flatMap {
        case Some(r) => Future.successful((Some(r), false))
        case None => project.pages.find(ModelFilter[Page](_.slug === parts(0)).fn).value.map((_, true))
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
    val data = request.data
    implicit val r = request.request

    withPage(data.project, page).flatMap {
      case (None, _) => Future.successful(notFound)
      case (Some(p), b) =>
        projects.queryProjectPages(data.project) flatMap { pages =>
          val pageCount = pages.size + pages.map(_._2.size).sum
          val parentPage = if (pages.map(_._1).contains(p)) None
          else pages.collectFirst { case (pp, subPage) if subPage.contains(p) => pp }
          this.stats.projectViewed(request)(_ => Ok(views.view(data, request.scoped, pages, p, parentPage, pageCount, b)))

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
    val data = request.data
    val parts = pageName.split("/")

    for {
      (name, parentId) <- if (parts.size != 2) Future.successful((parts(0), -1)) else {
        data.project.pages.find(equalsIgnoreCase(_.slug, parts(0))).subflatMap(_.id).getOrElse(-1).map((parts(1), _))
      }
      (p, pages) <- (
        data.project.pages.find(equalsIgnoreCase(_.slug, name)).getOrElseF(data.project.getOrCreatePage(name, parentId)),
        projects.queryProjectPages(data.project)
      ).parTupled
    } yield {
      val pageCount = pages.size + pages.map(_._2.size).sum
      val parentPage = pages.collectFirst { case (pp, page) if page.contains(p) => pp }
      Ok(views.view(data, request.scoped, pages, p, parentPage, pageCount, editorOpen = true))
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
        Future.successful(Redirect(self.show(author, slug, page)).withFormErrors(hasErrors.errors)),
      pageData => {
        val data = request.data
        val parentId = pageData.parentId.getOrElse(-1)
        //noinspection ComparingUnrelatedTypes
        data.project.rootPages.flatMap { rootPages =>
          if (parentId != -1 && !rootPages.filterNot(_.name.equals(Page.HomeName)).exists(_.id.get == parentId)) {
            Future.successful(BadRequest("Invalid parent ID."))
          } else {
            val content = pageData.content
            if (page.equals(Page.HomeName) && (content.isEmpty || content.get.length < Page.MinLength)) {
              Future.successful(Redirect(self.show(author, slug, page)).withError("error.minLength"))
            } else {
              val parts = page.split("/")

              val created = if (parts.size == 2) {
                data.project.pages.find(equalsIgnoreCase(_.slug, parts(0))).subflatMap(_.id).getOrElse(-1).flatMap { parentId =>
                  val pageName = pageData.name.getOrElse(parts(1))
                  data.project.getOrCreatePage(pageName, parentId, pageData.content)
                }
              } else {
                val pageName = pageData.name.getOrElse(parts(0))
                data.project.getOrCreatePage(pageName, parentId, pageData.content)
              }
              created flatMap { createdPage =>
                if (pageData.content.isDefined) {
                  val oldPage = createdPage.contents
                  val newPage = pageData.content.get
                  UserActionLogger.log(request.request, LoggedAction.ProjectPageEdited, data.project.id.getOrElse(-1), oldPage, newPage)
                  createdPage.setContents(newPage)
                } else Future.successful(createdPage)
              } map { _ =>
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
    val data = request.data
    withPage(data.project, page).map { optionPage =>
      if (optionPage._1.isDefined)
        this.service.access[Page](classOf[Page]).remove(optionPage._1.get)

      Redirect(routes.Projects.show(author, slug))
    }
  }

}
