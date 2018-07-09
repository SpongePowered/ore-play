package models.project

import java.net.{URI, URISyntaxException}

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
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.PageTable
import db.impl.model.OreModel
import db.impl.schema.PageSchema
import db.impl.table.ModelKeys._
import db.{ModelFilter, Named, ObjectId, ObjectReference, ObjectTimestamp}
import ore.OreConfig
import ore.permission.scope.ProjectScope
import play.twirl.api.Html
import util.StringUtils._
import util.instances.future._
import util.functional.OptionT

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a documentation page within a project.
  *
  * @param id           Page ID
  * @param createdAt    Timestamp of creation
  * @param projectId    Project ID
  * @param parentId     The parent page ID, -1 if none
  * @param name         Page name
  * @param slug         Page URL slug
  * @param _contents    Markdown contents
  * @param isDeletable  True if can be deleted by the user
  */
case class Page(override val id: ObjectId = ObjectId.Uninitialized,
                override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                override val projectId: ObjectReference = -1,
                parentId: ObjectReference = -1,
                override val name: String,
                slug: String,
                isDeletable: Boolean = true,
                private var _contents: String)
                extends OreModel(id, createdAt)
                  with ProjectScope
                  with Named {

  override type M = Page
  override type T = PageTable
  override type S = PageSchema

  import models.project.Page._

  checkNotNull(this.projectId != -1, "invalid project id", "")
  checkNotNull(this.name, "name cannot be null", "")
  checkNotNull(this.slug, "slug cannot be null", "")
  checkNotNull(this._contents, "contents cannot be null", "")

  def this(projectId: Int, name: String, content: String, isDeletable: Boolean, parentId: Int) = {
    this(projectId=projectId, name=compact(name), slug=slugify(name),
      _contents=content.trim, isDeletable=isDeletable, parentId = parentId)
  }

  /**
    * Returns the Markdown contents of this Page.
    *
    * @return Markdown contents
    */
  def contents: String = this._contents

  /**
    * Sets the Markdown contents of this Page and updates the associated forum
    * topic if this is the home page.
    *
    * @param _contents Markdown contents
    */
  def setContents(_contents: String)(implicit ec: ExecutionContext): Future[Page] = {
    checkNotNull(_contents, "null contents", "")
    checkArgument((this.isHome && _contents.length <= MaxLength) || _contents.length <= MaxLengthPage, "contents too long", "")
    this._contents = _contents
    if (!isDefined) Future.successful(this)
    else {
      for {
        _ <- update(Contents)
        project <- this.project
        // Contents were updated, update on forums
        _ <- if (this.name.equals(HomeName) && project.topicId != -1) this.forums.updateProjectTopic(project) else Future.successful(false)
      } yield {
        this
      }
    }
  }

  /**
    * Returns the HTML representation of this Page.
    *
    * @return HTML representation
    */
  def html(project: Option[Project]): Html = RenderPage(this, project)

  /**
    * Returns true if this is the home page.
    *
    * @return True if home page
    */
  def isHome: Boolean = this.name.equals(HomeName) && parentId == -1

  /**
    * Get Project associated with page.
    *
    * @return Optional Project
    */
  def parentProject(implicit ec: ExecutionContext): OptionT[Future, Project] = this.projectBase.get(projectId)

  /**
    *
    * @return
    */
  def parentPage(implicit ec: ExecutionContext): OptionT[Future, Page] = {
    parentProject.flatMap(_.pages.find(ModelFilter[Page](_.id === parentId).fn))
  }

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
  def children: ModelAccess[Page]
  = this.service.access[Page](classOf[Page], ModelFilter[Page](_.parentId === this.id.value))

  def url(implicit project: Project, parentPage: Option[Page]) : String = project.url + "/pages/" + this.fullSlug(parentPage)

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Page = this.copy(id = id, createdAt = theTime)
}

object Page {

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
      if (link.getLinkType.equals(LinkType.IMAGE) || node.isInstanceOf[MailLink]) {
        link
      } else {
        link.withStatus(LinkStatus.VALID).withUrl(wrapExternal(link.getUrl))
      }
    }

    private def wrapExternal(urlString: String) = {
      try {
        val uri = new URI(urlString)
        val host = uri.getHost
        if (uri.getScheme != null && host == null) {
          if (uri.getScheme == "mailto") {
            urlString
          } else {
            controllers.routes.Application.linkOut(urlString).toString
          }
        } else {
          val trustedUrlHosts = this.config.app.get[Seq[String]]("trustedUrlHosts")
          val checkSubdomain = (trusted: String) => trusted(0) == '.' && (host.endsWith(trusted) || host == trusted.substring(1));
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

      .set(Parser.EXTENSIONS, java.util.Arrays.asList(
        AutolinkExtension.create(),
        AnchorLinkExtension.create(),
        StrikethroughExtension.create(),
        TaskListExtension.create(),
        TablesExtension.create(),
        TypographicExtension.create(),
        WikiLinkExtension.create()
      ))

    (Parser.builder(options).build(), HtmlRenderer.builder(options)
      .linkResolverFactory(linkResolver.get)
      .build())
  }

  def Render(markdown: String)(implicit config: OreConfig): Html = {
    // htmlRenderer is lazy loaded so linkResolver will exist upon loading
    if (linkResolver.isEmpty)
      linkResolver = Some(new ExternalLinkResolver.Factory(config))
    Html(htmlRenderer.render(markdownParser.parse(markdown)))
  }

  def RenderPage(page: Page, project: Option[Project])(implicit config: OreConfig): Html = {
    if (linkResolver.isEmpty)
      linkResolver = Some(new ExternalLinkResolver.Factory(config))

    val options = new MutableDataSet().set[String](WikiLinkExtension.LINK_ESCAPE_CHARS, " +<>")

    if (project.isDefined)
      options.set[String](WikiLinkExtension.LINK_PREFIX, s"/${project.get.ownerName}/${project.get.slug}/pages/")

    Html(htmlRenderer.withOptions(options).render(markdownParser.parse(page._contents)))
  }

  /**
    * The name of each Project's homepage.
    */
  def HomeName(implicit config: OreConfig): String = config.pages.get[String]("home.name")

  /**
    * The template body for the Home page.
    */
  def HomeMessage(implicit config: OreConfig): String = config.pages.get[String]("home.message")

  /**
    * The minimum amount of characters a page may have.
    */
  def MinLength(implicit config: OreConfig): Int = config.pages.get[Int]("min-len")

  /**
    * The maximum amount of characters the home page may have.
    */
  def MaxLength(implicit config: OreConfig): Int = config.pages.get[Int]("max-len")

  /**
    * The maximum amount of characters a page may have.
    */
  def MaxLengthPage(implicit config: OreConfig): Int = config.pages.get[Int]("page.max-len")

  /**
    * Returns a template for new Pages.
    *
    * @param title  Page title
    * @param body   Default message
    * @return       Template
    */
  def Template(title: String, body: String = ""): String = "# " + title + "\n" + body

}
