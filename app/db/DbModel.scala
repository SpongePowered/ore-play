package db

import scala.language.implicitConversions

import ore.organization.OrganizationOwned
import ore.permission.scope.HasScope
import ore.project.ProjectOwned
import ore.user.UserOwned

case class DbModel[+A](
    id: ObjId[A],
    createdAt: ObjTimestamp,
    obj: A
)
object DbModel {
  implicit def unwrap[A](dbModel: DbModel[A]): A = dbModel.obj

  implicit def isProjectOwned[A](implicit isOwned: ProjectOwned[A]): ProjectOwned[DbModel[A]] =
    (a: DbModel[A]) => isOwned.projectId(a.obj)
  implicit def isUserOwned[A](implicit isOwned: UserOwned[A]): UserOwned[DbModel[A]] =
    (a: DbModel[A]) => isOwned.userId(a.obj)
  implicit def isOrgOwned[A](implicit isOwned: OrganizationOwned[A]): OrganizationOwned[DbModel[A]] =
    (a: DbModel[A]) => isOwned.organizationId(a.obj)

  implicit def hasUnderlyingScope[A](implicit hasScope: HasScope[A]): HasScope[DbModel[A]] =
    (a: DbModel[A]) => hasScope.scope(a.obj)
}
