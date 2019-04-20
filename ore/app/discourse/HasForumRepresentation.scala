package discourse

import scala.language.higherKinds

import ore.OreConfig
import ore.db.{Model, ModelService}
import ore.models.project.{Page, Version}
import util.IOUtils
import util.syntax._

import cats.effect.IO

trait HasForumRepresentation[F[_], A] {

  def updateForumContents(a: Model[A])(contents: String): F[Model[A]]
}
object HasForumRepresentation {

  implicit def pageHasForumRepresentation(
      implicit service: ModelService[IO],
      config: OreConfig,
      forums: OreDiscourseApi
  ): HasForumRepresentation[IO, Page] = new HasForumRepresentation[IO, Page] {
    override def updateForumContents(a: Model[Page])(contents: String): IO[Model[Page]] = {
      require(
        (a.isHome && contents.length <= Page.maxLength) || contents.length <= Page.maxLengthPage,
        "contents too long",
      )
      for {
        updated <- service.update(a)(_.copy(contents = contents))
        project <- a.project
        // Contents were updated, update on forums
        _ <- if (a.name == Page.homeName && project.topicId.isDefined)
          forums
            .updateProjectTopic(project)
            .runAsync(IOUtils.logCallback("Failed to update page with forums", logger))
            .toIO
        else IO.unit
      } yield updated
    }
  }

  implicit def versionHasForumRepresentation(
      implicit service: ModelService[IO],
      forums: OreDiscourseApi
  ): HasForumRepresentation[IO, Version] = new HasForumRepresentation[IO, Version] {
    override def updateForumContents(a: Model[Version])(contents: String): IO[Model[Version]] = {
      for {
        project <- a.project
        updated <- service.update(a)(_.copy(description = Some(contents)))
        _ <- if (project.topicId.isDefined && a.postId.isDefined) forums.updateVersionPost(project, updated)
        else IO.pure(false)
      } yield updated
    }
  }
}
