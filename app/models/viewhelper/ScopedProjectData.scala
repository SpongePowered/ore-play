package models.viewhelper

import db.access.ModelView
import db.{Model, ModelService}
import models.project.{Flag, Project}
import models.user.User
import ore.permission.{Permission, _}

import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * Holds ProjectData that is specific to a user
  */
object ScopedProjectData {

  def cacheKey(project: Model[Project], user: Model[User]) = s"""project${project.id}foruser${user.id}"""

  def of(
      currentUser: Option[Model[User]],
      project: Model[Project]
  )(implicit service: ModelService, cs: ContextShift[IO]): IO[ScopedProjectData] = {
    currentUser
      .map { user =>
        (
          user.hasUnresolvedFlagFor(project, ModelView.now(Flag)),
          project.stars.contains(user),
          project.watchers.contains(user),
          user.permissionsIn(project)
        ).parMapN {
          case (
              uProjectFlags,
              starred,
              watching,
              projectPerms
              ) =>
            ScopedProjectData(
              projectPerms.has(Permission.PostAsOrganization),
              uProjectFlags,
              starred,
              watching,
              projectPerms
            )
        }
      }
      .getOrElse(IO.pure(noScope))
  }

  val noScope = ScopedProjectData()
}

case class ScopedProjectData(
    canPostAsOwnerOrga: Boolean = false,
    uProjectFlags: Boolean = false,
    starred: Boolean = false,
    watching: Boolean = false,
    permissions: Permission = Permission.None
) {

  def perms(perm: Permission): Boolean = permissions.has(perm)

}
