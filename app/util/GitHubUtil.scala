package util

import java.util.Base64

import play.api.libs.ws.WSClient

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._

object GitHubUtil {

  private val identifier       = "A-Za-z0-9-_"
  private val gitHubUrlPattern = s"""http(s)?://github.com/[$identifier]+/[$identifier]+(/)?""".r.pattern
  private val readmeApi        = "https://api.github.com/repos/%s/%s/readme"

  def isGitHubUrl(url: String): Boolean = gitHubUrlPattern.matcher(url).matches()

  def getReadme(user: String, project: String)(implicit ws: WSClient): EitherT[IO, String, String] =
    EitherT(
      IO.fromFuture(IO(ws.url(readmeApi.format(user, project)).get())).map { res =>
        if (res.status == 200) {
          (res.json \ "content")
            .validate[String]
            .asEither
            .leftMap(
              _.map(t => s"Failed to decode ${t._1.path} because ${t._2.map(_.message).mkString("\n")}").mkString("\n")
            )
        } else Left(res.body)
      }
    ).map(content => new String(Base64.getDecoder.decode(content.replace("\\n", "")), "UTF-8"))
}
