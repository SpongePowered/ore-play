package util.uiowrappers

import ore.auth.{AuthUser, SSOApi}

import cats.data.OptionT
import scalaz.zio.{Task, UIO}

class UIOSSOApi(underlying: SSOApi[Task]) extends SSOApi[UIO] {
  override def isAvailable: UIO[Boolean] = underlying.isAvailable.orDie

  override def nonce(): String = underlying.nonce()

  override def getLoginUrl(returnUrl: String, nonce: String): String = underlying.getLoginUrl(returnUrl, nonce)

  override def getSignupUrl(returnUrl: String, nonce: String): String = underlying.getSignupUrl(returnUrl, nonce)

  override def getVerifyUrl(returnUrl: String, nonce: String): String = underlying.getVerifyUrl(returnUrl, nonce)

  override def generatePayload(returnUrl: String, nonce: String): String = underlying.generatePayload(returnUrl, nonce)

  override def generateSignature(payload: String): String = underlying.generateSignature(payload)

  override def authenticate(payload: String, sig: String)(
      isNonceValid: String => UIO[Boolean]
  ): OptionT[UIO, AuthUser] = OptionT(underlying.authenticate(payload, sig)(isNonceValid).value.orDie)
}
