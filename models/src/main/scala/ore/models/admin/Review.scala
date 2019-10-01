package ore.models.admin

import scala.language.higherKinds

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.Locale

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.ReviewTable
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.models.project.{Project, Version}
import ore.models.user.User
import ore.util.StringLocaleFormatterUtils

import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.syntax._
import slick.lifted.TableQuery

/**
  * Represents an approval instance of [[Project]] [[Version]].
  *
  * @param versionId    User who is approving
  * @param userId       User who is approving
  * @param endedAt      When the approval process ended
  * @param message      Message of why it ended
  */
case class Review(
    versionId: DbRef[Version],
    userId: DbRef[User],
    endedAt: Option[OffsetDateTime],
    message: Json
) {

  /**
    * Get all messages
    * @return
    */
  def decodeMessages: Seq[Message] =
    message.hcursor
      .getOrElse[Seq[Message]]("messages")(Nil)
      .toTry
      .get //Should be safe. If it's not we have bigger problems
}

/**
  * This modal is needed to convert the json
  */
@JsonCodec case class Message(message: String, time: Long = System.currentTimeMillis(), action: String = "message") {
  def getTime(implicit locale: Locale): String =
    StringLocaleFormatterUtils.prettifyDateAndTime(OffsetDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC))
  def isTakeover: Boolean = action.equalsIgnoreCase("takeover")
  def isStop: Boolean     = action.equalsIgnoreCase("stop")
}

object Review extends DefaultModelCompanion[Review, ReviewTable](TableQuery[ReviewTable]) {

  def ordering: Ordering[(Model[Review], _)] =
    // TODO make simple + check order
    Ordering.by(_._1.createdAt.value)

  def ordering2: Ordering[Model[Review]] =
    // TODO make simple + check order
    Ordering.by(_.createdAt.value)

  implicit val query: ModelQuery[Review] =
    ModelQuery.from(this)

  implicit class ReviewModelOps(private val self: Model[Review]) extends AnyVal {

    /**
      * Add new message
      */
    def addMessage[F[_]](message: Message)(implicit service: ModelService[F]): F[Model[Review]] = {
      val messages = self.decodeMessages :+ message
      service.update(self)(
        _.copy(
          message = Json.obj(
            "messages" := messages
          )
        )
      )
    }
  }
}
