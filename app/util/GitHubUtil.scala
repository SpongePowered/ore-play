package util

import java.io.FileNotFoundException

import play.api.libs.json.{JsValue, Json}

import scala.io.Source

object GitHubUtil {

  private val identifier = "A-Za-z0-9-_"
  private val gitHubUrlPattern = s"""http(s)?://github.com/[$identifier]+/[$identifier]+(/)?""".r.pattern
  private val readmeApi = "https://api.github.com/repos/%s/%s/readme"

  def isGitHubUrl(url: String): Boolean = gitHubUrlPattern.matcher(url).matches()

  def getReadme(user: String, project: String): Option[String] = {
    try {
      val readmeApiJson: JsValue = Json.parse(Source.fromURL(readmeApi.format(user, project)).mkString)
      val url = (readmeApiJson \ "download_url").get.toString()
      val readme = Source.fromURL(url).mkString
      Some(readme)
    } catch {
      case _: FileNotFoundException => None
    }
  }

}
