package ore

import db.impl.access.UserBase
import models.user.role.RoleModel
import ore.permission.scope.ScopeSubject
import ore.user.{Member, MembershipDossier}

import scala.concurrent.ExecutionContext

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable[M <: Member[_ <: RoleModel]] extends ScopeSubject {

  /**
    * Returns the owner of this object.
    *
    * @return Owner of object
    */
  def owner: M

  /**
   * Transfers ownership of this object to the given member.
   */
  def transferOwner(owner: M)(implicit ec: ExecutionContext)

  /**
    * Returns this objects membership information.
    *
    * @return Memberships
    */
  def memberships: MembershipDossier

}
