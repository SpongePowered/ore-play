package models.viewhelper

import scala.concurrent.{ExecutionContext, Future}

import db.ModelService
import models.project.{Project, Visibility}
import models.user.User
import ore.permission._

import cats.instances.future._
import cats.syntax.all._

/**
  * Holds ProjectData that is specific to a user
  */
object ScopedProjectData {

  def cacheKey(project: Project, user: User) = s"""project${project.id.value}foruser${user.id.value}"""

  def of(
      currentUser: Option[User],
      project: Project
  )(implicit ec: ExecutionContext, service: ModelService): Future[ScopedProjectData] = {
    currentUser
      .map { user =>
        (
          project.owner.user
            .flatMap(_.toMaybeOrganization.value)
            .flatMap(orgaOwner => user.can(PostAsOrganization) in orgaOwner),
          user.hasUnresolvedFlagFor(project),
          project.stars.contains(user),
          project.watchers.contains(user),
          user.trustIn(project),
        ).mapN {
          case (
              canPostAsOwnerOrga,
              uProjectFlags,
              starred,
              watching,
              projectTrust
              ) =>
            val perms = EditPages +: EditSettings +: EditChannels +: EditVersions +: UploadVersions +: Visibility.values
              .map(_.permission)
            val permMap = user.can.asMap(projectTrust)(perms: _*)
            ScopedProjectData(canPostAsOwnerOrga, uProjectFlags, starred, watching, permMap)
        }
      }
      .getOrElse(Future.successful(noScope))
  }

  val noScope = ScopedProjectData()
}

case class ScopedProjectData(
    canPostAsOwnerOrga: Boolean = false,
    uProjectFlags: Boolean = false,
    starred: Boolean = false,
    watching: Boolean = false,
    permissions: Map[Permission, Boolean] = Map.empty
) {

  def perms(perm: Permission): Boolean = permissions.getOrElse(perm, false)

}
