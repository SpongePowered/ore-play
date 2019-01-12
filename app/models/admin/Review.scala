package models.admin

import java.sql.Timestamp

import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.twirl.api.Html

import db.impl.schema.ReviewTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.project.{Page, Project, Version}
import models.user.User
import ore.OreConfig
import _root_.util.StringUtils

import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents an approval instance of [[Project]] [[Version]].
  *
  * @param id           Unique ID
  * @param createdAt    When it was created
  * @param versionId    User who is approving
  * @param userId       User who is approving
  * @param endedAt      When the approval process ended
  * @param message      Message of why it ended
  */
case class Review(
    id: ObjId[Review],
    createdAt: ObjectTimestamp,
    versionId: DbRef[Version],
    userId: DbRef[User],
    endedAt: Option[Timestamp],
    message: JsValue
) extends Model {

  /** Self referential type */
  override type M = Review

  /** The model's table */
  override type T = ReviewTable

  /**
    * Add new message
    */
  def addMessage(message: Message)(implicit service: ModelService): IO[Review] = {
    val messages = decodeMessages :+ message
    service.update(
      copy(
        message = JsObject(
          Seq("messages" -> Json.toJson(messages))
        )
      )
    )
  }

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
  def getTime(implicit messages: Messages): String = StringUtils.prettifyDateAndTime(new Timestamp(time))
  def isTakeover: Boolean                          = action.equalsIgnoreCase("takeover")
  def isStop: Boolean                              = action.equalsIgnoreCase("stop")
  def render(implicit oreConfig: OreConfig): Html  = Page.render(message)
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

object Review {
  def partial(
      versionId: DbRef[Version],
      userId: DbRef[User],
      endedAt: Option[Timestamp],
      message: JsValue
  ): InsertFunc[Review] = (id, time) => Review(id, time, versionId, userId, endedAt, message)

  def ordering: Ordering[(Review, _)] =
    // TODO make simple + check order
    Ordering.by(_._1.createdAt.value.getTime)

  def ordering2: Ordering[Review] =
    // TODO make simple + check order
    Ordering.by(_.createdAt.value.getTime)

  implicit val query: ModelQuery[Review] =
    ModelQuery.from[Review](TableQuery[ReviewTable], _.copy(_, _))
}
