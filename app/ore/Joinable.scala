package ore

import db.{DbRef, Model, ModelService}
import models.user.role.UserRoleModel
import ore.user.{Member, MembershipDossier}

import cats.effect.{ContextShift, IO}

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable[M <: Member[_ <: UserRoleModel], Self <: Model] {

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
  def transferOwner(owner: M)(implicit service: ModelService, cs: ContextShift[IO]): IO[Self]

  /**
    * Returns this objects membership information.
    *
    * @return Memberships
    */
  def memberships(implicit service: ModelService): MembershipDossier[IO, Self]

}
