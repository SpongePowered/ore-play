package ore.project

import db.impl.access.ProjectBase
import models.project.{Project, Version}
import models.user.Notification
import ore.user.notification.NotificationTypes
import play.api.i18n.{Lang, MessagesApi}

import scala.concurrent.ExecutionContext

/**
  * Notifies all [[models.user.User]]s that are watching the specified
  * [[Version]]'s [[models.project.Project]] that a new Version has been
  * released.
  *
  * @param version  New version
  * @param messages MessagesApi instance
  * @param projects ProjectBase instance
  */
case class NotifyWatchersTask(version: Version, project: Project, messages: MessagesApi)(implicit projects: ProjectBase, ec: ExecutionContext)
  extends Runnable {

  implicit val lang: Lang = Lang.defaultLang

  def run(): Unit = {
    val notification = Notification(
      originId = project.ownerId,
      notificationType = NotificationTypes.NewProjectVersion,
      message = messages("notification.project.newVersion", project.name, version.name),
      action = Some(version.url(project))
    )
    for {
      watchers <- project.watchers.all
    } yield {
      for (watcher <- watchers.filterNot(_.userId == version.authorId))
        watcher.sendNotification(notification)
    }

  }

}
