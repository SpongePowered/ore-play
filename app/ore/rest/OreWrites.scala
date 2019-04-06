package ore.rest

import play.api.libs.json.Json.obj
import play.api.libs.json._

import db.Model
import models.api.ProjectApiKey
import models.project._

/**
  * Contains implicit JSON [[Writes]] for the Ore API.
  */
trait OreWrites {

  implicit val projectApiKeyWrites: Writes[Model[ProjectApiKey]] = (key: Model[ProjectApiKey]) =>
    obj(
      "id"        -> key.id.value,
      "createdAt" -> key.createdAt.value,
      "keyType"   -> obj("id" -> key.keyType.value, "name" -> key.keyType.name),
      "projectId" -> key.projectId,
      "value"     -> key.value
  )

  implicit val pageWrites: Writes[Model[Page]] = (page: Model[Page]) =>
    obj(
      "id"        -> page.id.value,
      "createdAt" -> page.createdAt.toString,
      "parentId"  -> page.parentId,
      "name"      -> page.name,
      "slug"      -> page.slug
  )

  implicit val channelWrites: Writes[Channel] = (channel: Channel) =>
    obj("name" -> channel.name, "color" -> channel.color.hex, "nonReviewed" -> channel.isNonReviewed)

  implicit val tagWrites: Writes[Model[VersionTag]] = (tag: Model[VersionTag]) => {
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
}
object OreWrites extends OreWrites