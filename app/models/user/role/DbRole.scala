package models.user.role

import java.sql.Timestamp
import java.time.Instant

import db.impl.schema.DbRoleTable
import db.{Model, ObjectId, ObjectTimestamp}
import ore.permission.role.{Role, RoleCategory, Trust}

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

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = copy(id = id)
}
