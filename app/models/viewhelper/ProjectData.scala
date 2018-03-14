package models.viewhelper

import models.admin.VisibilityChange
import models.project.{Project, ProjectSettings, Version}
import models.user.User
import ore.permission.Permission
import play.twirl.api.Html


// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

// TODO perms in project ; EditPages ; EditSettings (currently in projects.view.html


case class ProjectData(headerData: HeaderData,
                       p: Project,
                       versions: Int, // project.versions.size
                       settings: ProjectSettings,
                       permissions: Map[Permission, Boolean],
                       uProjectFlags: Boolean, // TODO user.hasUnresolvedFlagFor(project)
                       starred: Boolean,
                       watching: Boolean,
                       projectLogSize: Int,
                       flagCount: Int, // flags.size
                       noteCount: Int, // getNotes.size
                       lastVisibilityChange: Option[VisibilityChange],
                       lastVisibilityChangeUser: String // users.get(project.lastVisibilityChange.get.createdBy.get).map(_.username).getOrElse("Unknown")
                      ) {

  def global = headerData

  def hasUser = global.hasUser
  def currentUser = global.currentUser

  def apply(permission: Permission): Boolean = permissions(permission)

  def visibility = p.visibility

  def fullSlug = s"""/${p.ownerName}/${p.slug}"""

  def renderVisibilityChange = lastVisibilityChange.map(_.renderComment())
}
