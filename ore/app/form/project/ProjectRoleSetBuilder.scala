package form.project

import ore.db.DbRef
import ore.models.user.User

/**
  * Concrete counterpart of [[TProjectRoleSetBuilder]].
  *
  * @param users Users for result set
  * @param roles Roles for result set
  */
case class ProjectRoleSetBuilder(users: List[DbRef[User]], roles: List[String]) extends TProjectRoleSetBuilder
