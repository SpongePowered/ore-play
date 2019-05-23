package ore.auth

import scala.language.higherKinds

import akka.http.scaladsl.model.Uri
import cats.data.EitherT

/**
  * Interfaces with the SpongeAuth Web API
  */
trait SpongeAuthApi[F[_]] {

  /**
    * Creates a "dummy" user that cannot log in and has no password.
    *
    * @param username Username
    * @param email    Email
    * @return         Newly created user
    */
  def createDummyUser(username: String, email: String): EitherT[F, List[String], AuthUser]

  /**
    * Returns the user with the specified username.
    *
    * @param username Username to lookup
    * @return         User with username
    */
  def getUser(username: String): EitherT[F, List[String], AuthUser]

  /**
    * Returns the signed_data that can be used to construct the change-avatar
    */
  def getChangeAvatarToken(
      requester: String,
      organization: String
  ): EitherT[F, List[String], ChangeAvatarToken]

  /**
    * Returns an url for changing the avatar of an organization.
    */
  def changeAvatarUri(organization: String, token: ChangeAvatarToken): Uri
}
