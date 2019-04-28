package ore.member

import scala.language.{higherKinds, implicitConversions}

import ore.db.impl.table.common.RoleTable
import ore.db.{DbRef, Model}
import ore.models.user.role.UserRoleModel
import ore.models.user.{User, UserOwned}

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable[F[_], M] {
  type RoleType <: UserRoleModel[RoleType]
  type RoleTypeTable <: RoleTable[RoleType]
  def userOwned: UserOwned[M]

  /**
    * Transfers ownership of this object to the given member.
    */
  def transferOwner(m: Model[M])(owner: DbRef[User]): F[Model[M]]

  /**
    * Returns this objects membership information.
    *
    * @return Memberships
    */
  def memberships: MembershipDossier.Aux[F, M, RoleType, RoleTypeTable]
}
object Joinable {
  type Aux[F[_], M, RoleType0 <: UserRoleModel[RoleType0], RoleTypeTable0 <: RoleTable[RoleType0]] = Joinable[F, M] {
    type RoleType      = RoleType0
    type RoleTypeTable = RoleTypeTable0
  }

  class ModelOps[M](private val m: Model[M]) extends AnyVal {

    def transferOwner[F[_]](owner: DbRef[User])(implicit joinable: Joinable[F, M]): F[Model[M]] =
      joinable.transferOwner(m)(owner)

    def memberships[F[_], RoleType0 <: UserRoleModel[RoleType0], RoleTypeTable0 <: RoleTable[RoleType0]](
        implicit joinable: Joinable.Aux[F, M, RoleType0, RoleTypeTable0]
    ): MembershipDossier.Aux[F, M, RoleType0, RoleTypeTable0] = joinable.memberships
  }

  trait ToJoinableOps {
    implicit def joinableToModelOps[M](m: Model[M]): ModelOps[M] = new ModelOps(m)
  }

}
