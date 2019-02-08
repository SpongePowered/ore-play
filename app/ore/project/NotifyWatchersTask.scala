package ore.project

import scala.concurrent.ExecutionContext

import db.impl.OrePostgresDriver.api._
import db.{DbRef, ModelService}
import models.project.{Project, Version}
import models.user.{Notification, User}
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
    service: ModelService
) extends Runnable {

  private val notification = (userId: DbRef[User]) =>
    Notification.partial(
      userId = userId,
      originId = project.ownerId,
      notificationType = NotificationType.NewProjectVersion,
      messageArgs = NonEmptyList.of("notification.project.newVersion", project.name, version.name),
      action = Some(version.url(project))
  )

  private val watchingUsers =
    service.runDBIO(project.watchers.allQueryFromParent(project).filter(_.id =!= version.authorId).result)

  def run(): Unit =
    watchingUsers
      .unsafeToFuture()
      .foreach(_.foreach(watcher => service.insert(notification(watcher.id))))
}
