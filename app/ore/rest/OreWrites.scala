package ore.rest

import play.api.libs.json.Json.obj
import play.api.libs.json._

import models.api.ProjectApiKey
import models.project._
import security.pgp.PGPPublicKeyInfo

/**
  * Contains implicit JSON [[Writes]] for the Ore API.
  */
trait OreWrites {

  implicit val projectApiKeyWrites: Writes[ProjectApiKey] = (key: ProjectApiKey) =>
    obj(
      "id"        -> key.id.value,
      "createdAt" -> key.createdAt.value,
      "keyType"   -> obj("id" -> key.keyType.value, "name" -> key.keyType.name),
      "projectId" -> key.projectId,
      "value"     -> key.value
  )

  implicit val pageWrites: Writes[Page] = (page: Page) =>
    obj(
      "id"        -> page.id.value,
      "createdAt" -> page.createdAt.value.toString,
      "parentId"  -> page.parentId,
      "name"      -> page.name,
      "slug"      -> page.slug
  )

  implicit val channelWrites: Writes[Channel] = (channel: Channel) =>
    obj("name" -> channel.name, "color" -> channel.color.hex, "nonReviewed" -> channel.isNonReviewed)

  implicit val tagWrites: Writes[Tag] = (tag: Tag) => {
    obj(
      "id"              -> tag.id.value,
      "name"            -> tag.name,
      "data"            -> tag.data,
      "backgroundColor" -> tag.color.background,
      "foregroundColor" -> tag.color.foreground
    )
  }

  implicit val tagColorWrites: Writes[TagColor] = (tagColor: TagColor) => {
    obj(
      "id"              -> tagColor.value,
      "backgroundColor" -> tagColor.background,
      "foregroundColor" -> tagColor.foreground
    )
  }

  implicit val pgpPublicKeyInfoWrites: Writes[PGPPublicKeyInfo] = (key: PGPPublicKeyInfo) => {
    obj(
      "raw"       -> key.raw,
      "userName"  -> key.userName,
      "email"     -> key.email,
      "id"        -> key.id,
      "createdAt" -> key.createdAt.toString
    )
  }

}
object OreWrites extends OreWrites