package ore.markdown

import java.net.{URI, URISyntaxException}
import java.util

import play.twirl.api.Html

import ore.OreConfig

import com.vladsch.flexmark.ast.MailLink
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.typographic.TypographicExtension
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension
import com.vladsch.flexmark.html.renderer.{LinkResolverBasicContext, LinkStatus, LinkType, ResolvedLink}
import com.vladsch.flexmark.html.{HtmlRenderer, LinkResolver, LinkResolverFactory}
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension

class FlexmarkRenderer(config: OreConfig) extends MarkdownRenderer {
  private val options = new MutableDataSet()
    .set[java.lang.Boolean](HtmlRenderer.SUPPRESS_HTML, true)
    .set[java.lang.String](AnchorLinkExtension.ANCHORLINKS_TEXT_PREFIX, "<i class=\"fas fa-link\"></i>")
    .set[java.lang.String](AnchorLinkExtension.ANCHORLINKS_ANCHOR_CLASS, "headeranchor")
    .set[java.lang.Boolean](AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false)
    // GFM table compatibility
    .set[java.lang.Boolean](TablesExtension.COLUMN_SPANS, false)
    .set[java.lang.Boolean](TablesExtension.APPEND_MISSING_COLUMNS, true)
    .set[java.lang.Boolean](TablesExtension.DISCARD_EXTRA_COLUMNS, true)
    .set[java.lang.Boolean](TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
    .set[java.util.Collection[Extension]](
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

  private val markdownParser = Parser.builder(options).build()

  override def render(s: String, settings: MarkdownRenderer.RenderSettings): Html = {
    val options = new MutableDataSet(this.options)

    settings.linkEscapeChars.foreach(chars => options.set[String](WikiLinkExtension.LINK_ESCAPE_CHARS, chars))

    settings.linkPrefix.foreach(prefix => options.set[String](WikiLinkExtension.LINK_PREFIX, prefix))

    val htmlRenderer = HtmlRenderer
      .builder(options)
      .linkResolverFactory(new FlexmarkRenderer.ExternalLinkResolver.Factory(config))
      .build()

    Html(htmlRenderer.render(markdownParser.parse(s)))
  }
}
object FlexmarkRenderer {

  object ExternalLinkResolver {

    // scalafix:off
    private[FlexmarkRenderer] class Factory(config: OreConfig) extends LinkResolverFactory {
      override def getAfterDependents: util.Set[Class[_]] = null

      override def getBeforeDependents: util.Set[Class[_]] = null

      override def affectsGlobalScope(): Boolean = false

      override def apply(context: LinkResolverBasicContext): LinkResolver = new ExternalLinkResolver(this.config)
    }
    // scalafix:on
  }

  private class ExternalLinkResolver(config: OreConfig) extends LinkResolver {

    override def resolveLink(node: Node, context: LinkResolverBasicContext, link: ResolvedLink): ResolvedLink = {
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
}
