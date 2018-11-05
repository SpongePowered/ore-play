package models.user.role

import java.sql.Timestamp
import java.time.Instant

import db.impl.schema.DbRoleTable
import db.{Model, ModelQuery, ObjectId, ObjectTimestamp}
import ore.permission.role.{Role, RoleCategory, Trust}

import slick.lifted.TableQuery

case class DbRole(
    id: ObjectId,
    name: String,
    category: RoleCategory,
    trust: Trust,
    title: String,
    color: String,
    isAssignable: Boolean,
    rank: Option[Int]
) extends Model {

  override val createdAt: ObjectTimestamp = ObjectTimestamp(Timestamp.from(Instant.EPOCH))

  override type M = DbRole
  override type T = DbRoleTable

  def toRole: Role = Role.withValue(name)
}
object DbRole {
  implicit val query: ModelQuery[DbRole] =
    ModelQuery.from[DbRole](TableQuery[DbRoleTable], (obj, id, _) => obj.copy(id = id))
}
