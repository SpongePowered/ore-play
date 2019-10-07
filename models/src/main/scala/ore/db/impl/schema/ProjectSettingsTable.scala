package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Project, ProjectSettings}

class ProjectSettingsTable(tag: Tag) extends ModelTable[ProjectSettings](tag, "project_settings") {

  def projectId   = column[DbRef[Project]]("project_id")
  def homepage    = column[String]("homepage")
  def issues      = column[String]("issues")
  def source      = column[String]("source")
  def support     = column[String]("support")
  def licenseName = column[String]("license_name")
  def licenseUrl  = column[String]("license_url")
  def forumSync   = column[Boolean]("forum_sync")

  override def * =
    (id.?, createdAt.?, (projectId, homepage.?, issues.?, source.?, support.?, licenseName.?, licenseUrl.?, forumSync)) <> (mkApply(
      (ProjectSettings.apply _).tupled
    ), mkUnapply(ProjectSettings.unapply))
}
