package util

import java.io.FileNotFoundException

import scala.io.Source

object GitHubUtil {

  private val identifier = "A-Za-z0-9-_"
  private val gitHubUrlPattern = s"""http(s)?://github.com/[$identifier]+/[$identifier]+(/)?""".r.pattern
  private val readmeUrl = "https://raw.githubusercontent.com/%s/%s/master/README.md"

  def isGitHubUrl(url: String): Boolean = gitHubUrlPattern.matcher(url).matches()

  def getReadme(user: String, project: String): Option[String] = {
    try {
      val readme = Source.fromURL(readmeUrl.format(user, project)).mkString
      Some(readme)
    } catch {
      case _: FileNotFoundException => None
    }
  }

}
