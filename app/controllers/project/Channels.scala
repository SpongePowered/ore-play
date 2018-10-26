package controllers.project

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import play.api.cache.AsyncCacheApi
import play.api.mvc.{Action, AnyContent}

import controllers.OreBaseController
import controllers.sugar.Bakery
import db.ModelService
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ChannelTable, VersionTable}
import form.OreForms
import form.project.ChannelData
import ore.permission.EditChannels
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.html.projects.{channels => views}

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Controller for handling Channel related actions.
  */
class Channels @Inject()(forms: OreForms)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    cache: AsyncCacheApi,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    env: OreEnv,
    config: OreConfig,
    service: ModelService
) extends OreBaseController {

  private val self = controllers.project.routes.Channels

  private def ChannelEditAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true).andThen(ProjectPermissionAction(EditChannels))

  /**
    * Displays a view of the specified Project's Channels.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return View of channels
    */
  def showList(author: String, slug: String): Action[AnyContent] = ChannelEditAction(author, slug).async {
    implicit request =>
      val query = for {
        channel <- TableQuery[ChannelTable] if channel.projectId === request.project.id.value
      } yield (channel, TableQuery[VersionTable].filter(_.channelId === channel.id).length)

      service.runDBIO(query.result).map(listWithVersionCount => Ok(views.list(request.data, listWithVersionCount)))
  }

  /**
    * Creates a submitted channel for the specified Project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Redirect to view of channels
    */
  def create(author: String, slug: String): Action[ChannelData] =
    ChannelEditAction(author, slug).asyncEitherT(
      parse.form(forms.ChannelEdit, onErrors = FormError(self.showList(author, slug)))
    ) { request =>
      request.body
        .addTo(request.project)
        .leftMap(Redirect(self.showList(author, slug)).withError(_))
        .map(_ => Redirect(self.showList(author, slug)))
    }

  /**
    * Submits changes to an existing channel.
    *
    * @param author      Project owner
    * @param slug        Project slug
    * @param channelName Channel name
    * @return View of channels
    */
  def save(author: String, slug: String, channelName: String): Action[ChannelData] =
    ChannelEditAction(author, slug).asyncEitherT(
      parse.form(forms.ChannelEdit, onErrors = FormError(self.showList(author, slug)))
    ) { request =>
      request.body
        .saveTo(request.project, channelName)
        .leftMap(errors => Redirect(self.showList(author, slug)).withErrors(errors.toList))
        .map(_ => Redirect(self.showList(author, slug)))
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
  def delete(author: String, slug: String, channelName: String): Action[AnyContent] =
    ChannelEditAction(author, slug).asyncEitherT { implicit request =>
      EitherT
        .right[Status](request.project.channels.all)
        .ensure(Redirect(self.showList(author, slug)).withError("error.channel.last"))(_.size != 1)
        .flatMap { channels =>
          EitherT
            .fromEither[Future](channels.find(_.name == channelName).toRight(NotFound))
            .semiflatMap { channel =>
              (channel.versions.isEmpty, Future.traverse(channels.toSeq)(_.versions.nonEmpty).map(_.count(identity))).tupled
                .tupleRight(channel)
            }
            .ensure(Redirect(self.showList(author, slug)).withError("error.channel.lastNonEmpty"))(
              { case ((emptyChannel, nonEmptyChannelCount), _) => emptyChannel || nonEmptyChannelCount > 1 }
            )
            .map(_._2)
            .ensure(Redirect(self.showList(author, slug)).withError("error.channel.lastReviewed"))(
              channel => channel.isNonReviewed || channels.count(_.isReviewed) > 1
            )
            .semiflatMap(channel => projects.deleteChannel(request.project, channel))
            .map(_ => Redirect(self.showList(author, slug)))
        }
    }
}
