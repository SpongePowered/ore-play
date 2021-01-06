package models.viewhelper

import scala.language.higherKinds

import controllers.sugar.Requests.ProjectRequest
import ore.data.Platform
import ore.data.project.Dependency
import ore.db.access.ModelView
import ore.db.{Model, ModelService}
import ore.models.project.{Channel, Project, Version}
import ore.models.user.User

import cats.syntax.all._
import cats.{MonadError, Parallel}

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
  def of[F[_]](request: ProjectRequest[_], version: Model[Version])(
      implicit service: ModelService[F],
      F: MonadError[F, Throwable],
      par: Parallel[F]
  ): F[VersionData] = {
    val depsF = version.dependencies.parTraverse(dep => dep.project.value.tupleLeft(dep))

    (version.channel, version.reviewer(ModelView.now(User)).sequence.subflatMap(identity).map(_.name).value, depsF)
      .parMapN {
        case (channel, approvedBy, deps) =>
          VersionData(request.data, version, channel, approvedBy, deps)
      }
  }
}
