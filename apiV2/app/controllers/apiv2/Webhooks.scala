package controllers.apiv2

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, ApiError, ApiErrors}
import controllers.sugar.Requests.ApiRequest
import controllers.sugar.ResolvedAPIScope
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import ore.db.Model
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.WebhookTable
import ore.models.Job
import ore.models.project.Webhook
import ore.models.project.Webhook.WebhookEventType
import ore.permission.Permission
import ore.util.CryptoUtils
import util.{PartialUtils, PatchDecoder}
import util.syntax._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import cats.data.NonEmptyList
import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import io.circe.derivation.annotations.SnakeCaseJsonCodec
import squeal.category.macros.Derive
import squeal.category.syntax.all._
import squeal.category._
import zio.{IO, UIO, ZIO}

class Webhooks(
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents,
    actorSystem: ActorSystem
) extends AbstractApiV2Controller(lifecycle) {
  import Webhooks._

  private val discordWebhookUrl =
    """https://(?:(?:discordapp)|(?:discord))\.com/api/(?:(?:v6/)|(?:v8/))?webhooks/(\d+)/([^/]+)""".r

  private def validateCallbackUrl(callbackUrl: String, discordFormatted: Boolean) =
    ZIO(Uri.parseAbsolute(callbackUrl))
      .orElseFail(BadRequest(ApiError("Invalid callback URL")))
      .filterOrFail(_.scheme == "https")(BadRequest(ApiError("Only HTTPS urls allowed")))
      .filterOrFail { uri =>
        val isDiscordHost = uri.authority.host.address == "discord" || uri.authority.host.address == "discordapp"
        if (isDiscordHost) discordFormatted && discordWebhookUrl.matches(callbackUrl)
        else true
      }(
        BadRequest(ApiError("Invalid url for discord formatted webhook"))
      )
      .map(_.toString)

  private def makeWebhookSecret(): String = {
    val secretBytes = new Array[Byte](32)
    new SecureRandom().nextBytes(secretBytes)
    CryptoUtils.bytesToHex(secretBytes)
  }

  private def apiWebhookFromWebhook(webhook: Webhook): APIV2.Webhook =
    APIV2.Webhook(
      webhook.publicId,
      webhook.name,
      webhook.callbackUrl,
      webhook.discordFormatted,
      webhook.events,
      webhook.lastError,
      webhook.secret
    )

  private def pingWebhook(
      webhook: Model[Webhook]
  )(implicit request: ApiRequest[ResolvedAPIScope.ProjectScope, _]): UIO[Model[Webhook]] = {
    webhook.callbackUrl match {
      case discordWebhookUrl(_, _) => service.update(webhook)(_.copy(lastError = None))
      case _ =>
        val webhookType = Webhook.WebhookEventType.Ping

        val body =
          Json
            .obj(
              "webhook_meta_info" := APIV2.WebhookPostData(
                request.scope.projectOwner,
                request.scope.projectSlug,
                webhookType
              )
            )
            .noSpaces
        val unixTime = System.currentTimeMillis() / 1000
        val signature = CryptoUtils.hmac_sha256(
          webhook.secret,
          ByteBuffer.allocate(8).putLong(unixTime).array ++ body.getBytes("UTF-8")
        )

        for {
          eitherResponse <- ZIO
            .fromFuture(_ =>
              Http().singleRequest(
                HttpRequest(
                  method = HttpMethods.GET,
                  uri = webhook.callbackUrl,
                  headers = Seq(
                    RawHeader("Ore-Webhook-EventType", webhookType.value),
                    RawHeader("Ore-Webhook-Timestamp", unixTime.toString),
                    RawHeader("Ore-Webhook-HMACSignature", signature)
                  ),
                  entity = HttpEntity(
                    ContentTypes.`application/json`,
                    body
                  )
                )
              )
            )
            .either
          newError = eitherResponse match {
            case Right(response) if response.status.isSuccess() =>
              response.discardEntityBytes()
              None
            case Right(response) =>
              response.discardEntityBytes()
              Some(s"Encountered response code: ${response.status.intValue}")
            case Left(e) => Some(s"Failed to run ping request: ${e.getMessage}")
          }
          res <- service.update(webhook)(_.copy(lastError = newError))
        } yield webhook
    }
  }

  def createWebhook(projectOwner: String, projectSlug: String): Action[CreateWebhookRequest] =
    ApiAction(Permission.EditWebhooks, APIScope.ProjectScope(projectOwner, projectSlug))
      .asyncF(parseCirce.decodeJson[CreateWebhookRequest]) { implicit request =>
        val data = request.body

        val publicId         = UUID.randomUUID()
        val discordFormatted = data.discordFormatted.getOrElse(false)

        validateCallbackUrl(data.callbackUrl, discordFormatted).flatMap { uri =>
          val secret = makeWebhookSecret()

          service
            .insert(
              Webhook(
                request.scope.id,
                publicId,
                data.name,
                uri,
                discordFormatted,
                data.events.toList,
                secret,
                None
              )
            )
            .flatMap(pingWebhook)
            .map { webhook =>
              Created(apiWebhookFromWebhook(webhook)).withHeaders(
                LOCATION -> routes.Webhooks.getWebhook(projectOwner, projectSlug, publicId.toString).absoluteURL()
              )
            }
        }
      }

  def getWebhook(projectOwner: String, projectSlug: String, webhookId: String): Action[AnyContent] =
    ApiAction(Permission.EditWebhooks, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF {
      for {
        uuidWebhookId <- IO(UUID.fromString(webhookId)).orElseFail(BadRequest)
        webhook       <- ModelView.now(Webhook).find(_.publicId === uuidWebhookId).toZIO.orElseFail(NotFound)
      } yield Ok(apiWebhookFromWebhook(webhook))
    }

  def pingWebhookAction(projectOwner: String, projectSlug: String, webhookId: String): Action[AnyContent] =
    ApiAction(Permission.EditWebhooks, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF { implicit request =>
      for {
        uuidWebhookId <- IO(UUID.fromString(webhookId)).orElseFail(BadRequest)
        webhook       <- ModelView.now(Webhook).find(_.publicId === uuidWebhookId).toZIO.orElseFail(NotFound)
        pingedWebhook <- pingWebhook(webhook)
      } yield Ok(apiWebhookFromWebhook(pingedWebhook))
    }

  def refreshSecret(projectOwner: String, projectSlug: String, webhookId: String): Action[AnyContent] =
    ApiAction(Permission.EditWebhooks, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF {
      for {
        uuidWebhookId <- IO(UUID.fromString(webhookId)).orElseFail(BadRequest)
        newSecret = makeWebhookSecret()
        webhook        <- ModelView.now(Webhook).find(_.publicId === uuidWebhookId).toZIO.orElseFail(NotFound)
        updatedWebhook <- service.update(webhook)(_.copy(secret = newSecret))
        _ <- service.deleteWhere(Job) { j =>
          j.jobType === (Job.PostWebhookResponse: Job.JobType) && j.jobProperties
            .>>[Long]("foo") === updatedWebhook.id.value
        }
      } yield Ok(apiWebhookFromWebhook(updatedWebhook))
    }

  def editWebhook(projectOwner: String, projectSlug: String, webhookId: String): Action[Json] =
    ApiAction(Permission.EditWebhooks, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF(parseCirce.json) {
      implicit request =>
        for {
          uuidWebhookId <- IO(UUID.fromString(webhookId)).orElseFail(BadRequest)
          webhookEdits <- IO
            .fromEither(
              EditableWebhookF.patchDecoder
                .traverseKC(PartialUtils.decodeAll(request.body.hcursor))
                .toEither: Either[NonEmptyList[Error], EditableWebhook]
            )
            .mapError(e => BadRequest(ApiErrors(e.map(_.show))))
          validatedEdits <- ZIO
            .foreach(webhookEdits.callbackUrl) { callbackUrl =>
              val discordFormattedF = webhookEdits.discordFormatted
                .fold(
                  service.runDBIO(
                    TableQuery[WebhookTable]
                      .filter(_.publicId === uuidWebhookId)
                      .map(_.discordFormatted)
                      .result
                      .head
                  )
                )(b => UIO.succeed(b))

              discordFormattedF.flatMap(validateCallbackUrl(callbackUrl, _))
            }
            .map(callbackUrl => webhookEdits.copy[Option](callbackUrl = callbackUrl))
          _              <- service.runDbCon(APIV2Queries.updateWebhook(uuidWebhookId, validatedEdits).run)
          updatedWebhook <- ModelView.now(Webhook).find(_.publicId === uuidWebhookId).toZIO.orElseFail(NotFound)
          pingedWebhook  <- pingWebhook(updatedWebhook)
        } yield Ok(apiWebhookFromWebhook(pingedWebhook))
    }

  def deleteWebhook(projectOwner: String, projectSlug: String, webhookId: String): Action[AnyContent] =
    ApiAction(Permission.EditWebhooks, APIScope.ProjectScope(projectOwner, projectSlug)).asyncF {
      for {
        uuidWebhookId <- IO(UUID.fromString(webhookId)).orElseFail(BadRequest)
        _             <- service.deleteWhere(Webhook)(_.publicId === uuidWebhookId)
      } yield NoContent
    }
}
object Webhooks {

  import APIV2.webhookEventTypeCodec

  @SnakeCaseJsonCodec case class CreateWebhookRequest(
      name: String,
      callbackUrl: String,
      discordFormatted: Option[Boolean],
      events: Seq[WebhookEventType]
  )

  type EditableWebhook = EditableWebhookF[Option]
  case class EditableWebhookF[F[_]](
      name: F[String],
      callbackUrl: F[String],
      discordFormatted: F[Boolean],
      events: F[List[WebhookEventType]]
  )
  object EditableWebhookF {
    implicit val F
        : ApplicativeKC[EditableWebhookF] with TraverseKC[EditableWebhookF] with DistributiveKC[EditableWebhookF] =
      Derive.allKC[EditableWebhookF]

    val patchDecoder: EditableWebhookF[PatchDecoder] =
      PatchDecoder.fromName(Derive.namesWithProductImplicitsC[EditableWebhookF, Decoder])(
        io.circe.derivation.renaming.snakeCase
      )
  }
}
