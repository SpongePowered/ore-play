package util.uiowrappers

import javax.inject.Inject

import discourse.OreDiscourseApi
import ore.db.Model
import ore.discourse.DiscoursePost
import ore.models.project.{Project, Version}
import ore.models.user.User

import scalaz.zio.{Task, UIO}

class UIOOreDiscourseApi @Inject()(underlying: OreDiscourseApi[Task]) extends OreDiscourseApi[UIO] {
  override def createProjectTopic(project: Model[Project]): UIO[Model[Project]] = createProjectTopic(project).orDie

  override def updateProjectTopic(project: Model[Project]): UIO[Boolean] = updateProjectTopic(project).orDie

  override def postDiscussionReply(project: Project, user: User, content: String): UIO[Either[String, DiscoursePost]] =
    postDiscussionReply(project, user, content).orDie

  override def createVersionPost(project: Model[Project], version: Model[Version]): UIO[Model[Version]] =
    underlying.createVersionPost(project, version).orDie

  override def updateVersionPost(project: Model[Project], version: Model[Version]): UIO[Boolean] =
    underlying.updateVersionPost(project, version).orDie

  override def changeTopicVisibility(project: Project, isVisible: Boolean): UIO[Unit] =
    underlying.changeTopicVisibility(project, isVisible).orDie

  override def deleteProjectTopic(project: Model[Project]): UIO[Model[Project]] =
    underlying.deleteProjectTopic(project).orDie

  override def isAvailable: UIO[Boolean] = underlying.isAvailable.orDie
}
