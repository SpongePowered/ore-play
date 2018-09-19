package ore.project

import scala.concurrent.ExecutionContext

import db.{ModelService, ObjectReference}
import models.project.{Project, Version}
import models.user.Notification
import ore.OreConfig
import ore.user.notification.NotificationType

import cats.data.NonEmptyList

/**
  * Notifies all [[models.user.User]]s that are watching the specified
  * [[Version]]'s [[models.project.Project]] that a new Version has been
  * released.
  *
  * @param version  New version
  * @param projects ProjectBase instance
  */
case class NotifyWatchersTask(version: Version, project: Project)(
    implicit ec: ExecutionContext,
    service: ModelService,
    config: OreConfig
) extends Runnable {

  def run(): Unit = {
    val notification = (userId: ObjectReference) =>
      Notification(
        userId = userId,
        originId = project.ownerId,
        notificationType = NotificationType.NewProjectVersion,
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
