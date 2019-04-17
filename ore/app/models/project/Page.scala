package models.project

import scala.language.higherKinds

import play.twirl.api.Html

import db.impl.DefaultModelCompanion
import db.impl.OrePostgresDriver.api._
import db.impl.common.Named
import db.impl.schema.{PageTable, ProjectTableMain}
import discourse.OreDiscourseApi
import ore.OreConfig
import ore.db.access.{ModelView, QueryView}
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.markdown.MarkdownRenderer
import ore.project.ProjectOwned
import util.IOUtils
import util.StringUtils._
import util.syntax._

import cats.effect.IO
import cats.syntax.all._
import com.google.common.base.Preconditions._
import com.typesafe.scalalogging.LoggerTakingImplicit
import slick.lifted.TableQuery

/**
  * Represents a documentation page within a project.
  *
  * @param projectId    Project ID
  * @param parentId     The parent page ID, -1 if none
  * @param name         Page name
  * @param slug         Page URL slug
  * @param contents    Markdown contents
  * @param isDeletable  True if can be deleted by the user
  */
case class Page private (
    projectId: DbRef[Project],
    parentId: Option[DbRef[Page]],
    name: String,
    slug: String,
    isDeletable: Boolean,
    contents: String
) extends Named {
  import models.project.Page._

  checkNotNull(this.name, "name cannot be null", "")
  checkNotNull(this.slug, "slug cannot be null", "")
  checkNotNull(this.contents, "contents cannot be null", "")

  /**
    * Returns the HTML representation of this Page.
    *
    * @return HTML representation
    */
  def html(project: Option[Project])(implicit renderer: MarkdownRenderer): Html = {
    val settings = MarkdownRenderer.RenderSettings(
      linkEscapeChars = Some(" +<>"),
      linkPrefix = project.map(p => s"/${p.ownerName}/${p.slug}/pages/")
    )
    renderer.render(contents, settings)
  }

  /**
    * Returns true if this is the home page.
    *
    * @return True if home page
    */
  def isHome(implicit config: OreConfig): Boolean = this.name.equals(homeName) && parentId.isEmpty

  /**
    * Get Project associated with page.
    *
    * @return Optional Project
    */
  def parentProject[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, ProjectTableMain, Model[Project]]): QOptRet =
    view.get(projectId)

  def parentPage[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, PageTable, Model[Page]]): Option[QOptRet] =
    parentId.map(view.get)

  /**
    * Get the /:parent/:child
    *
    * @return String
    */
  def fullSlug(parentPage: Option[Page]): String = parentPage.fold(slug)(pp => s"${pp.slug}/$slug")
}

object Page extends DefaultModelCompanion[Page, PageTable](TableQuery[PageTable]) {

  def apply(
      projectId: DbRef[Project],
      name: String,
      content: String,
      isDeletable: Boolean,
      parentId: Option[DbRef[Page]]
  ): Page = Page(
    projectId = projectId,
    name = compact(name),
    slug = slugify(name),
    contents = content.trim,
    isDeletable = isDeletable,
    parentId = parentId
  )

  implicit val query: ModelQuery[Page] =
    ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[Page] = (a: Page) => a.projectId

  /**
    * The name of each Project's homepage.
    */
  def homeName(implicit config: OreConfig): String = config.ore.pages.homeName

  /**
    * The template body for the Home page.
    */
  def homeMessage(implicit config: OreConfig): String = config.ore.pages.homeMessage

  /**
    * The minimum amount of characters a page may have.
    */
  def minLength(implicit config: OreConfig): Int = config.ore.pages.minLen

  /**
    * The maximum amount of characters the home page may have.
    */
  def maxLength(implicit config: OreConfig): Int = config.ore.pages.maxLen

  /**
    * The maximum amount of characters a page may have.
    */
  def maxLengthPage(implicit config: OreConfig): Int = config.ore.pages.pageMaxLen

  /**
    * Returns a template for new Pages.
    *
    * @param title  Page title
    * @param body   Default message
    * @return       Template
    */
  def template(title: String, body: String = ""): String = "# " + title + "\n" + body

  implicit class PageModelOps(private val self: Model[Page]) extends AnyVal {

    /**
      * Sets the Markdown contents of this Page and updates the associated forum
      * topic if this is the home page.
      *
      * @param contents Markdown contents
      */
    def updateContentsWithForum[A](
        contents: String,
        logger: LoggerTakingImplicit[A]
    )(implicit service: ModelService, config: OreConfig, forums: OreDiscourseApi, mdc: A): IO[Model[Page]] = {
      checkNotNull(contents, "null contents", "")
      checkArgument(
        (self.isHome && contents.length <= maxLength) || contents.length <= maxLengthPage,
        "contents too long",
        ""
      )
      for {
        updated <- service.update(self)(_.copy(contents = contents))
        project <- ProjectOwned[Page].project(self)
        // Contents were updated, update on forums
        _ <- if (self.name.equals(homeName) && project.topicId.isDefined)
          forums
            .updateProjectTopic(project)
            .runAsync(IOUtils.logCallback("Failed to update page with forums", logger))
            .toIO
        else IO.unit
      } yield updated
    }

    /**
      * Returns access to this Page's children (if any).
      *
      * @return Page's children
      */
    def children[V[_, _]: QueryView](view: V[PageTable, Model[Page]]): V[PageTable, Model[Page]] =
      view.filterView(page => page.parentId.isDefined && page.parentId.get === self.id.value)
  }

}
