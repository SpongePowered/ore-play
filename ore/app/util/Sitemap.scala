package util

import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scala.collection.immutable
import scala.xml.{Elem, NodeSeq, XML}

import play.api.mvc.{Call, RequestHeader}

import enumeratum.values.{StringEnum, StringEnumEntry}

object Sitemap {
  sealed abstract class ChangeFreq(val value: String) extends StringEnumEntry
  object ChangeFreq extends StringEnum[ChangeFreq] {
    case object Always  extends ChangeFreq("always")
    case object Hourly  extends ChangeFreq("hourly")
    case object Daily   extends ChangeFreq("daily")
    case object Weekly  extends ChangeFreq("weekly")
    case object Monthly extends ChangeFreq("monthly")
    case object Yearly  extends ChangeFreq("yearly")
    case object Never   extends ChangeFreq("never")

    override def values: immutable.IndexedSeq[ChangeFreq] = findValues
  }

  private val xmlDatetimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

  case class Entry(
      loc: Call,
      lastmod: Option[LocalDateTime] = None,
      changeFreq: Option[ChangeFreq] = None,
      priority: Option[Double] = None
  ) {

    def toXML(implicit request: RequestHeader): Elem =
      <url>
        <loc>{loc.absoluteURL()}</loc>
        {lastmod.fold(NodeSeq.Empty)(v => <lastmod>{xmlDatetimeFormat.format(v)}</lastmod>)}
        {changeFreq.fold(NodeSeq.Empty)(v => <changefreq>{v.value}</changefreq>)}
        {priority.fold(NodeSeq.Empty)(v => <priority>{v}</priority>)}
      </url>
  }

  def apply(entries: Entry*)(implicit request: RequestHeader): Elem = {
    require(entries.length < 50000)

    <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
      {entries.map(_.toXML)}
    </urlset>
  }

  def asString(entries: Entry*)(implicit requests: RequestHeader): String = {
    val xml = apply(entries: _*)

    val writer = new StringWriter()
    XML.write(writer, xml, "UTF-8", xmlDecl = true, null)

    writer.toString
  }
}
