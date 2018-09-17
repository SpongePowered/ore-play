package ore.project

import models.project.{Project, Version}
import models.user.Notification
import ore.user.notification.NotificationTypes
import scala.concurrent.ExecutionContext

import cats.data.NonEmptyList
import db.{ModelService, ObjectReference}
import ore.OreConfig

/**
  * Notifies all [[models.user.User]]s that are watching the specified
  * [[Version]]'s [[models.project.Project]] that a new Version has been
  * released.
  *
  * @param version  New version
  * @param projects ProjectBase instance
  */
case class NotifyWatchersTask(version: Version, project: Project)(implicit ec: ExecutionContext, service: ModelService, config: OreConfig)
  extends Runnable {

  def run(): Unit = {
    val notification = (userId: ObjectReference) => Notification(
      userId = userId,
      originId = project.ownerId,
      notificationType = NotificationTypes.NewProjectVersion,
      messageArgs = NonEmptyList.of("notification.project.newVersion", project.name, version.name),
      action = Some(version.url(project))
    )

    for {
      watchers <- project.watchers.all
    } yield {
      for (watcher <- watchers.filterNot(_.userId == version.authorId))
        watcher.sendNotification(notification(watcher.id.value))
    }

  }

}
