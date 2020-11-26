package ore

import java.nio.ByteBuffer
import java.time.OffsetDateTime

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectTable, VersionTable, WebhookTable}
import ore.db.{DbRef, Model, ObjId}
import ore.discourse.DiscourseError
import ore.models.project.{Project, Version, Webhook}
import ore.models.{Job, JobInfo}
import ore.util.CryptoUtils

import ackcord.data.{MessageFlags, OutgoingEmbed, SnowflakeType}
import ackcord.requests.{
  AllowedMention,
  ExecuteWebhook,
  ExecuteWebhookData,
  HttpException,
  Ratelimiter,
  RequestDropped,
  RequestError,
  RequestRatelimited,
  RequestResponse
}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.pattern.CircuitBreakerOpenException
import akka.util.Timeout
import com.typesafe.scalalogging
import cats.syntax.all._
import enumeratum.values.{ValueEnum, ValueEnumEntry}
import io.circe._
import io.circe.syntax._
import shapeless.Typeable
import slick.lifted.TableQuery
import zio._
import zio.clock.Clock

object JobsProcessor {
  private val Logger = scalalogging.Logger("JobsProcessor")

  def fiber: URIO[Db with Clock with Discourse with Discord with Actors with Config, Unit] =
    tryGetJob.flatMap {
      case Some(job) =>
        (logJob(job) *> decodeJob(job).mapError(Right.apply) >>= processJob >>= finishJob).catchAll(logErrors) *> fiber
      case None =>
        ZIO.descriptorWith(desc => UIO(Logger.debug(s"No more jobs found. Finishing fiber ${desc.id}"))): UIO[Unit]
    }

  private def logErrors(e: Either[Unit, String]): UIO[Unit] = {
    e.foreach(s => Logger.error(s))
    ZIO.succeed(())
  }

  private def tryGetJob: URIO[Db, Option[Model[Job]]] =
    ZIO.accessM(_.get.runDbCon(JobsQueries.tryGetjob.option))

  private def logJob(job: Model[Job]): UIO[Unit] =
    ZIO.descriptorWith(desc => UIO(Logger.debug(s"Starting on fiber ${desc.id} job $job")))

  private def updateJobInfo(job: Job)(update: JobInfo => JobInfo): Job =
    job.copy(info = update(job.info).copy(lastUpdated = Some(OffsetDateTime.now())))

  private def asFatalFailure(job: Job, error: String, errorDescriptor: String): Job =
    updateJobInfo(job)(asFatalFailureInfo(_, error, errorDescriptor))

  private def asFatalFailureInfo(jobInfo: JobInfo, error: String, errorDescriptor: String): JobInfo = {
    jobInfo.copy(
      lastUpdated = Some(OffsetDateTime.now()),
      lastError = Some(error),
      lastErrorDescriptor = Some(errorDescriptor),
      state = Job.JobState.FatalFailure
    )
  }

  private def decodeJob(job: Model[Job]): ZIO[Db, String, Model[Job.TypedJob]] = {
    job.toTyped.map(r => Model[Job.TypedJob](ObjId(job.id.value), job.createdAt, r)) match {
      case Left(e) =>
        ZIO
          .accessM[Db](_.get.update(job)(asFatalFailure(_, e, "decode_failed")))
          .andThen(ZIO.fail(s"Failed to decode job\nJob: $job\nError: $e"))

      case Right(typedJob) => ZIO.succeed(typedJob)
    }
  }

  private def convertJob[A, B](typed: Model[Job.TypedJob], ctx: A)(f: (A, Model[Job]) => B): B =
    f(ctx, Model(ObjId(typed.id), typed.createdAt, typed.toJob))

  private def updateJob(job: Model[Job.TypedJob], update: JobInfo => JobInfo): URIO[Db, Model[Job]] =
    ZIO.accessM[Db](convertJob(job, _)(_.get.update(_)(updateJobInfo(_)(update))))

  private def setLastUpdated(job: Model[Job.TypedJob]): URIO[Db, Unit] =
    updateJob(job, identity).unit

  private def finishJob(job: Model[Job.TypedJob]): URIO[Db, Unit] =
    updateJob(job, _.copy(state = Job.JobState.Done)).unit

  private def getDataOrFatal[A](
      data: UIO[Option[A]],
      job: Model[Job.TypedJob],
      error: => String,
      errorDescriptor: String
  ): ZIO[Db, Either[Unit, String], A] = data.flatMap {
    case Some(value) => ZIO.succeed(value)
    case None =>
      updateJob(job, asFatalFailureInfo(_, error, errorDescriptor)) *> ZIO.fail(Right(error))
  }

