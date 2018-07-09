package models.viewhelper

import models.project.{Project, VisibilityTypes}
import models.user.User
import ore.permission._
import play.api.cache.AsyncCacheApi

import scala.concurrent.{ExecutionContext, Future}

import util.syntax._

/**
  * Holds ProjectData that is specific to a user
  */
object ScopedProjectData {

  def cacheKey(project: Project, user: User) = s"""project${project.id.value}foruser${user.id.value}"""

  def of(currentUser: Option[User], project: Project)(implicit ec: ExecutionContext, cache: AsyncCacheApi): Future[ScopedProjectData] = {
    currentUser.map { user =>
      (
        project.owner.user.flatMap(_.toMaybeOrganization.value).flatMap(orgaOwner => user can PostAsOrganization in orgaOwner),

        user.hasUnresolvedFlagFor(project),
        project.stars.contains(user),
        project.watchers.contains(user),

        user can EditPages in project map ((EditPages, _)),
        user can EditSettings in project map ((EditSettings, _)),
        user can EditChannels in project map ((EditChannels, _)),
        user can EditVersions in project map ((EditVersions, _)),
        user can UploadVersions in project map ((UploadVersions, _)),
        Future.sequence(VisibilityTypes.values.map(_.permission).map(p => user can p in project map ((p, _))))
      ).parMapN {
        case (canPostAsOwnerOrga, uProjectFlags, starred, watching, editPages, editSettings, editChannels, editVersions, uploadVersions, visibilities) =>
          val perms = visibilities + editPages + editSettings + editChannels + editVersions + uploadVersions
          ScopedProjectData(canPostAsOwnerOrga, uProjectFlags, starred, watching, perms.toMap)
      }
    } getOrElse Future.successful(noScope)
  }

  val noScope = ScopedProjectData()

}

case class ScopedProjectData(canPostAsOwnerOrga: Boolean = false,
                             uProjectFlags: Boolean = false,
                             starred: Boolean = false,
                             watching: Boolean = false,
                             permissions: Map[Permission, Boolean] = Map.empty
                            ) {

  def perms(perm: Permission): Boolean = permissions.getOrElse(perm, false)

}
