package ore.auth

import java.util.Locale

import io.circe.{Decoder, HCursor}

/**
  * Represents a Sponge auth user.
  *
  * @param id        Unique ID
  * @param username  Username
  * @param email     Email
  * @param avatarUrl Avatar url
  * @param lang      Language
  * @param addGroups Groups
  */
case class AuthUser(
    id: Long,
    username: String,
    email: String,
    avatarUrl: Option[String],
    lang: Option[Locale],
    addGroups: Option[String]
)
object AuthUser {

  implicit val decoder: Decoder[AuthUser] = (c: HCursor) => {
    for {
      id         <- c.get[Long]("id")
      username   <- c.get[String]("username")
      email      <- c.get[String]("email")
      avatarUrl  <- c.get[Option[String]]("avatar_url")
      language   <- c.get[Option[String]]("language").map(_.map(Locale.forLanguageTag))
      add_groups <- c.get[Option[String]]("add_groups")
    } yield AuthUser(id, username, email, avatarUrl, language, add_groups)
  }
}
