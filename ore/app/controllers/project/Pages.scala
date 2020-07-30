package controllers.project

import java.nio.charset.StandardCharsets

import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent}
import play.utils.UriEncoding

import controllers.{OreBaseController, OreControllerComponents}
import form.OreForms
import form.project.PageSaveForm
import ore.StatTracker
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.PageTable
import ore.db.{DbRef, Model}
import ore.markdown.MarkdownRenderer
import ore.models.{Job, JobInfo}
import ore.models.project.{Page, Project}
import ore.models.user.{LoggedActionPage, LoggedActionType}
import ore.permission.Permission
import ore.util.StringUtils._
import util.UserActionLogger
import util.syntax._
import views.html.projects.{pages => views}

import cats.syntax.all._
import zio.interop.catz._
import zio.{IO, Task, UIO}

/**
  * Controller for handling Page related actions.
  */
class Pages(forms: OreForms, stats: StatTracker[UIO])(
    implicit oreComponents: OreControllerComponents,
    renderer: MarkdownRenderer
) extends OreBaseController {

  private val self = controllers.project.routes.Pages

  private def PageEditAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true).andThen(ProjectPermissionAction(Permission.EditPage))

  private val childPageQuery = {
    def childPageQueryFunction(parentSlug: Rep[String], childSlug: Rep[String]) = {
      val q           = TableQuery[PageTable]
      val parentPages = q.filter(_.slug.toLowerCase === parentSlug.toLowerCase).map(_.id)
      val childPage =
        q.filter(page => (page.parentId in parentPages) && page.slug.toLowerCase === childSlug.toLowerCase)
      childPage.take(1)
    }

    Compiled(childPageQueryFunction _)
  }

  def pageParts(page: String): List[String] =
    page.split("/").map(page => UriEncoding.decodePathSegment(page, StandardCharsets.UTF_8)).toList

  /**
    * Return the best guess of the page
    */
  def findPage(project: Model[Project], page: String): IO[Unit, Model[Page]] = pageParts(page) match {
    case parent :: child :: Nil => service.runDBIO(childPageQuery((parent, child)).result.headOption).get.orElseFail(())
    case single :: Nil =>
      project
        .pages(ModelView.now(Page))
        .find(p => p.slug.toLowerCase === single.toLowerCase && p.parentId.isEmpty)
        .toZIO
    case _ => IO.fail(())
  }

  def queryProjectPagesAndFindSpecific(
      project: Model[Project],
      page: String
  ): IO[Unit, (Seq[(Model[Page], Seq[Model[Page]])], Model[Page])] =
    projects
      .queryProjectPages(project)
      .map { pages =>
        def pageEqual(name: String): Model[Page] => Boolean = _.slug.toLowerCase == name.toLowerCase
        def findUpper(name: String)                         = pages.find(t => pageEqual(name)(t._1))

        val res = pageParts(page) match {
          case parent :: child :: Nil => findUpper(parent).map(_._2).flatMap(_.find(pageEqual(child)))
          case single :: Nil          => findUpper(single).map(_._1)
          case _                      => None
        }

        import cats.instances.option._
        res.tupleLeft(pages)
      }
      .get
      .orElseFail(())

  /**
    * Displays the specified page.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @param page   Page name
    * @return View of page
    */
  def show(author: String, slug: String, page: String): Action[AnyContent] = ProjectAction(author, slug).asyncF {
    implicit request =>
      queryProjectPagesAndFindSpecific(request.project, page).orElseFail(notFound).flatMap {
        case (pages, p) =>
          val pageCount = pages.size + pages.map(_._2.size).sum
          val parentPage =
            if (pages.map(_._1).contains(p)) None
            else pages.collectFirst { case (pp, subPage) if subPage.contains(p) => pp }

          import cats.instances.option._
          this.stats.projectViewed(
            IO.succeed(
              Ok(
                views.view(
                  request.data,
                  request.scoped,
                  Model.unwrapNested[Seq[(Model[Page], Seq[Page])]](pages),
                  p,
                  Model.unwrapNested(parentPage),
                  pageCount
                )
              )
            )
          )
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
  def showEditor(author: String, slug: String, pageName: String): Action[AnyContent] =
    PageEditAction(author, slug).asyncF { implicit request =>
      queryProjectPagesAndFindSpecific(request.project, pageName).orElseFail(notFound).map {
        case (pages, p) =>
          val pageCount  = pages.size + pages.map(_._2.size).sum
          val parentPage = pages.collectFirst { case (pp, page) if page.contains(p) => pp }

          import cats.instances.option._
          Ok(
            views.view(
              request.data,
              request.scoped,
              Model.unwrapNested[Seq[(Model[Page], Seq[Page])]](pages),
              p,
              Model.unwrapNested(parentPage),
              pageCount,
              editorOpen = true
            )
          )
      }
    }

  /**
    * Renders the submitted page content and returns the result.
    *
    * @return Rendered content
    */
  def showPreview(): Action[JsValue] = Action(parse.json) { implicit request =>
    Ok(renderer.render((request.body \ "raw").as[String]))
  }

  /**
    * Saves changes made on a documentation page.
    *
    * @param author Owner name
    * @param slug   Project slug
    * @param page   Page name
    * @return Project home
    */
  def save(author: String, slug: String, page: String): Action[PageSaveForm] =
    PageEditAction(author, slug).asyncF(
      parse.form(forms.PageEdit, onErrors = FormError(self.show(author, slug, page)))
    ) { implicit request =>
      val pageData = request.body
      val content  = pageData.content
      val project  = request.project
      val parentId = pageData.parentId

      for {
        rootPages <- service.runDBIO(project.rootPages(ModelView.raw(Page)).result)

        _ <- {
          val hasParent = parentId.isDefined
          val parentExists = rootPages
            .filter(_.name != Page.homeName)
            .exists(p => parentId.contains(p.id.value))

          if (hasParent && !parentExists)
            IO.fail(BadRequest("Invalid parent ID."))
          else
            IO.succeed(())
        }

        _ <- {
          if (page == Page.homeName && !content.exists(_.length >= Page.minLength)) {
            IO.fail(Redirect(self.show(author, slug, page)).withError("error.minLength"))
          } else {
            IO.succeed(())
          }
        }

        parts = page.split("/")
        getOrCreate = (parentId: Option[DbRef[Page]], part: Int) => {
          val pageName = pageData.name.getOrElse(parts(part))
          //For some reason Scala doesn't want to use the implicit monad here
          project.getOrCreatePage[UIO](pageName, parentId, content)
        }

        createdPage <- {
          if (parts.size == 2) {
            service
              .runDBIO(
                project
                  .pages(ModelView.later(Page))
                  .find(equalsIgnoreCase(_.slug, parts(0)))
                  .map(_.id)
                  .result
                  .headOption
              )
              .flatMap(getOrCreate(_, 1))
          } else {
            getOrCreate(parentId, 0)
          }
        }
        _ <- content.fold(IO.succeed(createdPage)) { newPage =>
          val oldPage    = createdPage.contents
          val updatePage = service.update(createdPage)(_.copy(contents = newPage))

          val addForumJob = if (createdPage.isHome) {
            service.insert(Job.UpdateDiscourseProjectTopic.newJob(project.id).toJob).unit
          } else IO.unit

          val log = UserActionLogger.log(
            request.request,
            LoggedActionType.ProjectPageEdited,
            createdPage.id,
            newPage,
            oldPage
          )(LoggedActionPage(_, Some(createdPage.projectId)))

          updatePage <* log <* addForumJob
        }
      } yield Redirect(self.show(author, slug, page))
    }

  /**
    * Irreversibly deletes the specified Page from the specified Project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @param page   Page name
    * @return Redirect to Project homepage
    */
  def delete(author: String, slug: String, page: String): Action[AnyContent] =
    PageEditAction(author, slug).asyncF { request =>
      findPage(request.project, page)
        .flatMap(service.delete(_).unit)
        .either
        .as(Redirect(routes.Projects.show(author, slug)))
    }

}
