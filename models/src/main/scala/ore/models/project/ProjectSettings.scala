package ore.models.project

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.ProjectSettingsTable
import ore.db.{DbRef, ModelQuery}

import slick.lifted.TableQuery

/**
  * Represents a [[Project]]'s settings.
  *
  * @param projectId    ID of project settings belong to
  * @param homepage     Project homepage
  * @param issues      Project issues URL
  * @param source      Project source URL
  * @param support     Project support URL
  * @param licenseName Project license name
  * @param licenseUrl  Project license URL
  */
case class ProjectSettings(
    projectId: DbRef[Project],
    homepage: Option[String] = None,
    issues: Option[String] = None,
    source: Option[String] = None,
    support: Option[String] = None,
    licenseName: Option[String] = None,
    licenseUrl: Option[String] = None,
    forumSync: Boolean = true
)
object ProjectSettings
    extends DefaultModelCompanion[ProjectSettings, ProjectSettingsTable](TableQuery[ProjectSettingsTable]) {

  implicit val query: ModelQuery[ProjectSettings] = ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[ProjectSettings] = (a: ProjectSettings) => a.projectId
}