  private def processJob(
      job: Model[Job.TypedJob]
  ): ZIO[Db with Discourse with Discord with Actors with Config, Either[Unit, String], Model[Job.TypedJob]] =
    setLastUpdated(job) *> ZIO.access[Db](_.get).flatMap { implicit service =>
      job.obj match {
        case Job.UpdateDiscourseProjectTopic(_, projectId) =>
          getDataOrFatal(
            ModelView.now(Project).get(projectId).value,
            job,
            s"Can't find project with id $projectId",
            "project_not_found"
          ).flatMap(updateProjectTopic(job, _))

        case Job.UpdateDiscourseVersionPost(_, versionId) =>
          val q = for {
            v <- TableQuery[VersionTable]
            p <- TableQuery[ProjectTable]
            if v.id === versionId && v.projectId === p.id
          } yield (v, p)

          getDataOrFatal(
            service.runDBIO(q.result.headOption),
            job,
            s"Can't find version with id $versionId",
            "version_not_found"
          ).flatMap {
            case (version, project) =>
              updateVersionPost(job, project, version)
          }

        case Job.DeleteDiscourseTopic(_, topicId) =>
          deleteTopic(job, topicId)

        case Job.PostDiscourseReply(_, topicId, poster, content) =>
          postReply(job, topicId, poster, content)

        case Job.PostWebhookResponse(
            _,
            projectOwner,
            projectSlug,
            webhookId,
            webhookSecret,
            callbackUrl,
            webhookType,
            data
            ) =>
          def optionToResult[A](s: String, opt: String => Option[A], history: List[CursorOp])(
              implicit tpe: Typeable[A]
          ): Either[DecodingFailure, A] =
            opt(s).toRight(DecodingFailure(s"$s is not a valid ${tpe.describe}", history))

          def valueEnumCodec[V, A <: ValueEnumEntry[V]: Typeable](
              enumObj: ValueEnum[V, A]
          )(name: A => String): Codec[A] =
            Codec.from(
              (c: HCursor) =>
                c.as[String].flatMap(optionToResult(_, s => enumObj.values.find(a => name(a) == s), c.history)),
              (a: A) => name(a).asJson
            )

          implicit val webhookEventTypeCodec: Codec[ore.models.project.Webhook.WebhookEventType] =
            valueEnumCodec(ore.models.project.Webhook.WebhookEventType)(_.value)

          val webhookExtraInfo = Json.obj(
            "project_owner" := projectOwner,
            "project_slug" := projectSlug,
            "event_type" := webhookType
          )

          executeWebhook(job, webhookId, webhookType, webhookSecret, callbackUrl, data, webhookExtraInfo)
      }
    }

  def handleDiscourseErrors(job: Model[Job.TypedJob])(
      program: Discourse => ZIO[Any, Throwable, Either[DiscourseError, Unit]]
  ): ZIO[Db with Discourse with Config, Either[Unit, String], Model[Job.TypedJob]] = {
    def retryIn(error: Option[String], errorDescriptor: Option[String])(duration: FiniteDuration) =
      updateJob(
        job,
        _.copy(
          retryAt = Some(OffsetDateTime.now().plusNanos((duration + 5.seconds).toNanos)),
          lastError = error.orElse(job.info.lastError),
          lastErrorDescriptor = errorDescriptor.orElse(job.info.lastErrorDescriptor),
          state = Job.JobState.NotStarted
        )
      ).andThen(ZIO.fail(error.toRight(())))

    def retryInConfig(error: Option[String], errorDescriptor: Option[String])(
        duration: OreJobsConfig => FiniteDuration
    ) =
      ZIO.access[Config](env => duration(env.get)) >>= retryIn(error, errorDescriptor)

    ZIO
      .accessM[Discourse](program)
      .catchSome {
        case _: TimeoutException =>
          ZIO.succeed(Left(()))

        case e: CircuitBreakerOpenException => retryIn(None, None)(e.remainingDuration).catchAll(ZIO.succeed(_))
      }
      .orDie
      .absolve
      .as(job)
      .catchAll {
        case DiscourseError.RatelimitError(waitTime) => retryIn(None, None)(waitTime)

        case DiscourseError.UnknownError(messages, tpe, extras) =>
          val e = s"""|Encountered error when executing Discourse request
                      |Job: ${job.obj.withoutError}
                      |Type: $tpe
                      |Messages: ${messages.mkString("\n  ", "\n  ", "")}
                      |Extras: ${extras.mkString("\n  ", "\n  ", "")}""".stripMargin

          retryInConfig(Some(e), Some(s"unknown_error_$tpe"))(_.jobs.timeouts.unknownError)
        case DiscourseError.StatusError(statusCode, message) =>
          val e = s"""|Encountered status error when executing Discourse request
                      |Job: ${job.obj.withoutError}
                      |Status code: $statusCode
                      |Message: ${message.getOrElse("None")}""".stripMargin

          retryInConfig(Some(e), Some(s"status_error_${statusCode.intValue}"))(_.jobs.timeouts.statusError)
        case DiscourseError.NotAvailable => retryInConfig(None, None)(_.jobs.timeouts.notAvailable)
      }
  }

