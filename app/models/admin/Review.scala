package models.admin

import java.sql.Timestamp
import java.time.Instant

import db.{Model, ObjectId, ObjectReference, ObjectTimestamp}
import db.impl.ReviewTable
import db.impl.model.OreModel
import db.impl.schema.ReviewSchema
import db.impl.table.ModelKeys._
import models.project.{Page, Project, Version}
import ore.OreConfig
import play.api.libs.functional.syntax._
import play.twirl.api.Html
import util.StringUtils
import play.api.libs.json._
import scala.concurrent.Future

import play.api.i18n.Messages


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
case class Review(override val id: ObjectId = ObjectId.Uninitialized,
                  override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                  versionId: ObjectReference = -1,
                  userId: ObjectReference,
                  var endedAt: Option[Timestamp],
                  var message: String) extends OreModel(id, createdAt) {

  /** Self referential type */
  override type M = Review
  /** The model's table */
  override type T = ReviewTable
  /** The model's schema */
  override type S = ReviewSchema

  /**
    * Set a message and update the database
    * @param content
    * @return
    */
  private def setMessage(content: String) = {
    this.message = content
    update(Comment)
  }

  /**
    * Add new message
    * @param message
    * @return
    */
  def addMessage(message: Message): Future[Int] = {

    /**
      * Helper function to encode to json
      */
    implicit val messageWrites: Writes[Message] = new Writes[Message] {
      def writes(message: Message): JsObject = Json.obj(
        "message" -> message.message,
        "time" -> message.time,
        "action" -> message.action
      )
    }

    val messages = getMessages() :+ message
    val js: Seq[JsValue] = messages.map(m => Json.toJson(m))
    setMessage(
        Json.stringify(
        JsObject(Seq(
          "messages" -> JsArray(
            js
          )
        ))
      )
    )
  }

  /**
    * Get all messages
    * @return
    */
  def getMessages(): Seq[Message] = {
    if (message.startsWith("{")  && message.endsWith("}")) {
      val messages: JsValue = Json.parse(message)
      (messages \ "messages").as[Seq[Message]]
    } else {
      Seq()
    }
  }

  /**
    * Set time and update in db
    * @param time
    * @return
    */
  def setEnded(time: Option[Timestamp]): Future[Int] = {
    this.endedAt = time
    update(EndedAt)
  }

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id      ID to set
    * @param theTime Timestamp
    * @return Copy of model
    */
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model = this.copy(id = id, createdAt = createdAt)

  /**
    * Helper function to decode the json
    */
  implicit val messageReads: Reads[Message] = (
    (JsPath \ "message").read[String] and
    (JsPath \ "time").read[Long] and
    (JsPath \ "action").read[String]
  )(Message.apply _)


}

/**
  * This modal is needed to convert the json
  * @param time
  * @param message
  */
case class Message(message: String, time: Long = System.currentTimeMillis(), action: String = "message") {
  def getTime(implicit messages: Messages): String = StringUtils.prettifyDateAndTime(new Timestamp(time))
  def isTakeover(): Boolean = action.equalsIgnoreCase("takeover")
  def isStop(): Boolean = action.equalsIgnoreCase("stop")
  def render(implicit oreConfig: OreConfig): Html = Page.Render(message)
}


object Review {
  def ordering: Ordering[(Review, _)] = {
    // TODO make simple + check order
    Ordering.by(_._1.createdAt.value.getTime)
  }

  def ordering2: Ordering[Review] = {
    // TODO make simple + check order
    Ordering.by(_.createdAt.value.getTime)
  }
}
