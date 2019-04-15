package models.api

import db.impl.DefaultModelCompanion
import db.impl.query.UserQueries
import db.impl.schema.ApiKeyTable
import models.user.User
import ore.db.{DbRef, ModelQuery, ModelService}
import ore.permission.scope.{GlobalScope, HasScope, OrganizationScope, ProjectScope}
import ore.permission.{NamedPermission, Permission}
import ore.user.UserOwned
import util.syntax._

import cats.effect.IO
import slick.lifted.TableQuery

case class ApiKey(
    name: String,
    ownerId: DbRef[User],
    tokenIdentifier: String,
    private val rawKeyPermissions: Permission
) {

  def permissionsIn[A: HasScope](a: A)(implicit service: ModelService): IO[Permission] = {
    val query = a.scope match {
      case GlobalScope              => UserQueries.globalPermission(ownerId)
      case ProjectScope(projectId)  => UserQueries.projectPermission(ownerId, projectId)
      case OrganizationScope(orgId) => UserQueries.organizationPermission(ownerId, orgId)
    }

    service.runDbCon(query.unique).map(userPerms => Permission.fromLong(userPerms & rawKeyPermissions))
  }

  def isSubKey(perms: Permission): Boolean = rawKeyPermissions.has(perms)

  def namedRawPermissions: Seq[NamedPermission] = rawKeyPermissions.toNamedSeq
}
object ApiKey extends DefaultModelCompanion[ApiKey, ApiKeyTable](TableQuery[ApiKeyTable]) {
  implicit val query: ModelQuery[ApiKey] = ModelQuery.from(this)

  implicit val isUserOwned: UserOwned[ApiKey] = (a: ApiKey) => a.ownerId
}
