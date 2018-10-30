package ore.project

import scala.concurrent.ExecutionContext

import db.impl.OrePostgresDriver.api._
import db.{DbRef, ModelService}
import models.project.{Project, Version}
import models.user.{Notification, User}
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
    val notification = (userId: DbRef[User]) =>
      Notification(
        userId = userId,
        originId = project.ownerId,
        notificationType = NotificationType.NewProjectVersion,
        messageArgs = NonEmptyList.of("notification.project.newVersion", project.name, version.name),
        action = Some(version.url(project))
    )

    service.runDBIO(project.watchers.allQueryFromParent(project).filter(_.id =!= version.authorId).result).foreach {
      watchers =>
        watchers.foreach(watcher => watcher.sendNotification(notification(watcher.id.value)))
    }
  }
}
