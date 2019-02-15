package ore

import db.{Model, DbRef, ModelService}
import models.user.User
import models.user.role.UserRoleModel
import ore.user.{Member, MembershipDossier}

import cats.effect.{ContextShift, IO}

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable {

  def ownerId: DbRef[User]

}

trait JoinableOps[M, Mem <: Member[_ <: UserRoleModel[_]]] extends Any {

  /**
    * Returns the owner of this object.
    *
    * @return Owner of object
    */
  def owner(implicit service: ModelService): Mem

  /**
    * Transfers ownership of this object to the given member.
    */
  def transferOwner(owner: Mem)(implicit service: ModelService, cs: ContextShift[IO]): IO[Model[M]]

  /**
    * Returns this objects membership information.
    *
    * @return Memberships
    */
  def memberships(implicit service: ModelService): MembershipDossier[IO, M]
}
