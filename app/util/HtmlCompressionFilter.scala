package util

import javax.inject.Inject

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import play.api.http.{HeaderNames, HttpEntity, HttpProtocol}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class HtmlCompressionFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  lazy val charset: String = "utf-8"
  val compressor = new HtmlCompressor

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).flatMap(result => {
      if (isCompressible(result)) {
        result.body match {
          case HttpEntity.Strict(data, contentType) =>
            if (contentType.isDefined && contentType.get.contains("html")) {
              val compressedData = compress(data)
              Future.successful(result.copy(body = HttpEntity.Strict(compressedData, contentType)))
            } else Future.successful(result)
          case HttpEntity.Streamed(data, _, contentType) =>
            if (contentType.isDefined && contentType.get.contains("html")) {
              for {
                bytes <- data.toMat(Sink.fold(ByteString())(_ ++ _))(Keep.right).run()
              } yield {
                val compressedData = compress(bytes)
                val length = compressedData.length.toLong
                result.copy(body = HttpEntity.Streamed(Source.single(compressedData), Some(length), contentType))
              }
            } else Future.successful(result)
          case _ =>
            Future.successful(result)
        }
      } else {
        Future.successful(result)
      }
    })
  }

  private def isCompressible(result: Result): Boolean = {
    val isChunked = result.header.headers.get(HeaderNames.TRANSFER_ENCODING).contains(HttpProtocol.CHUNKED)
    val isGzipped = result.header.headers.get(HeaderNames.CONTENT_ENCODING).contains("gzip")
    !(isChunked || isGzipped)
  }

  private def compress(data: ByteString): ByteString = {
    val dataAsString = data.decodeString(charset).trim
    val compressedData = compressor.compress(dataAsString)
    ByteString(compressedData.getBytes(charset))
  }

}
