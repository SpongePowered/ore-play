package ore.member

import scala.language.higherKinds

import ore.db.impl.table.common.RoleTable
import ore.db.{DbRef, Model}
import ore.models.user.User
import ore.models.user.role.UserRoleModel

/**
  * Represents something with a [[MembershipDossier]].
  */
trait Joinable[F[_], M] {
  type RoleType <: UserRoleModel[RoleType]
  type RoleTypeTable <: RoleTable[RoleType]


  def ownerId(m: M): DbRef[User]

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
