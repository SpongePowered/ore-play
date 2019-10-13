package db.impl.query

import ore.db.DbRef
import ore.models.project.{Project, Version}
import ore.models.user.User

import com.github.tminglei.slickpg.InetString

import doobie._
import doobie.implicits._

object StatTrackerQueries extends WebDoobieOreProtocol {

  def addVersionDownload(
      projectId: DbRef[Project],
      versionId: DbRef[Version],
      address: InetString,
      cookie: String,
      userId: Option[DbRef[User]]
  ): Query0[String] =
    sql"""|SELECT add_version_download($projectId, $versionId, $address, $cookie, $userId)""".stripMargin.query[String]

  def addProjectView(
      projectId: DbRef[Project],
      address: InetString,
      cookie: String,
      userId: Option[DbRef[User]]
  ): Query0[String] =
    sql"""|SELECT add_project_view($projectId, $address, $cookie, $userId);""".stripMargin.query[String]

  val processVersionDownloads: Query0[Unit] = sql"SELECT update_project_versions_downloads();".query[Unit]
  val processProjectViews: Query0[Unit]     = sql"SELECT update_project_views();".query[Unit]

}
