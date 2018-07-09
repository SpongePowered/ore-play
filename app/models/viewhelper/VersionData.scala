package models.viewhelper

import controllers.sugar.Requests.ProjectRequest
import db.ModelService
import db.impl.access.ProjectBase
import models.project.{Channel, Project, Version}
import ore.Platforms
import ore.project.Dependency
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend
import util.instances.future._
import util.syntax._

import scala.concurrent.{ExecutionContext, Future}

case class VersionData(p: ProjectData, v: Version, c: Channel,
                       approvedBy: Option[String], // Reviewer if present
                       dependencies: Seq[(Dependency, Option[Project])]) {

  def isRecommended: Boolean = p.project.recommendedVersionId.contains(v.id.value)

  def fullSlug = s"""${p.fullSlug}/versions/${v.versionString}"""

  /**
    * Filters out platforms from the dependencies
    * @return filtered dependencies
    */
  def filteredDependencies: Seq[(Dependency, Option[Project])] = {
    dependencies.filterNot(d => Platforms.values.map(_.dependencyId).contains(d._1.pluginId))
  }
}

object VersionData {
  def of[A](request: ProjectRequest[A], version: Version)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[VersionData] = {
    implicit val base: ProjectBase = version.projectBase
    val depsFut = Future.sequence(version.dependencies.map(dep => dep.project.value.map((dep, _))))

    (version.channel, version.reviewer.map(_.name).value, depsFut).parMapN {
      case (channel, approvedBy, deps) =>
        VersionData(request.data, version, channel, approvedBy, deps)
    }
  }
}
