package models.viewhelper

import models.project.{Project, ProjectStates}
import models.user.User
import ore.permission._
import play.api.cache.AsyncCacheApi

import scala.concurrent.{ExecutionContext, Future}

/**
  * Holds ProjectData that is specific to a user
  */
object ScopedProjectData {

  def cacheKey(project: Project, user: User) = s"""project${project.id.get}foruser${user.id.get}"""

  def of(currentUser: Option[User], project: Project)(implicit ec: ExecutionContext, cache: AsyncCacheApi): Future[ScopedProjectData] = {
    currentUser.map { user =>
      for {
        projectOwner <- project.owner.user
        orgaOwner <- projectOwner.toMaybeOrganization

        canPostAsOwnerOrga <- user can PostAsOrganization in orgaOwner
        uProjectFlags <- user.hasUnresolvedFlagFor(project)
        starred <- project.stars.contains(user)
        watching <- project.watchers.contains(user)

        editPages <- user can EditPages in project map ((EditPages, _))
        editSettings <- user can EditSettings in project map ((EditSettings, _))
        editChannels <- user can EditChannels in project map ((EditChannels, _))
        editVersions <- user can EditVersions in project map ((EditVersions, _))
        states <- Future.sequence(ProjectStates.values.map(_.permission).map(p => user can p in project map ((p, _))))
      } yield {
        val perms = states + editPages + editSettings + editChannels + editVersions
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