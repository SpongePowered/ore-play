package models.user.role

import java.sql.Timestamp
import java.time.Instant

import db.impl.schema.DbRoleTable
import db.{Model, ModelQuery, ObjId, ObjTimestamp}
import ore.permission.role.{Role, RoleCategory, Trust}

import slick.lifted.TableQuery

case class DbRole private (
    id: ObjId[DbRole],
    name: String,
    category: RoleCategory,
    trust: Trust,
    title: String,
    color: String,
    isAssignable: Boolean,
    rank: Option[Int]
) extends Model {

  override val createdAt: ObjTimestamp = ObjTimestamp(Timestamp.from(Instant.EPOCH))

  override type M = DbRole
  override type T = DbRoleTable

  def toRole: Role = Role.withValue(name)
}
object DbRole {
  implicit val query: ModelQuery[DbRole] =
    ModelQuery.from[DbRole](TableQuery[DbRoleTable], (obj, id, _) => obj.copy(id = id))
}
