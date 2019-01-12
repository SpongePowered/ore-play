package ore.project

import db.{DbRef, ModelService}
import models.project.Project
import models.user.User
import models.user.role.ProjectUserRole
import ore.user.{Member, UserOwned}

import cats.effect.IO

/**
  * Represents a member of a [[Project]].
  *
  * @param project  Project this Member is a part of
  * @param userId   Member user ID
  */
class ProjectMember(val project: Project, val userId: DbRef[User]) extends Member[ProjectUserRole] {

  override def roles(implicit service: ModelService): IO[Set[ProjectUserRole]] =
    UserOwned[ProjectMember].user(this).flatMap(user => this.project.memberships.getRoles(project, user))

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit service: ModelService): IO[ProjectUserRole] =
    this.roles.map(_.maxBy(_.role.trust))
}
object ProjectMember {
  implicit val isUserOwned: UserOwned[ProjectMember] = (a: ProjectMember) => a.userId
}
