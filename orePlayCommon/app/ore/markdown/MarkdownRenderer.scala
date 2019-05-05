package ore.markdown

import play.twirl.api.Html

trait MarkdownRenderer {

  def render(s: String, settings: MarkdownRenderer.RenderSettings): Html

  def render(s: String): Html = render(s, MarkdownRenderer.RenderSettings(None, None))
}
object MarkdownRenderer {

  case class RenderSettings(
      linkEscapeChars: Option[String],
      linkPrefix: Option[String]
  )
}
