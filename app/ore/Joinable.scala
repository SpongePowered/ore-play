package ore

import scala.concurrent.{ExecutionContext, Future}

import db.{Model, ModelService, ObjectReference}
import models.user.role.RoleModel
import ore.permission.scope.ScopeSubject
import ore.user.{Member, MembershipDossier}

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable[M <: Member[_ <: RoleModel], Self <: Model] extends ScopeSubject {

  /**
    * Returns the owner of this object.
    *
    * @return Owner of object
    */
  def owner(implicit service: ModelService): M

  def ownerId: ObjectReference

  /**
    * Transfers ownership of this object to the given member.
    */
  def transferOwner(owner: M)(implicit ec: ExecutionContext, service: ModelService): Future[Self]

  /**
    * Returns this objects membership information.
    *
    * @return Memberships
    */
  def memberships(implicit service: ModelService): MembershipDossier[Self]

}
