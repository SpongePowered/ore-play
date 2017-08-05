package models.project

import java.sql.Timestamp

import com.google.common.base.Preconditions._
import com.vladsch.flexmark.ast.{Node, MailLink}
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.typographic.TypographicExtension
import com.vladsch.flexmark.html.{HtmlRenderer, LinkResolverFactory, LinkResolver}
import com.vladsch.flexmark.html.renderer.{NodeRendererContext, ResolvedLink, LinkType, LinkStatus}
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.PageTable
import db.impl.model.OreModel
import db.impl.schema.PageSchema
import db.impl.table.ModelKeys._
import db.{ModelFilter, Named}
import java.net.{URI, URISyntaxException}
import ore.permission.scope.ProjectScope
import ore.{OreConfig, Visitable}
import play.twirl.api.Html
import util.StringUtils._

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
case class Page(override val id: Option[Int] = None,
                override val createdAt: Option[Timestamp] = None,
                override val projectId: Int = -1,
                parentId: Int = -1,
                override val name: String,
                slug: String,
                isDeletable: Boolean = true,
                private var _contents: String)
                extends OreModel(id, createdAt)
                  with ProjectScope
                  with Named
                  with Visitable {

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
  def contents_=(_contents: String) = {
    checkNotNull(_contents, "null contents", "")
    checkArgument(_contents.length <= MaxLength, "contents too long", "")
    this._contents = _contents
    if (isDefined) {
      update(Contents)
      // Contents were updated, update on forums
      val project = this.project
      if (this.name.equals(HomeName) && project.topicId != -1)
        this.forums.updateProjectTopic(project)
    }
  }

  /**
    * Returns the HTML representation of this Page.
    *
    * @return HTML representation
    */
  def html: Html = Render(this.contents)

  /**
    * Returns true if this is the home page.
    *
    * @return True if home page
    */
  def isHome: Boolean = this.name.equals(HomeName)

  /**
    * Returns access to this Page's children (if any).
    *
    * @return Page's children
    */
  def children: ModelAccess[Page]
  = this.service.access[Page](classOf[Page], ModelFilter[Page](_.parentId === this.id.get))

  override def url: String = this.project.url + "/pages/" + this.slug
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(id = id, createdAt = theTime)

}

object Page {

  private object ExternalLinkResolver {

    class Factory(config: OreConfig) extends LinkResolverFactory {
      override def getAfterDependents() = null
      override def getBeforeDependents() = null
      override def affectsGlobalScope() = false
      override def create(context: NodeRendererContext) = new ExternalLinkResolver(this.config)
    }

  }

  private class ExternalLinkResolver(config: OreConfig) extends LinkResolver {
    override def resolveLink(node: Node, context: NodeRendererContext, link: ResolvedLink) = {
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
            controllers.routes.Application.linkOut(urlString).toString()
          }
        } else {
          val trustedUrlHosts = this.config.app.getStringSeq("trustedUrlHosts").get
          val checkSubdomain = (trusted: String) => trusted(0) == '.' && (host.endsWith(trusted) || host == trusted.substring(1));
          if (host == null || trustedUrlHosts.exists(trusted => trusted == host || checkSubdomain(trusted))) {
            urlString
          } else {
            controllers.routes.Application.linkOut(urlString).toString()
          }
        }
      } catch { case _: URISyntaxException => controllers.routes.Application.linkOut(urlString).toString() }
    }
  }

  private val (markdownParser, htmlRenderer) = {
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
        TypographicExtension.create()
      ))

    (Parser.builder(options).build(), HtmlRenderer.builder(options)
      // TODO: Get OreConfig using dependency injection or pass as parameter
      .linkResolverFactory(new ExternalLinkResolver.Factory(play.api.Play.current.injector.instanceOf(classOf[OreConfig])))
      .build())
  }

  def Render(markdown: String): Html = Html(htmlRenderer.render(markdownParser.parse(markdown)))

  /**
    * The name of each Project's homepage.
    */
  def HomeName(implicit config: OreConfig): String = config.pages.getString("home.name").get

  /**
    * The template body for the Home page.
    */
  def HomeMessage(implicit config: OreConfig): String = config.pages.getString("home.message").get

  /**
    * The minimum amount of characters a page may have.
    */
  def MinLength(implicit config: OreConfig): Int = config.pages.getInt("min-len").get

  /**
    * The maximum amount of characters a page may have.
    */
  def MaxLength(implicit config: OreConfig): Int = config.pages.getInt("max-len").get

  /**
    * Returns a template for new Pages.
    *
    * @param title  Page title
    * @param body   Default message
    * @return       Template
    */
  def Template(title: String, body: String = ""): String = "# " + title + "\n" + body

}
