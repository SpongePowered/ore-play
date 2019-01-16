package form.project

import db.DbRef
import models.user.User

/**
  * Represents the configurable Project settings that can be submitted via a
  * form.
  */
case class ProjectSettingsForm(
    categoryName: String,
    issues: String,
    source: String,
    licenseName: String,
    licenseUrl: String,
    description: String,
    users: List[DbRef[User]],
    roles: List[String],
    userUps: List[String],
    roleUps: List[String],
    updateIcon: Boolean,
    ownerId: Option[DbRef[User]],
    forumSync: Boolean,
    githubSync: Boolean
) extends TProjectRoleSetBuilder
