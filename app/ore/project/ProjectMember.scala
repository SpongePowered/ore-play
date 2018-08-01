package ore.project

import db.impl.access.UserBase
import models.project.Project
import models.user.role.ProjectRole
import ore.permission.scope.Scope
import ore.user.Member
import scala.concurrent.{ExecutionContext, Future}

import db.ModelService

/**
  * Represents a member of a [[Project]].
  *
  * @param project  Project this Member is a part of
  * @param userId   Member user ID
  */
class ProjectMember(val project: Project, override val userId: Int)(implicit users: UserBase)
                    extends Member[ProjectRole](userId) {

  override def roles(implicit ec: ExecutionContext, service: ModelService): Future[Set[ProjectRole]] =
    this.user.flatMap(user => this.project.memberships.getRoles(user))
  override val scope: Scope = this.project.scope

  /*
  override def toString: String = MoreObjects.toStringHelper(this).add("name", this.user.fullName).toString
  override def hashCode: Int = Objects.hashCode(this.project, this.user.fullName)

  override def equals(o: Any): Boolean = {
    o match {
      case that: ProjectMember => that.project.equals(this.project) && that.userId.equals(this.userId)
      case _ => false
    }
  }
  */
  /**
    * Returns the Member's top role.
    *
    * @return Top role
    */
  override def headRole(implicit ec: ExecutionContext, service: ModelService): Future[ProjectRole] = this.roles.map(_.maxBy(_.roleType.trust))
}