  private def updateProjectTopic(job: Model[Job.TypedJob], project: Model[Project]) =
    handleDiscourseErrors(job) { env =>
      if (project.topicId.isDefined) env.get.updateProjectTopic(project)
      else env.get.createProjectTopic(project).map(_.void)
    }

  private def updateVersionPost(job: Model[Job.TypedJob], project: Model[Project], version: Model[Version]) = {
    handleDiscourseErrors(job) { env =>
      val doVersion = (project: Model[Project]) =>
        if (version.postId.isDefined) env.get.updateVersionPost(project, version)
        else env.get.createVersionPost(project, version).map(_.void)

      if (project.topicId.isDefined) {
        doVersion(project)
      } else {
        env.get.createProjectTopic(project).flatMap {
          case Left(e)      => ZIO.succeed(Left(e))
          case Right(value) => doVersion(value)
        }
      }
    }
  }

  private def deleteTopic(job: Model[Job.TypedJob], topicId: Int) =
    handleDiscourseErrors(job)(_.get.deleteTopic(topicId))

  private def postReply(job: Model[Job.TypedJob], topicId: Int, poster: String, content: String) =
    handleDiscourseErrors(job)(_.get.postDiscussionReply(topicId, poster, content).map(_.void))

  private val discordWebhookUrl =
    """https://(?:(?:discordapp)|(?:discord))\.com/api/(?:(?:v6/)|(?:v8/))?webhooks/(\d+)/([^/]+)""".r

  private def executeWebhook(
      job: Model[Job.TypedJob],
      webhookId: DbRef[Webhook],
      webhookType: Webhook.WebhookEventType,
      webhookSecret: String,
      url: String,
      data: Json,
      webhookExtraInfo: Json
  ) = url match {
    case discordWebhookUrl(webhookId, webhookToken) =>
      executeDiscordWebhook(job, webhookId, webhookToken, data)
    case _ =>
      def fatalError(error: String, errorDescriptor: String) =
        updateJob(
          job,
          asFatalFailureInfo(_, error, errorDescriptor)
        ).andThen(ZIO.succeed(error.asRight[Unit]))

      ZIO
        .accessM[Actors] { hasActors =>
          implicit val system: ActorSystem = hasActors.get

          val body     = Json.obj("webhook_meta_info" -> webhookExtraInfo, "data" -> data).noSpaces
          val unixTime = System.currentTimeMillis() / 1000
          val signature = CryptoUtils.hmac_sha256(
            webhookSecret,
            ByteBuffer.allocate(8).putLong(unixTime).array ++ body.getBytes("UTF-8")
          )

          ZIO
            .fromFuture { _ =>
              Http().singleRequest(
                HttpRequest(
                  method = HttpMethods.POST,
                  uri = url,
                  headers = Seq(
                    RawHeader("Ore-Webhook-EventType", webhookType.value),
                    RawHeader("Ore-Webhook-Timestamp", unixTime.toString),
                    RawHeader("Ore-Webhook-HMACSignature", signature)
                  ),
                  entity = HttpEntity(ContentTypes.`application/json`, body)
                )
              )
            }
            .tap(response => UIO(response.discardEntityBytes()))
        }
        .flatMapError { e =>
          fatalError(s"Failed to send webhook to $url with error ${e.getMessage}", "webhook_request_error")
        }
        .flatMap { response =>
          if (response.status.isSuccess) UIO.succeed(job)
          else
            ZIO
              .accessM[Db](
                _.get.runDBIO(
                  TableQuery[WebhookTable]
                    .filter(_.id === webhookId)
                    .map(_.lastError.?)
                    .update(Some(s"Encountered response code: ${response.status.intValue}"))
                )
              )
              .as(job)
        }
  }

