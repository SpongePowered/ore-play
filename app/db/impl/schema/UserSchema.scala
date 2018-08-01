package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.UserTable
import db.{ModelSchema, ModelService}
import models.user.User
import scala.concurrent.{ExecutionContext, Future}

import util.functional.OptionT

/**
  * User related queries.
  *
  * TODO: Find solution to eliminate the need for this class
  */
class UserSchema(override val service: ModelService)
  extends ModelSchema[User](service, classOf[User], TableQuery[UserTable]) {

  override def like(user: User)(implicit ec: ExecutionContext): OptionT[Future, User]
  = this.service.find[User](this.modelClass, _.name.toLowerCase === user.name.toLowerCase)

}
