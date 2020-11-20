package ore.auth

import akka.http.scaladsl.model.Uri
import zio.IO

/**
  * Interfaces with the SpongeAuth Web API
  */
trait SpongeAuthApi {

  /**
    * Creates a "dummy" user that cannot log in and has no password.
    *
    * @param username Username
    * @param email    Email
    * @return         Newly created user
    */
  def createDummyUser(username: String, email: String): IO[List[String], AuthUser]

  /**
    * Returns the user with the specified username.
    *
    * @param username Username to lookup
    * @return         User with username
    */
  def getUser(username: String): IO[List[String], AuthUser]

  /**
    * Returns an url for changing the avatar of an organization.
    */
  def changeAvatarUri(requester: String, organization: String): IO[List[String], Uri]
}
