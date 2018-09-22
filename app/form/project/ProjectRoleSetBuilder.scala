package form.project

import db.ObjectReference

/**
  * Concrete counterpart of [[TProjectRoleSetBuilder]].
  *
  * @param users Users for result set
  * @param roles Roles for result set
  */
case class ProjectRoleSetBuilder(users: List[ObjectReference], roles: List[String]) extends TProjectRoleSetBuilder
