package controllers.project

import controllers.OreBaseController
import controllers.sugar.{Bakery, Requests}
import db.ModelService
import form.OreForms
import javax.inject.Inject

import ore.permission.EditChannels
import ore.project.factory.ProjectFactory
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.html.projects.{channels => views}
import util.instances.future._
import scala.concurrent.{ExecutionContext, Future}

import models.project.Project
import models.viewhelper.ProjectData
import play.api.mvc.{Action, AnyContent}
import util.functional.EitherT
import util.syntax._

/**
  * Controller for handling Channel related actions.
  */
class Channels @Inject()(forms: OreForms,
                         factory: ProjectFactory)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    cache: AsyncCacheApi,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    messagesApi: MessagesApi,
    env: OreEnv,
    config: OreConfig,
    service: ModelService
) extends OreBaseController {

  private val self = controllers.project.routes.Channels

  private def ChannelEditAction(author: String, slug: String)
  = AuthedProjectAction(author, slug, requireUnlock = true) andThen ProjectPermissionAction(EditChannels)

  /**
    * Displays a view of the specified Project's Channels.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return View of channels
    */
  def showList(author: String, slug: String): Action[AnyContent] = ChannelEditAction(author, slug).async { request =>
    implicit val r: Requests.AuthRequest[AnyContent] = request.request
    for {
      channels <- request.data.project.channels.toSeq
      versionCount <- Future.sequence(channels.map(_.versions.size))
    } yield {
      val listWithVersionCount = channels zip versionCount
      Ok(views.list(request.data, listWithVersionCount))
    }
  }

  /**
    * Creates a submitted channel for the specified Project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Redirect to view of channels
    */
  def create(author: String, slug: String): Action[AnyContent] = ChannelEditAction(author, slug).async { implicit request =>
    val res = for {
      channelData <- bindFormEitherT[Future](this.forms.ChannelEdit)(hasErrors => Redirect(self.showList(author, slug)).withFormErrors(hasErrors.errors))
      _ <- channelData.addTo(request.data.project).leftMap(error => Redirect(self.showList(author, slug)).withError(error))
    } yield Redirect(self.showList(author, slug))

    res.merge
  }

  /**
    * Submits changes to an existing channel.
    *
    * @param author      Project owner
    * @param slug        Project slug
    * @param channelName Channel name
    * @return View of channels
    */
  def save(author: String, slug: String, channelName: String): Action[AnyContent] = ChannelEditAction(author, slug).async { implicit request =>
    implicit val project: Project = request.data.project

    val res = for {
      channelData <- bindFormEitherT[Future](this.forms.ChannelEdit)(hasErrors => Redirect(self.showList(author, slug)).withFormErrors(hasErrors.errors))
      _ <- channelData.saveTo(channelName).leftMap(errors => Redirect(self.showList(author, slug)).withErrors(errors))
    } yield Redirect(self.showList(author, slug))

    res.merge
  }

  /**
    * Irreversibly deletes the specified channel and all version associated
    * with it.
    *
    * @param author      Project owner
    * @param slug        Project slug
    * @param channelName Channel name
    * @return View of channels
    */
  def delete(author: String, slug: String, channelName: String): Action[AnyContent] = ChannelEditAction(author, slug).async { implicit request =>
    implicit val data: ProjectData = request.data
    EitherT.right[Status](data.project.channels.all)
      .filterOrElse(_.size != 1, Redirect(self.showList(author, slug)).withError("error.channel.last"))
      .flatMap { channels =>
        EitherT.fromEither[Future](channels.find(_.name == channelName).toRight(NotFound))
          .semiFlatMap { channel =>
            (channel.versions.isEmpty, Future.traverse(channels.toSeq)(_.versions.nonEmpty).map(_.count(identity)))
              .parTupled
              .tupleRight(channel)
          }
          .filterOrElse(
            { case ((emptyChannel, nonEmptyChannelCount), _) =>
              emptyChannel || nonEmptyChannelCount > 1},
            Redirect(self.showList(author, slug)).withError("error.channel.lastNonEmpty")
          )
          .map(_._2)
          .filterOrElse(
            channel => channel.isNonReviewed || channels.count(_.isReviewed) > 1,
            Redirect(self.showList(author, slug)).withError("error.channel.lastReviewed")
          )
          .semiFlatMap(channel => projects.deleteChannel(channel))
          .map(_ => Redirect(self.showList(author, slug)))
      }.merge
  }
}
