package ore.rest

import db.impl.OrePostgresDriver
import db.table.MappedType

import scala.language.implicitConversions

object ProjectApiKeyTypes extends Enumeration {

  val Deployment = ProjectApiKeyType(0, "deployment")

  case class ProjectApiKeyType(i: Int, name: String) extends super.Val(i, name) with MappedType[ProjectApiKeyType] {
    implicit val mapper = OrePostgresDriver.api.projectApiKeyTypeTypeMapper
  }
  implicit def convert(value: Value): ProjectApiKeyType = value.asInstanceOf[ProjectApiKeyType]

}
