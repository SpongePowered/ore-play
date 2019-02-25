package models.project

import scala.language.higherKinds

import java.net.{URI, URISyntaxException}

import play.twirl.api.Html

import db.access.{ModelView, QueryView}
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.Named
import db.impl.schema.{PageTable, ProjectTableMain}
import db._
import discourse.OreDiscourseApi
import ore.OreConfig
import ore.project.ProjectOwned
import util.StringUtils._
import util.syntax._
import java.util

import cats.effect.IO
import com.google.common.base.Preconditions._
import com.vladsch.flexmark.ast.MailLink
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.typographic.TypographicExtension
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension
import com.vladsch.flexmark.html.renderer._
import com.vladsch.flexmark.html.{HtmlRenderer, LinkResolver, LinkResolverFactory}
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.options.MutableDataSet
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
  def html(project: Option[Project])(implicit config: OreConfig): Html = renderPage(this, project)

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

  private object ExternalLinkResolver {

    // scalafix:off
    class Factory(config: OreConfig) extends LinkResolverFactory {
      override def getAfterDependents: util.Set[Class[_ <: LinkResolverFactory]] = null

      override def getBeforeDependents: util.Set[Class[_ <: LinkResolverFactory]] = null

      override def affectsGlobalScope(): Boolean = false

      override def create(context: LinkResolverContext) = new ExternalLinkResolver(this.config)
    }
    // scalafix:on
  }

  private class ExternalLinkResolver(config: OreConfig) extends LinkResolver {
    override def resolveLink(node: Node, context: LinkResolverContext, link: ResolvedLink): ResolvedLink = {
      if (link.getLinkType == LinkType.IMAGE || node.isInstanceOf[MailLink]) { //scalafix:ok
        link
      } else {
        link.withStatus(LinkStatus.VALID).withUrl(wrapExternal(link.getUrl))
      }
    }

    private def wrapExternal(urlString: String) = {
      try {
        val uri  = new URI(urlString)
        val host = uri.getHost
        if (uri.getScheme != null && host == null) { // scalafix:ok
          if (uri.getScheme == "mailto") {
            urlString
          } else {
            controllers.routes.Application.linkOut(urlString).toString
          }
        } else {
          val trustedUrlHosts = this.config.app.trustedUrlHosts
          val checkSubdomain = (trusted: String) =>
            trusted(0) == '.' && (host.endsWith(trusted) || host == trusted.substring(1))
          if (host == null || trustedUrlHosts.exists(trusted => trusted == host || checkSubdomain(trusted))) { // scalafix:ok
            urlString
          } else {
            controllers.routes.Application.linkOut(urlString).toString
          }
        }
      } catch {
        case _: URISyntaxException => controllers.routes.Application.linkOut(urlString).toString
      }
    }
  }

  //TODO: Move this to it's own class and make it a val
  private var linkResolver: Option[LinkResolverFactory] = None // scalafix:ok

  private lazy val (markdownParser, htmlRenderer) = {
    val options = new MutableDataSet()
      .set[java.lang.Boolean](HtmlRenderer.SUPPRESS_HTML, true)
      .set[java.lang.String](AnchorLinkExtension.ANCHORLINKS_TEXT_PREFIX, "<i class=\"fas fa-link\"></i>")
      .set[java.lang.String](AnchorLinkExtension.ANCHORLINKS_ANCHOR_CLASS, "headeranchor")
      .set[java.lang.Boolean](AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false)

      // GFM table compatibility
      .set[java.lang.Boolean](TablesExtension.COLUMN_SPANS, false)
      .set[java.lang.Boolean](TablesExtension.APPEND_MISSING_COLUMNS, true)
      .set[java.lang.Boolean](TablesExtension.DISCARD_EXTRA_COLUMNS, true)
      .set[java.lang.Boolean](TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
      .set(
        Parser.EXTENSIONS,
        java.util.Arrays.asList(
          AutolinkExtension.create(),
          AnchorLinkExtension.create(),
          StrikethroughExtension.create(),
          TaskListExtension.create(),
          TablesExtension.create(),
          TypographicExtension.create(),
          WikiLinkExtension.create()
        )
      )

    (
      Parser.builder(options).build(),
      HtmlRenderer
        .builder(options)
        .linkResolverFactory(linkResolver.get)
        .build()
    )
  }

  def render(markdown: String)(implicit config: OreConfig): Html = {
    // htmlRenderer is lazy loaded so linkResolver will exist upon loading
    if (linkResolver.isEmpty)
      linkResolver = Some(new ExternalLinkResolver.Factory(config))
    Html(htmlRenderer.render(markdownParser.parse(markdown)))
  }

  def renderPage(page: Page, project: Option[Project])(implicit config: OreConfig): Html = {
    if (linkResolver.isEmpty)
      linkResolver = Some(new ExternalLinkResolver.Factory(config))

    val options = new MutableDataSet().set[String](WikiLinkExtension.LINK_ESCAPE_CHARS, " +<>")

    if (project.isDefined)
      options.set[String](WikiLinkExtension.LINK_PREFIX, s"/${project.get.ownerName}/${project.get.slug}/pages/")

    Html(htmlRenderer.withOptions(options).render(markdownParser.parse(page.contents)))
  }

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
    def updateContentsWithForum(
        contents: String
    )(implicit service: ModelService, config: OreConfig, forums: OreDiscourseApi): IO[Model[Page]] = {
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
        _ <- if (self.name.equals(homeName) && project.topicId.isDefined) forums.updateProjectTopic(project)
        else IO.pure(false)
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
