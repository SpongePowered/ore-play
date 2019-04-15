package models.admin

import java.sql.Timestamp

import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.twirl.api.Html

import db.impl.DefaultModelCompanion
import db.impl.schema.ReviewTable
import models.project.{Project, Version}
import models.user.User
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.markdown.MarkdownRenderer
import _root_.util.StringUtils

import cats.effect.IO
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
    endedAt: Option[Timestamp],
    message: JsValue
) {

  /**
    * Get all messages
    * @return
    */
  def decodeMessages: Seq[Message] =
    (message \ "messages").asOpt[Seq[Message]].getOrElse(Nil)
}

/**
  * This modal is needed to convert the json
  */
case class Message(message: String, time: Long = System.currentTimeMillis(), action: String = "message") {
  def getTime(implicit messages: Messages): String      = StringUtils.prettifyDateAndTime(new Timestamp(time))
  def isTakeover: Boolean                               = action.equalsIgnoreCase("takeover")
  def isStop: Boolean                                   = action.equalsIgnoreCase("stop")
  def render(implicit renderer: MarkdownRenderer): Html = renderer.render(message)
}
object Message {
  implicit val messageReads: Reads[Message] =
    (JsPath \ "message")
      .read[String]
      .and((JsPath \ "time").read[Long])
      .and((JsPath \ "action").read[String])(Message.apply _)

  implicit val messageWrites: Writes[Message] = (message: Message) =>
    Json.obj(
      "message" -> message.message,
      "time"    -> message.time,
      "action"  -> message.action
  )
}

object Review extends DefaultModelCompanion[Review, ReviewTable](TableQuery[ReviewTable]) {

  def ordering: Ordering[(Model[Review], _)] =
    // TODO make simple + check order
    Ordering.by(_._1.createdAt.getTime)

  def ordering2: Ordering[Model[Review]] =
    // TODO make simple + check order
    Ordering.by(_.createdAt.getTime)

  implicit val query: ModelQuery[Review] =
    ModelQuery.from(this)

  implicit class ReviewModelOps(private val self: Model[Review]) extends AnyVal {

    /**
      * Add new message
      */
    def addMessage(message: Message)(implicit service: ModelService): IO[Model[Review]] = {
      val messages = self.decodeMessages :+ message
      service.update(self)(
        _.copy(
          message = JsObject(
            Seq("messages" -> Json.toJson(messages))
          )
        )
      )
    }
  }
}
