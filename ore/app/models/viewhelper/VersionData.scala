package models.viewhelper

import controllers.sugar.Requests.ProjectRequest
import db.{Model, ModelService}
import db.access.ModelView
import models.project.{Channel, Project, Version}
import models.user.User
import ore.Platform
import ore.project.Dependency

import cats.effect.{ContextShift, IO}
import cats.syntax.all._

case class VersionData(
    p: ProjectData,
    v: Model[Version],
    c: Model[Channel],
    approvedBy: Option[String], // Reviewer if present
    dependencies: Seq[(Dependency, Option[Model[Project]])]
) {

  def isRecommended: Boolean = p.project.recommendedVersionId.contains(v.id.value)

  def fullSlug = s"""${p.fullSlug}/versions/${v.versionString}"""

  /**
    * Filters out platforms from the dependencies
    * @return filtered dependencies
    */
  def filteredDependencies: Seq[(Dependency, Option[Model[Project]])] = {
    val platformIds = Platform.values.map(_.dependencyId)
    dependencies.filterNot(d => platformIds.contains(d._1.pluginId))
  }
}

object VersionData {
  def of[A](request: ProjectRequest[A], version: Model[Version])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[VersionData] = {
    import cats.instances.list._
    import cats.instances.option._
    val depsF = version.dependencies.parTraverse(dep => dep.project.value.tupleLeft(dep))

    (version.channel, version.reviewer(ModelView.now(User)).sequence.subflatMap(identity).map(_.name).value, depsF)
      .parMapN {
        case (channel, approvedBy, deps) =>
          VersionData(request.data, version, channel, approvedBy, deps)
      }
  }
}
