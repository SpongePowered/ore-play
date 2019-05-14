package form.project

import ore.models.user.User
import ore.db.DbRef

/**
  * Concrete counterpart of [[TProjectRoleSetBuilder]].
  *
  * @param users Users for result set
  * @param roles Roles for result set
  */
case class ProjectRoleSetBuilder(users: List[DbRef[User]], roles: List[String]) extends TProjectRoleSetBuilder
