package ore.project

import scala.concurrent.{ExecutionContext, Future}

import db.{ModelService, ObjectReference}
import models.project.Project
import models.user.role.ProjectRole
import ore.user.Member

/**
  * Represents a member of a [[Project]].
  *
  * @param project  Project this Member is a part of
  * @param userId   Member user ID
  */
class ProjectMember(val project: Project, val userId: ObjectReference) extends Member[ProjectRole] {

  override def roles(implicit ec: ExecutionContext, service: ModelService): Future[Set[ProjectRole]] =
    this.user.flatMap(user => this.project.memberships.getRoles(project, user))

  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit ec: ExecutionContext, service: ModelService): Future[ProjectRole] =
    this.roles.map(_.maxBy(_.roleType.trust))
}
