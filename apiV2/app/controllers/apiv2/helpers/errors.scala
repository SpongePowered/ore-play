package controllers.apiv2.helpers

import cats.data.NonEmptyList
import cats.kernel.Semigroup
import io.circe.generic.extras.ConfiguredJsonCodec
import models.protocols.APIV2.circeConfig

@ConfiguredJsonCodec case class ApiError(error: String)
@ConfiguredJsonCodec case class ApiErrors(errors: NonEmptyList[String])
object ApiErrors {
  implicit val semigroup: Semigroup[ApiErrors] = (x: ApiErrors, y: ApiErrors) => ApiErrors(x.errors.concatNel(y.errors))
}

@ConfiguredJsonCodec case class UserError(userError: String)
@ConfiguredJsonCodec case class UserErrors(userErrors: NonEmptyList[String])
