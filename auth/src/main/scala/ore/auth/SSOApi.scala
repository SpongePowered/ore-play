package ore.auth

import zio.{IO, UIO}

/**
  * Manages authentication to Sponge services.
  */
trait SSOApi {

  /**
    * Returns a future result of whether SSO is available.
    *
    * @return True if available
    */
  def isAvailable: UIO[Boolean]

  /**
    * Returns the login URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getLoginUrl(returnUrl: String): URLWithNonce

  /**
    * Returns the signup URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getSignupUrl(returnUrl: String): URLWithNonce

  /**
    * Returns the verify URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getVerifyUrl(returnUrl: String): URLWithNonce

  /**
    * Validates an incoming payload and extracts user information. The
    * incoming payload indicates that the User was authenticated successfully
    * off-site.
    *
    * @param payload        Incoming SSO payload
    * @param sig            Incoming SSO signature
    * @param isNonceValid   Callback to check if an incoming nonce is valid and
    *                       marks the nonce as invalid so it cannot be used again
    * @return               [[AuthUser]] if successful
    */
  def authenticate(payload: String, sig: String)(isNonceValid: String => UIO[Boolean]): IO[Option[Nothing], AuthUser]
}
