package models.user.role

import java.sql.Timestamp
import java.time.Instant

import db.impl.schema.DbRoleTable
import db.{Model, ModelCompanionPartial, ModelQuery, ObjId, ObjTimestamp}
import ore.permission.Permission
import ore.permission.role.{Role, RoleCategory}

import slick.lifted.TableQuery

case class DbRole(
    name: String,
    category: RoleCategory,
    permissions: Permission,
    title: String,
    color: String,
    isAssignable: Boolean,
    rank: Option[Int]
) {

  def toRole: Role = Role.withValue(name)
}
object DbRole extends ModelCompanionPartial[DbRole, DbRoleTable](TableQuery[DbRoleTable]) {

  override def asDbModel(
      model: DbRole,
      id: ObjId[DbRole],
      time: ObjTimestamp
  ): Model[DbRole] = Model(id, ObjTimestamp(Timestamp.from(Instant.EPOCH)), model)

  implicit val query: ModelQuery[DbRole] = ModelQuery.from(this)
}
