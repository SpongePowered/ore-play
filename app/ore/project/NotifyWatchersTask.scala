package ore.project

import db.impl.access.ProjectBase
import models.project.Version
import models.user.Notification
import ore.user.notification.NotificationTypes
import play.api.i18n.{Lang, MessagesApi}

/**
  * Notifies all [[models.user.User]]s that are watching the specified
  * [[Version]]'s [[models.project.Project]] that a new Version has been
  * released.
  *
  * @param version  New version
  * @param messages MessagesApi instance
  * @param projects ProjectBase instance
  */
case class NotifyWatchersTask(version: Version, messages: MessagesApi)(implicit projects: ProjectBase)
  extends Runnable {

  implicit val lang = Lang.defaultLang

  def run() = {
    val project = version.project
    val notification = Notification(
      originId = project.ownerId,
      notificationType = NotificationTypes.NewProjectVersion,
      message = messages("notification.project.newVersion", project.name, version.name),
      action = Some(version.url)
    )
    for (watcher <- project.watchers.all.filterNot(_.userId == version.author.get.userId))
      watcher.sendNotification(notification)
  }

}
