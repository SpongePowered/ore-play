package models.viewhelper

import scala.concurrent.{ExecutionContext, Future}

import controllers.sugar.Requests.ProjectRequest
import db.ModelService
import models.project.{Channel, Project, Version}
import ore.Platform
import ore.project.Dependency

import cats.instances.future._
import cats.syntax.all._

case class VersionData(
    p: ProjectData,
    v: Version,
    c: Channel,
    approvedBy: Option[String], // Reviewer if present
    dependencies: Seq[(Dependency, Option[Project])]
) {

  def isRecommended: Boolean = p.project.recommendedVersionId.contains(v.id.value)

  def fullSlug = s"""${p.fullSlug}/versions/${v.versionString}"""

  /**
    * Filters out platforms from the dependencies
    * @return filtered dependencies
    */
  def filteredDependencies: Seq[(Dependency, Option[Project])] =
    dependencies.filterNot(d => Platform.values.map(_.dependencyId).contains(d._1.pluginId))
}

object VersionData {
  def of[A](request: ProjectRequest[A], version: Version)(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Future[VersionData] = {
    val depsFut = Future.sequence(version.dependencies.map(dep => dep.project.value.map((dep, _))))

    (version.channel, version.reviewer.map(_.name).value, depsFut).mapN {
      case (channel, approvedBy, deps) =>
        VersionData(request.data, version, channel, approvedBy, deps)
    }
  }
}
