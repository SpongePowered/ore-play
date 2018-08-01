package ore

import models.user.role.RoleModel
import ore.permission.scope.ScopeSubject
import ore.user.{Member, MembershipDossier}
import scala.concurrent.{ExecutionContext, Future}

import db.ModelService

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable[M <: Member[_ <: RoleModel], Self] extends ScopeSubject {

  /**
    * Returns the owner of this object.
    *
    * @return Owner of object
    */
  def owner(implicit service: ModelService): M

  def ownerId: Int

  /**
   * Transfers ownership of this object to the given member.
   */
  def transferOwner(owner: M)(implicit ec: ExecutionContext, service: ModelService): Future[Self]

  /**
    * Returns this objects membership information.
    *
    * @return Memberships
    */
  def memberships(implicit service: ModelService): MembershipDossier

}
