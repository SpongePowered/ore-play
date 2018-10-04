package ore.project

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import db.{ModelService, ObjectReference}
import models.project.Project
import models.user.role.ProjectRole
import ore.permission.scope.Scope
import ore.user.Member

/**
  * Represents a member of a [[Project]].
  *
  * @param project  Project this Member is a part of
  * @param userId   Member user ID
  */
class ProjectMember(val project: Project, override val userId: ObjectReference)(implicit users: UserBase)
    extends Member[ProjectRole](userId) {

  override def roles(implicit ec: ExecutionContext, service: ModelService): Future[Set[ProjectRole]] =
    this.user.flatMap(user => this.project.memberships.getRoles(user))

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit ec: ExecutionContext, service: ModelService): Future[ProjectRole] =
    this.roles.map(_.maxBy(_.roleType.trust))
}
