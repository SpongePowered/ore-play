package ore.auth

import scala.language.higherKinds

import cats.data.OptionT

/**
  * Manages authentication to Sponge services.
  */
trait SSOApi[F[_]] {

  /**
    * Returns a future result of whether SSO is available.
    *
    * @return True if available
    */
  def isAvailable: F[Boolean]

  /**
    * Generates a new nonce
    */
  def nonce(): String

  /**
    * Returns the login URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getLoginUrl(returnUrl: String, nonce: String): String

  /**
    * Returns the signup URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getSignupUrl(returnUrl: String, nonce: String): String

  /**
    * Returns the verify URL with a generated SSO payload to the SSO instance.
    *
    * @param returnUrl  URL to return to after authentication
    * @return           URL to SSO
    */
  def getVerifyUrl(returnUrl: String, nonce: String): String

  /**
    * Generates a new Base64 encoded SSO payload.
    *
    * @param returnUrl  URL to return to once authenticated
    * @return           New payload
    */
  def generatePayload(returnUrl: String, nonce: String): String

  /**
    * Generates a signature for the specified Base64 encoded payload.
    *
    * @param payload  Payload to sign
    * @return         Signature of payload
    */
  def generateSignature(payload: String): String

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
  def authenticate(payload: String, sig: String)(isNonceValid: String => F[Boolean]): OptionT[F, AuthUser]
}
