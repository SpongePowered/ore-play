package form

import db.ObjectReference
import models.user.role.UserRoleModel
import ore.permission.role.Role

/**
  * Builds a set of [[UserRoleModel]]s based on input data.
  *
  * @tparam M RoleModel type
  */
trait RoleSetBuilder[M <: UserRoleModel] {

  /**
    * Returns the user IDs to use in building the set.
    *
    * @return User IDs
    */
  def users: List[ObjectReference]

  /**
    * Returns the role names to use in building the set.
    *
    * @return Role names
    */
  def roles: List[String]

  /**
    * Builds the result set from the form data.
    *
    * @return Result set
    */
  def build(): Set[M] =
    (for ((userId, i) <- this.users.zipWithIndex) yield {
      newRole(userId, Role.withValue(roles(i)))
    }).toSet

  /**
    * Creates a new role for the specified user ID and role type.
    *
    * @param userId User ID
    * @param role   Role type
    * @return       New role
    */
  def newRole(userId: ObjectReference, role: Role): M

}
