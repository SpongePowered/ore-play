package ore.auth

import io.circe.{Decoder, HCursor}

case class ChangeAvatarToken(signedData: String, targetUsername: String, requestUserId: Int)
object ChangeAvatarToken {

  implicit val decoder: Decoder[ChangeAvatarToken] = (c: HCursor) => {
    val raw = c.downField("raw_data")
    for {
      signedData <- c.get[String]("signed_data")
      targetUsername <- raw.get[String]("target_username")
      requestUserId <- raw.get[Int]("request_user_id")
    } yield ChangeAvatarToken(signedData, targetUsername, requestUserId)
  }
}
