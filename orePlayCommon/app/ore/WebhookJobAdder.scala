package ore

import _root_.db.impl.query.SharedQueries
import ore.db.{DbRef, ModelService}
import ore.models.project.{Project, Webhook}

import ackcord.data.OutgoingEmbed
import ackcord.requests.ExecuteWebhookData
import io.circe.Encoder
import io.circe.syntax._
import zio.UIO

object WebhookJobAdder {

  def add[A: Encoder](
      projectId: DbRef[Project],
      projectOwner: String,
      projectSlug: String,
      webhookEvent: Webhook.WebhookEventType,
      data: A,
      discordData: OutgoingEmbed
  )(
      implicit service: ModelService[UIO]
  ): UIO[Unit] =
    service
      .runDbCon(
        SharedQueries
          .addWebhookJobs(
            projectId,
            projectOwner,
            projectSlug,
            webhookEvent,
            data.asJson.noSpaces,
            ExecuteWebhookData(embeds = Seq(discordData)).asJson.noSpaces
          )
          .run
      )
      .unit

}
