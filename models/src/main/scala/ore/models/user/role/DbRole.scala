package ore.models.user.role

import java.time.OffsetDateTime

import ore.db.impl.ModelCompanionPartial
import ore.db.impl.schema.DbRoleTable
import ore.db.{Model, ModelQuery, ObjId, ObjOffsetDateTime}
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
      time: ObjOffsetDateTime
  ): Model[DbRole] = Model(id, ObjOffsetDateTime(OffsetDateTime.MIN), model)

  implicit val query: ModelQuery[DbRole] = ModelQuery.from(this)
}
