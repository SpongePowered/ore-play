package ore.auth

import scala.language.higherKinds

import akka.http.scaladsl.model.Uri
import cats.tagless.autoFunctorK

/**
  * Interfaces with the SpongeAuth Web API
  */
@autoFunctorK
trait SpongeAuthApi[+F[_]] {

  /**
    * Creates a "dummy" user that cannot log in and has no password.
    *
    * @param username Username
    * @param email    Email
    * @return         Newly created user
    */
  def createDummyUser(username: String, email: String): F[Either[List[String], AuthUser]]

  /**
    * Returns the user with the specified username.
    *
    * @param username Username to lookup
    * @return         User with username
    */
  def getUser(username: String): F[Either[List[String], AuthUser]]

  /**
    * Returns an url for changing the avatar of an organization.
    */
  def changeAvatarUri(requester: String, organization: String): F[Either[List[String], Uri]]
}
