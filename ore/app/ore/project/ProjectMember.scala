package ore.project

import models.project.Project
import models.user.User
import models.user.role.ProjectUserRole
import ore.db.{DbRef, Model, ModelService}
import ore.user.{Member, UserOwned}

import cats.effect.IO

/**
  * Represents a member of a [[Project]].
  *
  * @param project  Project this Member is a part of
  * @param userId   Member user ID
  */
class ProjectMember(val project: Model[Project], val userId: DbRef[User]) extends Member[ProjectUserRole] {

  override def roles(implicit service: ModelService): IO[Set[Model[ProjectUserRole]]] =
    UserOwned[ProjectMember].user(this).flatMap(user => this.project.memberships.getRoles(project, user))

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit service: ModelService): IO[Model[ProjectUserRole]] =
    this.roles.map(_.maxBy(_.role.permissions: Long)) ////This is terrible, but probably works
}
object ProjectMember {
  implicit val isUserOwned: UserOwned[ProjectMember] = (a: ProjectMember) => a.userId
}
