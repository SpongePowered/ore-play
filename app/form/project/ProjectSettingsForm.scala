package form.project

import db.ObjectReference

/**
  * Represents the configurable Project settings that can be submitted via a
  * form.
  */
case class ProjectSettingsForm(categoryName: String,
                               issues: String,
                               source: String,
                               licenseName: String,
                               licenseUrl: String,
                               description: String,
                               override val users: List[Int],
                               override val roles: List[String],
                               userUps: List[String],
                               roleUps: List[String],
                               updateIcon: Boolean,
                               ownerId: Option[ObjectReference],
                               forumSync: Boolean)
                               extends TProjectRoleSetBuilder
