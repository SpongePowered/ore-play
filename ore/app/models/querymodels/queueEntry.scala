package models.querymodels
import java.time.Instant

import ore.data.Color
import ore.data.project.ProjectNamespace
import ore.db.DbRef
import ore.models.user.User

case class UnsortedQueueEntry(
    namespace: ProjectNamespace,
    projectName: String,
    versionString: String,
    versionCreatedAt: Instant,
    channelName: String,
    channelColor: Color,
    versionAuthor: Option[String],
    reviewerId: Option[DbRef[User]],
    reviewerName: Option[String],
    reviewStarted: Option[Instant],
    reviewEnded: Option[Instant]
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
    versionCreatedAt: Instant,
    channelName: String,
    channelColor: Color,
    versionAuthor: Option[String],
    reviewerId: DbRef[User],
    reviewerName: String,
    reviewStarted: Instant,
    reviewEnded: Option[Instant]
) {

  def isUnfinished: Boolean = reviewEnded.nonEmpty
}

case class NotStartedQueueEntry(
    namespace: ProjectNamespace,
    projectName: String,
    versionString: String,
    versionCreatedAt: Instant,
    channelName: String,
    channelColor: Color,
    versionAuthor: Option[String]
)
