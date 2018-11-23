package models.project

import java.net.{URI, URISyntaxException}

import play.twirl.api.Html

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.ProjectBase
import db.impl.model.common.Named
import db.impl.schema.PageTable
import db.{DbRef, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import discourse.OreDiscourseApi
import ore.OreConfig
import ore.project.ProjectOwned
import util.StringUtils._

import cats.data.OptionT
import cats.effect.IO
import com.google.common.base.Preconditions._
import com.vladsch.flexmark.ast.{MailLink, Node}
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
import com.vladsch.flexmark.util.options.MutableDataSet
import slick.lifted.TableQuery

/**
  * Represents a documentation page within a project.
  *
  * @param id           Page ID
  * @param createdAt    Timestamp of creation
  * @param projectId    Project ID
  * @param parentId     The parent page ID, -1 if none
  * @param name         Page name
  * @param slug         Page URL slug
  * @param contents    Markdown contents
  * @param isDeletable  True if can be deleted by the user
  */
case class Page(
    id: ObjId[Page] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: DbRef[Project],
    parentId: Option[DbRef[Page]],
    name: String,
    slug: String,
    isDeletable: Boolean = true,
    contents: String
) extends Model
    with Named {

  override type M = Page
  override type T = PageTable

  import models.project.Page._

  checkNotNull(this.name, "name cannot be null", "")
  checkNotNull(this.slug, "slug cannot be null", "")
  checkNotNull(this.contents, "contents cannot be null", "")

  def this(
      projectId: DbRef[Project],
      name: String,
      content: String,
      isDeletable: Boolean,
      parentId: Option[DbRef[Page]]
  ) = {
    this(
      projectId = projectId,
      name = compact(name),
      slug = slugify(name),
      contents = content.trim,
      isDeletable = isDeletable,
      parentId = parentId
    )
  }

  /**
    * Sets the Markdown contents of this Page and updates the associated forum
    * topic if this is the home page.
    *
    * @param contents Markdown contents
    */
  def updateContentsWithForum(
      contents: String
  )(implicit service: ModelService, config: OreConfig, forums: OreDiscourseApi): IO[Page] = {
    checkNotNull(contents, "null contents", "")
    checkArgument(
      (this.isHome && contents.length <= maxLength) || contents.length <= maxLengthPage,
      "contents too long",
      ""
    )
    val newPage = copy(contents = contents)
    if (!isDefined) IO.pure(newPage)
    else {
      for {
        updated <- service.update(newPage)
        project <- ProjectOwned[Page].project(this)
        // Contents were updated, update on forums
        _ <- if (this.name.equals(homeName) && project.topicId.isDefined) forums.updateProjectTopic(project)
        else IO.pure(false)
      } yield updated
    }
  }

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
  def parentProject(implicit projectBase: ProjectBase): OptionT[IO, Project] =
    projectBase.get(projectId)

  def parentPage(implicit service: ModelService): OptionT[IO, Page] =
    for {
      parent  <- OptionT.fromOption[IO](parentId)
      project <- parentProject
      page    <- project.pages.find(_.id === parent)
    } yield page

  /**
    * Get the /:parent/:child
    *
    * @return String
    */
  def fullSlug(parentPage: Option[Page]): String = parentPage.fold(slug)(pp => s"${pp.slug}/$slug")

  /**
    * Returns access to this Page's children (if any).
    *
    * @return Page's children
    */
  def children(implicit service: ModelService): ModelAccess[Page] =
    service.access(page => page.parentId.isDefined && page.parentId.get === this.id.value)

  def url(implicit project: Project, parentPage: Option[Page]): String =
    project.url + "/pages/" + this.fullSlug(parentPage)
}

object Page {

  implicit val query: ModelQuery[Page] =
    ModelQuery.from[Page](TableQuery[PageTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[Page] = (a: Page) => a.projectId

  private object ExternalLinkResolver {

    class Factory(config: OreConfig) extends LinkResolverFactory {
      override def getAfterDependents: Null = null

      override def getBeforeDependents: Null = null

      override def affectsGlobalScope() = false

      override def create(context: LinkResolverContext) = new ExternalLinkResolver(this.config)
    }

  }

  private class ExternalLinkResolver(config: OreConfig) extends LinkResolver {
    override def resolveLink(node: Node, context: LinkResolverContext, link: ResolvedLink): ResolvedLink = {
      if (link.getLinkType == LinkType.IMAGE || node.isInstanceOf[MailLink]) {
        link
      } else {
        link.withStatus(LinkStatus.VALID).withUrl(wrapExternal(link.getUrl))
      }
    }

    private def wrapExternal(urlString: String) = {
      try {
        val uri  = new URI(urlString)
        val host = uri.getHost
        if (uri.getScheme != null && host == null) {
          if (uri.getScheme == "mailto") {
            urlString
          } else {
            controllers.routes.Application.linkOut(urlString).toString
          }
        } else {
          val trustedUrlHosts = this.config.app.trustedUrlHosts
          val checkSubdomain = (trusted: String) =>
            trusted(0) == '.' && (host.endsWith(trusted) || host == trusted.substring(1))
          if (host == null || trustedUrlHosts.exists(trusted => trusted == host || checkSubdomain(trusted))) {
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

  private var linkResolver: Option[LinkResolverFactory] = None

  private lazy val (markdownParser, htmlRenderer) = {
    val options = new MutableDataSet()
      .set[java.lang.Boolean](HtmlRenderer.SUPPRESS_HTML, true)
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

}
