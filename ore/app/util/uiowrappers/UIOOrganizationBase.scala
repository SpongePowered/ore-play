package util.uiowrappers

import javax.inject.Inject

import db.impl.access.OrganizationBase
import ore.db.{DbRef, Model}
import ore.models.organization.Organization
import ore.models.user.User
import ore.models.user.role.OrganizationUserRole
import ore.util.OreMDC

import cats.data.{EitherT, OptionT}
import scalaz.zio.{Task, UIO}

class UIOOrganizationBase @Inject()(underlying: OrganizationBase[Task]) extends OrganizationBase[UIO] {
  override def create(name: String, ownerId: DbRef[User], members: Set[OrganizationUserRole])(
      implicit mdc: OreMDC
  ): EitherT[UIO, List[String], Model[Organization]] = EitherT(underlying.create(name, ownerId, members).value.orDie)

  override def withName(name: String): OptionT[UIO, Model[Organization]] =
    OptionT(underlying.withName(name).value.orDie)
}
