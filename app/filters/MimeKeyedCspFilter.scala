package filters

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import play.api.Configuration
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.mvc.{Filter, RequestHeader, Result}
import play.filters.headers.SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MimeKeyedCspFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext, conf: Configuration) extends Filter {

    private type CspSpec = Map[String, Seq[String]]

    private val keyWords = Set("self", "unsafe-inline", "none")
    private val defaultMime: String = compileHeader(readCspSpec(conf.getConfig("filters.csp.default")))
    private val mimeLookup: Map[String, String] = {
        conf.getConfig("filters.csp.per-mime") match {
            case Some(perMime) => perMime.keys.map(k => (k, compileHeader(readCspSpec(perMime.getConfig(k))))).toMap
            case None => Map.empty
        }
    }

    private def readCspSpec(conf: Option[Configuration]): CspSpec = {
        conf match {
            case Some(defaults) =>
                defaults.keys.map { k =>
                    (k, defaults.getStringList(k).map(_.asScala).getOrElse(Seq.empty))
                }.toMap.filter(_._2.nonEmpty)
            case None => Map.empty
        }
    }

    private def compileHeader(section: CspSpec): String = {
        val parts = for ((key, values) <- section) yield {
            val combinedValue = values.map {
                case v if keyWords.contains(v) => s"'$v'"
                case v => v
            }.mkString(" ")
            s"${key.toLowerCase} $combinedValue"
        }

        parts.mkString("; ")
    }

    private def detectMime(request: RequestHeader): Option[String] = {
        request.path match {
            case p if p.endsWith(".svg") => Some("image/svg")
            case _ => None
        }
    }

    override def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
        next(request) map { result =>
            val contentType = result.header.headers.get(CONTENT_TYPE) match {
                case ct @ Some(_) => ct
                case _ => detectMime(request)
            }
            val csp = contentType.flatMap(mimeLookup.get).getOrElse(defaultMime)
            result.withHeaders(CONTENT_SECURITY_POLICY_HEADER -> csp)
        }
    }

}
