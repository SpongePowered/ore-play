package security.spauth

import play.api.i18n.Lang
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import ore.models.user.User
import ore.db.{DbRef, ObjId}
import ore.permission.role.Role

/**
  * Represents a Sponge user.
  *
  * @param id        Unique ID
  * @param username  Username
  * @param email     Email
  * @param avatarUrl Avatar url
  * @param lang      Language
  * @param addGroups Groups
  */
case class SpongeUser(
    id: DbRef[User],
    username: String,
    email: String,
    avatarUrl: Option[String],
    lang: Option[Lang],
    addGroups: Option[String]
) {

  def newGlobalRoles: Option[List[Role]] = addGroups.map { groups =>
    if (groups.trim.isEmpty) Nil
    else groups.split(",").flatMap(Role.withValueOpt).toList
  }

  def toUser: User = User(
    id = ObjId(id),
    fullName = None,
    name = username,
    email = Some(email),
    lang = lang.map(_.locale),
    tagline = None,
    joinDate = None,
    readPrompts = Nil
  )
}
object SpongeUser {
  implicit val spongeUserReads: Reads[SpongeUser] =
    (JsPath \ "id")
      .read[DbRef[User]]
      .and((JsPath \ "username").read[String])
      .and((JsPath \ "email").read[String])
      .and((JsPath \ "avatar_url").readNullable[String])
      .and((JsPath \ "language").readNullable[String].map(_.flatMap(Lang.get)))
      .and((JsPath \ "add_groups").readNullable[String])(SpongeUser.apply _)
}