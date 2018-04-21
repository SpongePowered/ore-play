package models.project

import db.impl.OrePostgresDriver
import db.table.MappedType
import ore.permission.{Permission, ReviewProjects}
import slick.jdbc.JdbcType

object ProjectStates extends Enumeration {
  val Public          = ProjectState(1, "public"        , ReviewProjects, false,    "")
  val New             = ProjectState(2, "new"           , ReviewProjects, false,    "project-new")
  val NeedsChanges    = ProjectState(3, "needsChanges"  , ReviewProjects, true,     "striped project-needsChanges")
  val NeedsApproval   = ProjectState(4, "needsApproval" , ReviewProjects, false,    "striped project-needsChanges")
  val SoftDelete      = ProjectState(5, "softDelete"    , ReviewProjects, true,     "striped project-hidden")

  def withId(id: Int): ProjectState = {
    this.apply(id).asInstanceOf[ProjectState]
  }

  case class ProjectState(override val id: Int, nameKey: String, permission: Permission, showModal: Boolean, cssClass: String) extends super.Val(id) with MappedType[ProjectState] {
    implicit val mapper: JdbcType[ProjectState] = OrePostgresDriver.api.projectStateMapper
  }

  implicit def convert(value: Value): ProjectState = value.asInstanceOf[ProjectState]
}
