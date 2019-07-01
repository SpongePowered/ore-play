package discourse

import scala.language.{higherKinds, implicitConversions}

import ore.OreConfig
import ore.db.{Model, ModelService}
import ore.models.project.{Page, Version}
import ore.util.OreMDC
import util.TaskUtils
import util.syntax._

import cats.MonadError
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.Effect
import com.typesafe.scalalogging

trait HasForumRepresentation[F[_], A] {

  def updateForumContents(a: Model[A])(contents: String): F[Model[A]]
}
object HasForumRepresentation {

  private val PagesLogger = scalalogging.Logger.takingImplicit[OreMDC]("Pages")

  implicit def pageHasForumRepresentation[F[_]](
      implicit service: ModelService[F],
      config: OreConfig,
      forums: OreDiscourseApi[F],
      F: Effect[F]
  ): HasForumRepresentation[F, Page] = new HasForumRepresentation[F, Page] {
    override def updateForumContents(a: Model[Page])(contents: String): F[Model[Page]] = {
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
            .runAsync(TaskUtils.logCallback("Failed to update page with forums", PagesLogger)(OreMDC.NoMDC))
            .to[F]
        else F.unit
      } yield updated
    }
  }

  implicit def versionHasForumRepresentation[F[_]](
      implicit service: ModelService[F],
      forums: OreDiscourseApi[F],
      F: MonadError[F, Throwable]
  ): HasForumRepresentation[F, Version] = new HasForumRepresentation[F, Version] {
    override def updateForumContents(a: Model[Version])(contents: String): F[Model[Version]] = {
      for {
        project <- a.project
        updated <- service.update(a)(_.copy(description = Some(contents)))
        _ <- if (project.topicId.isDefined && a.postId.isDefined) forums.updateVersionPost(project, updated)
        else F.pure(false)
      } yield updated
    }
  }

  class HasForumRepresentationOps[A](private val value: Model[A]) extends AnyVal {

    def updateForumContents[F[_]](contents: String)(
        implicit hasForumRepresentation: HasForumRepresentation[F, A]
    ): F[Model[A]] = hasForumRepresentation.updateForumContents(value)(contents)
  }

  trait ToHasForumRepresentationOps {
    implicit def hasForumRepresentationToOps[A](model: Model[A]): HasForumRepresentationOps[A] =
      new HasForumRepresentationOps(model)
  }
}
