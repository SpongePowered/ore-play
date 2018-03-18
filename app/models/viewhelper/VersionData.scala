package models.viewhelper

import controllers.sugar.Requests.{OreRequest, ProjectRequest}
import db.ModelService
import models.project.{Channel, Project, Version}
import ore.project.Dependency
import ore.project.Dependency._
import play.api.cache.AsyncCacheApi
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

case class VersionData(p: ProjectData, v: Version, c: Channel,
                       approvedBy: Option[String], // Reviewer if present
                       dependencies: Seq[(Dependency, Option[Project])]
                      ) {

  def isRecommended = p.project.recommendedVersionId == v.id

  def fullSlug = s"""${p.fullSlug}/versions/${v.versionString}"""


  def filteredDependencies = {
    dependencies.filterNot(_._1.pluginId.equals(SpongeApiId))
      .filterNot(_._1.pluginId.equals(MinecraftId))
      .filterNot(_._1.pluginId.equals(ForgeId))
  }
}

object VersionData {
  def of[A](request: ProjectRequest[A], version: Version)(implicit cache: AsyncCacheApi, db: JdbcBackend#DatabaseDef, ec: ExecutionContext, service: ModelService): Future[VersionData] = {
    implicit val base = version.projectBase
    for {
      channel <- version.channel
      approvedBy <- version.reviewer
      deps <- Future.sequence(version.dependencies.map(dep => dep.project.map((dep, _))))
    } yield {
      VersionData(request.data,
        version,
        channel,
        approvedBy.map(_.name),
        deps)
    }
  }
}
