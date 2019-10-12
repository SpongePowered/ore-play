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
    sql"""|INSERT INTO project_versions_downloads_individual (created_at, project_id, version_id, address, cookie, user_id)
          |    VALUES (now(), $projectId, $versionId, $address, $cookie, $userId)
          |ON CONFLICT DO UPDATE SET address=excluded.address,
          |                          cookie=excluded.cookie,
          |                          user_id=excluded.user_id
          |                          RETURNING cookie;""".stripMargin.query[String]

  def addProjectView(
      projectId: DbRef[Project],
      address: InetString,
      cookie: String,
      userId: Option[DbRef[User]]
  ): Query0[String] =
    sql"""|INSERT INTO project_views_individual (created_at, project_id, address, cookie, user_id)
          |    VALUES (now(), $projectId, $address, $cookie, $userId)
          |ON CONFLICT DO UPDATE SET address=excluded.address,
          |                          cookie=excluded.cookie,
          |                          user_id=excluded.user_id
          |                          RETURNING cookie;""".stripMargin.query[String]

  val processVersionDownloads: Update0 = sql"CALL update_project_versions_downloads();".update
  val processProjectViews: Update0     = sql"CALL update_project_views();".update

}
