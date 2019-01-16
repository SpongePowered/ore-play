package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.project.{Project, ProjectSettings}

class ProjectSettingsTable(tag: Tag) extends ModelTable[ProjectSettings](tag, "project_settings") {

  def projectId   = column[DbRef[Project]]("project_id")
  def homepage    = column[String]("homepage")
  def issues      = column[String]("issues")
  def source      = column[String]("source")
  def licenseName = column[String]("license_name")
  def licenseUrl  = column[String]("license_url")
  def forumSync   = column[Boolean]("forum_sync")
  def githubSync  = column[Boolean]("github_sync")

  override def * =
    mkProj(
      (id.?, createdAt.?, projectId, homepage.?, issues.?, source.?, licenseName.?, licenseUrl.?, forumSync, githubSync)
    )(
      mkTuple[ProjectSettings]()
    )
}
