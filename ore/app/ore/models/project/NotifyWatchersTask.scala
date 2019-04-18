package ore.models.project

import scala.concurrent.ExecutionContext

import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Project, Version}
import ore.models.user.{Notification, User}
import ore.db.{DbRef, Model, ModelService}
import ore.models.user.notification.NotificationType

import cats.data.NonEmptyList

/**
  * Notifies all [[ore.models.user.User]]s that are watching the specified
  * [[Version]]'s [[ore.models.project.Project]] that a new Version has been
  * released.
  *
  * @param version  New version
  * @param projects ProjectBase instance
  */
case class NotifyWatchersTask(version: Model[Version], project: Model[Project])(
    implicit ec: ExecutionContext,
    service: ModelService
) extends Runnable {

  private val notification = (userId: DbRef[User]) =>
    Notification(
      userId = userId,
      originId = project.ownerId,
      notificationType = NotificationType.NewProjectVersion,
      messageArgs = NonEmptyList.of("notification.project.newVersion", project.name, version.name),
      action = Some(version.url(project))
  )

  private val watchingUsers =
    service.runDBIO(project.watchers.allQueryFromParent.filter(_.id =!= version.authorId).result)

  def run(): Unit =
    watchingUsers
      .unsafeToFuture()
      .foreach(_.foreach(watcher => service.insert(notification(watcher.id))))
}
