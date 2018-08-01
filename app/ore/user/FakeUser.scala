package ore.user

import java.sql.Timestamp
import java.util.Date

import javax.inject.Inject

import db.ObjectId
import scala.language.implicitConversions

import models.user.User
import ore.OreConfig
import ore.permission.role.RoleType

/**
  * Represents a "fake" User object for bypassing the standard authentication
  * method in a development environment.
  */
final class FakeUser @Inject()(config: OreConfig) {

  private lazy val conf = config.app

  /**
    * True if FakeUser should be used.
    */
  lazy val isEnabled: Boolean = conf.get[Boolean]("fakeUser.enabled")

  lazy private val user = if (isEnabled) User(
    id = ObjectId(conf.get[Int]("fakeUser.id")),
    fullName = conf.getOptional[String]("fakeUser.name"),
    name = conf.get[String]("fakeUser.username"),
    email = conf.getOptional[String]("fakeUser.email"),
    joinDate = Some(new Timestamp(new Date().getTime)),
    globalRoles = List(RoleType.OreAdmin)
  ) else null

}

object FakeUser { implicit def unwrap(fake: FakeUser): User = fake.user }
