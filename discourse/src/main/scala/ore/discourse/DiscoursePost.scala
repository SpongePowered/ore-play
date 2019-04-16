package ore.discourse

import java.time.LocalDateTime

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}

case class DiscoursePost(
    postId: Int,
    topicId: Int,
    userId: Int,
    username: String,
    topicSlug: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    content: String,
    replyCount: Int,
    postNum: Int
) {

  def isTopic: Boolean = postNum == 1

  def isDeleted: Boolean = deletedAt.isDefined
}
object DiscoursePost {

  implicit val decoder: Decoder[DiscoursePost] = (c: HCursor) => {
    for {
      postId     <- c.get[Int]("id")
      topicId    <- c.get[Int]("topic_id")
      userId     <- c.get[Int]("user_id")
      username   <- c.get[String]("username")
      topicSlug  <- c.get[String]("topic_slug")
      createdAt  <- c.get[LocalDateTime]("created_at")
      updatedAt  <- c.get[LocalDateTime]("updated_at")
      deletedAt  <- c.get[Option[LocalDateTime]]("deleted_at")
      content    <- c.get[String]("cooked")
      replyCount <- c.get[Int]("reply_count")
      postNum    <- c.get[Int]("post_number")
    } yield
      DiscoursePost(
        postId,
        topicId,
        userId,
        username,
        topicSlug,
        createdAt,
        updatedAt,
        deletedAt,
        content,
        replyCount,
        postNum
      )
  }
}
