package ore.db.impl.schema

import java.util.UUID

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Project, Webhook}

class WebhookTable(tag: Tag) extends ModelTable[Webhook](tag, "project_webhooks") {

  def projectId        = column[DbRef[Project]]("project_id")
  def publicId         = column[UUID]("public_id")
  def name             = column[String]("name")
  def callbackUrl      = column[String]("callback_url")
  def discordFormatted = column[Boolean]("discord_formatted")
  def eventTypes       = column[List[Webhook.WebhookEventType]]("event_types")
  def secret           = column[String]("secret")
  def lastError        = column[String]("last_error")

  override def * =
    (id.?, createdAt.?, (projectId, publicId, name, callbackUrl, discordFormatted, eventTypes, secret, lastError.?)).<>(
      mkApply((Webhook.apply _).tupled),
      mkUnapply(Webhook.unapply)
    )
}
