package ore

import scala.concurrent.{ExecutionContext, Future}

import db.{Model, ModelService, DbRef}
import models.user.role.RoleModel
import ore.user.{Member, MembershipDossier}

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable[M <: Member[_ <: RoleModel], Self <: Model] {

  /**
    * Returns the owner of this object.
    *
    * @return Owner of object
    */
  def owner(implicit service: ModelService): M

  def ownerId: DbRef[M]

  /**
    * Transfers ownership of this object to the given member.
    */
  def transferOwner(owner: M)(implicit ec: ExecutionContext, service: ModelService): Future[Self]

  /**
    * Returns this objects membership information.
    *
    * @return Memberships
    */
  def memberships(implicit ec: ExecutionContext, service: ModelService): MembershipDossier[Future, Self]

}
