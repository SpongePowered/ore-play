package controllers.project

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.cache.AsyncCacheApi
import play.api.mvc.{Action, AnyContent}

import controllers.OreBaseController
import controllers.sugar.Bakery
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ChannelTable, VersionTable}
import form.OreForms
import form.project.ChannelData
import ore.models.project.Channel
import ore.db.ModelService
import ore.db.access.ModelView
import ore.permission.Permission
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.html.projects.{channels => views}

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Controller for handling Channel related actions.
  */
class Channels @Inject()(forms: OreForms)(
    implicit val ec: ExecutionContext,
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer,
    env: OreEnv,
    config: OreConfig,
    service: ModelService[IO]
) extends OreBaseController {

  private val self = controllers.project.routes.Channels

  private def ChannelEditAction(author: String, slug: String) =
    AuthedProjectAction(author, slug, requireUnlock = true).andThen(ProjectPermissionAction(Permission.EditChannel))

  /**
    * Displays a view of the specified Project's Channels.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return View of channels
    */
  def showList(author: String, slug: String): Action[AnyContent] = ChannelEditAction(author, slug).asyncF {
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
        .leftMap(Redirect(self.showList(author, slug)).withErrors(_))
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
        .leftMap(Redirect(self.showList(author, slug)).withErrors(_))
        .map(_ => Redirect(self.showList(author, slug)))
    }

  /**
    * Irreversibly deletes the specified channel.
    *
    * @param author      Project owner
    * @param slug        Project slug
    * @param channelName Channel name
    * @return View of channels
    */
  def delete(author: String, slug: String, channelName: String): Action[AnyContent] =
    ChannelEditAction(author, slug).asyncEitherT { implicit request =>
      val channelsAccess = request.project.channels(ModelView.later(Channel))

      val ourChannel = channelsAccess.find(_.name === channelName)
      val ourChannelVersions = for {
        channel <- ourChannel
        version <- TableQuery[VersionTable] if version.channelId === channel.id
      } yield version

      val moreThanOneChannelR = channelsAccess.size =!= 1
      val isChannelEmptyR     = ourChannelVersions.size === 0
      val nonEmptyChannelsR = channelsAccess.query
        .map(channel => TableQuery[VersionTable].filter(_.channelId === channel.id).length =!= 0)
        .filter(identity)
        .length
      val reviewedChannelsCount = channelsAccess.count(!_.isNonReviewed) > 1

      val query = for {
        channel <- ourChannel
      } yield
        (
          channel,
          moreThanOneChannelR,
          isChannelEmptyR || nonEmptyChannelsR > 1,
          channel.isNonReviewed || reviewedChannelsCount
        )

      OptionT(service.runDBIO(query.result.headOption))
        .toRight(NotFound)
        .subflatMap {
          case (channel, notLast, notLastNonEmpty, notLastReviewed) =>
            val errorSeq = Seq(
              notLast         -> "error.channel.last",
              notLastNonEmpty -> "error.channel.lastNonEmpty",
              notLastReviewed -> "error.channel.lastReviewed"
            ).collect {
              case (success, msg) if !success => msg
            }

            if (errorSeq.isEmpty)
              Right(channel)
            else
              Left(Redirect(self.showList(author, slug)).withErrors(errorSeq.toList))
        }
        .semiflatMap(channel => projects.deleteChannel(request.project, channel))
        .as(Redirect(self.showList(author, slug)))
    }
}
