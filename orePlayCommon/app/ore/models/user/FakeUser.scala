package ore.models.user

import scala.language.implicitConversions

import java.time.Instant
import javax.inject.Inject

import ore.OreConfig
import ore.db.{DbRef, ObjId}

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

  lazy val username: String = conf.username
  lazy val id: DbRef[User]  = conf.id

  private lazy val user =
    if (isEnabled)
      User(
        id = ObjId(id),
        fullName = conf.name,
        name = username,
        email = conf.email,
        joinDate = Some(Instant.now())
      )
    else sys.error("Tried to use disabled fake user")

}

object FakeUser { implicit def unwrap(fake: FakeUser): User = fake.user }
