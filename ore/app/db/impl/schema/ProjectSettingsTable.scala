package db.impl.schema

import db.impl.OrePostgresDriver.api._
import models.project.{Project, ProjectSettings}
import ore.db.DbRef

class ProjectSettingsTable(tag: Tag) extends ModelTable[ProjectSettings](tag, "project_settings") {

  def projectId   = column[DbRef[Project]]("project_id")
  def homepage    = column[String]("homepage")
  def issues      = column[String]("issues")
  def source      = column[String]("source")
  def licenseName = column[String]("license_name")
  def licenseUrl  = column[String]("license_url")
  def forumSync   = column[Boolean]("forum_sync")

  override def * =
    (id.?, createdAt.?, (projectId, homepage.?, issues.?, source.?, licenseName.?, licenseUrl.?, forumSync)) <> (mkApply(
      (ProjectSettings.apply _).tupled
    ), mkUnapply(ProjectSettings.unapply))
}
