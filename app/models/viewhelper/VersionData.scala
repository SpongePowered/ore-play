package models.viewhelper

import controllers.sugar.Requests.ProjectRequest
import db.ModelService
import models.project.{Channel, Project, Version}
import ore.project.Dependency
import ore.project.Dependency._
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.ProjectBase
import util.syntax._
import util.instances.future._

case class VersionData(p: ProjectData, v: Version, c: Channel,
                       approvedBy: Option[String], // Reviewer if present
                       dependencies: Seq[(Dependency, Option[Project])]) {

  def isRecommended: Boolean = p.project.recommendedVersionId == v.id

  def fullSlug = s"""${p.fullSlug}/versions/${v.versionString}"""


  def filteredDependencies: Seq[(Dependency, Option[Project])] = {
    dependencies.filterNot(_._1.pluginId == SpongeApiId)
      .filterNot(_._1.pluginId == MinecraftId)
      .filterNot(_._1.pluginId == ForgeId)
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
