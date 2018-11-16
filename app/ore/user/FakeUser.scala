package ore.user

import scala.language.implicitConversions

import java.sql.Timestamp
import java.util.Date
import javax.inject.Inject

import db.ObjectId
import models.user.User
import ore.OreConfig

/**
  * Represents a "fake" User object for bypassing the standard authentication
  * method in a development environment.
  */
final class FakeUser @Inject()(config: OreConfig) {

  private lazy val conf = config.app.fakeUser

  /**
    * True if FakeUser should be used.
    */
  lazy val isEnabled: Boolean = conf.enabled

  private lazy val user =
    if (isEnabled)
      User(
        id = ObjectId(conf.id),
        fullName = conf.name,
        name = conf.username,
        email = conf.email,
        joinDate = Some(new Timestamp(new Date().getTime)),
      )
    else null

}

object FakeUser { implicit def unwrap(fake: FakeUser): User = fake.user }