  private def executeDiscordWebhook(
      job: Model[Job.TypedJob],
      webhookIdString: String,
      webhookToken: String,
      json: Json
  ) = {
    implicit val ExecuteWebhookDataDecoder: Decoder[ExecuteWebhookData] = (c: HCursor) => {
      import ackcord.data.DiscordProtocol._
      for {
        content         <- c.get[String]("content")
        username        <- c.get[Option[String]]("username")
        avatarUrl       <- c.get[Option[String]]("avatar_url")
        tts             <- c.get[Option[Boolean]]("tts")
        embeds          <- c.get[Seq[OutgoingEmbed]]("embeds")
        allowedMentions <- c.get[Option[AllowedMention]]("allowed_mentions")
        flags           <- c.get[MessageFlags]("flags")
      } yield ExecuteWebhookData(content, username, avatarUrl, tts, Nil, embeds, allowedMentions, flags)

    }

    def fatalError(error: String, errorDescriptor: String) =
      updateJob(
        job,
        asFatalFailureInfo(_, error, errorDescriptor)
      ).andThen(ZIO.succeed(error.asRight[Unit]))

    def retryIn(error: Option[String], errorDescriptor: Option[String])(duration: FiniteDuration) =
      updateJob(
        job,
        _.copy(
          retryAt = Some(OffsetDateTime.now().plusNanos((duration + 5.seconds).toNanos)),
          lastError = error.orElse(job.info.lastError),
          lastErrorDescriptor = errorDescriptor.orElse(job.info.lastErrorDescriptor),
          state = Job.JobState.NotStarted
        )
      ).andThen(ZIO.fail(error.toRight(())))

    for {
      //TODO: Fix this cast in AckCord
      webhookId <- ZIO(
        SnowflakeType[ackcord.data.Webhook](webhookIdString)
          .asInstanceOf[ackcord.data.SnowflakeType[ackcord.data.Webhook]]
      ).flatMapError(_ => fatalError(s"$webhookIdString is not a valid snowflake", "discord_invalid_snowflake"))
      executeWebhookData <- ZIO
        .fromEither(json.as[ExecuteWebhookData])
        .flatMapError(decodeFailure => fatalError(decodeFailure.show, "discord_decode_execute_webhook_data_failed"))
      request = ExecuteWebhook(webhookId, webhookToken, waitQuery = false, executeWebhookData)
      answer <- ZIO.accessM[Discord](d => ZIO.fromFuture(_ => d.get.singleFuture(request))).flatMapError { e =>
        fatalError(
          s"""|Failed to send discord webhook 
              |Error: ${e.getMessage}
              |WebhookId: $webhookIdString
              |WebhookToken: $webhookToken""".stripMargin,
          "discord_run_request_failed"
        )
      }
      _ <- answer match {
        case RequestResponse(_, _, _, _)                => UIO.unit
        case RequestRatelimited(_, ratelimitInfo, _, _) => retryIn(None, None)(ratelimitInfo.tilReset)
        case RequestError(e: HttpException, _, _) =>
          fatalError(
            s"""|Encountered error when executing discord webhook
                |StatusCode: ${e.statusCode.intValue}
                |StatusReason: ${e.statusCode.reason}
                |ExtraInfo: ${e.extraInfo.getOrElse("")}
                |WebhookId: $webhookIdString
                |WebhookToken: $webhookToken""".stripMargin,
            "discord_run_request_error"
          ).flip

        case RequestError(e, _, _) =>
          fatalError(
            s"""|Failed to send discord webhook 
                |Error: ${e.getMessage}
                |WebhookId: $webhookIdString
                |WebhookToken: $webhookToken""".stripMargin,
            "discord_run_request_failed"
          ).flip

        case RequestDropped(route, _) =>
          ZIO
            .accessM[Discord] { hasRequests =>
              import akka.actor.typed.scaladsl.AskPattern._
              val requests = hasRequests.get
              import requests.system

              implicit val askTimeout: Timeout = 1.second

              ZIO
                .fromFuture { _ =>
                  requests.settings.ratelimitActor.ask[Either[Duration, Int]](replyTo =>
                    Ratelimiter.QueryRatelimits(route, replyTo)
                  )
                }
                .map(_.swap.getOrElse(Duration.Zero))
                .orElseSucceed(60.seconds)
                .map {
                  case d: FiniteDuration => d
                  case _                 => 60.seconds
                }
            }
            .flatMap(retryIn(None, None))
      }
    } yield job
  }

}
