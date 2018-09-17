package form.project

import db.ObjectReference

/**
  * Concrete counterpart of [[TProjectRoleSetBuilder]].
  *
  * @param users Users for result set
  * @param roles Roles for result set
  */
case class ProjectRoleSetBuilder(override val users: List[ObjectReference],
                                 override val roles: List[String])
                                 extends TProjectRoleSetBuilder
