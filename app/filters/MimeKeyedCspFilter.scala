package filters

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.mvc.{Filter, RequestHeader, Result}
import play.filters.headers.SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MimeKeyedCspFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext, conf: Configuration) extends Filter {

    private type CspSpec = Map[String, Seq[String]]

    private val keyWords = Set("self", "unsafe-inline", "none")
    private val defaultMime: String = compileHeader(conf.get[CspSpec]("filters.csp.default"))
    private val mimeLookup: Map[String, String] = conf.getOptional[Map[String, CspSpec]]("filters.csp.per-mime") match {
        case Some(perMime) => perMime.map { case (mime, spec) => (mime, compileHeader(spec)) }
        case None => Map.empty
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
