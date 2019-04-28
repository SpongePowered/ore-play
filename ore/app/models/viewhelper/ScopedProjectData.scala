package models.viewhelper

import scala.language.higherKinds

import ore.db.access.ModelView
import ore.db.{Model, ModelService}
import ore.models.project.{Flag, Project}
import ore.models.user.User
import ore.permission.{Permission, _}

import cats.syntax.all._
import cats.{Applicative, Parallel}

/**
  * Holds ProjectData that is specific to a user
  */
object ScopedProjectData {

  def cacheKey(project: Model[Project], user: Model[User]) = s"""project${project.id}foruser${user.id}"""

  def of[F[_], G[_]](
      currentUser: Option[Model[User]],
      project: Model[Project]
  )(implicit service: ModelService[F], F: Applicative[F], par: Parallel[F, G]): F[ScopedProjectData] = {
    currentUser
      .map { user =>
        (
          user.hasUnresolvedFlagFor(project, ModelView.now(Flag)),
          project.stars.contains(user.id),
          project.watchers.contains(user.id),
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
      .getOrElse(F.pure(noScope))
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
