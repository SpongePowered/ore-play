package models.querymodels
import java.sql.Timestamp

import db.DbRef
import models.user.User
import ore.Color

case class UnsortedQueueEntry(
    namespace: ProjectNamespace,
    projectName: String,
    versionString: String,
    versionCreatedAt: Timestamp,
    channelName: String,
    channelColor: Color,
    versionAuthor: Option[String],
    reviewerId: Option[DbRef[User]],
    reviewerName: Option[String],
    reviewStarted: Option[Timestamp],
    reviewEnded: Option[Timestamp]
) {

  def sort: Either[ReviewedQueueEntry, NotStartedQueueEntry] =
    if (reviewerId.isDefined) {
      Left(
        ReviewedQueueEntry(
          namespace,
          projectName,
          versionString,
          versionCreatedAt,
          channelName,
          channelColor,
          versionAuthor,
          reviewerId.get,
          reviewerName.get,
          reviewStarted.get,
          reviewEnded
        )
      )
    } else {
      Right(
        NotStartedQueueEntry(
          namespace,
          projectName,
          versionString,
          versionCreatedAt,
          channelName,
          channelColor,
          versionAuthor
        )
      )
    }
}

case class ReviewedQueueEntry(
    namespace: ProjectNamespace,
    projectName: String,
    versionString: String,
    versionCreatedAt: Timestamp,
    channelName: String,
    channelColor: Color,
    versionAuthor: Option[String],
    reviewerId: DbRef[User],
    reviewerName: String,
    reviewStarted: Timestamp,
    reviewEnded: Option[Timestamp]
) {

  def isUnfinished: Boolean = reviewEnded.nonEmpty
}

case class NotStartedQueueEntry(
    namespace: ProjectNamespace,
    projectName: String,
    versionString: String,
    versionCreatedAt: Timestamp,
    channelName: String,
    channelColor: Color,
    versionAuthor: Option[String]
)
