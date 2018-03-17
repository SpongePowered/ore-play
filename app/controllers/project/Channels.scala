package controllers.project

import javax.inject.Inject

import controllers.OreBaseController
import controllers.sugar.Bakery
import db.ModelService
import form.OreForms
import models.viewhelper.ProjectData
import ore.permission.EditChannels
import ore.project.factory.ProjectFactory
import ore.{OreConfig, OreEnv}
import play.api.cache.AsyncCacheApi
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import views.html.projects.{channels => views}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Controller for handling Channel related actions.
  */
class Channels @Inject()(forms: OreForms,
                         factory: ProjectFactory,
                         implicit override val bakery: Bakery,
                         implicit override val cache: AsyncCacheApi,
                         implicit override val sso: SingleSignOnConsumer,
                         implicit override val messagesApi: MessagesApi,
                         implicit override val env: OreEnv,
                         implicit override val config: OreConfig,
                         implicit override val service: ModelService)
                         extends OreBaseController {

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
  def showList(author: String, slug: String) = ChannelEditAction(author, slug).async { request =>
    val project = request.project
    project.project.channels.toSeq.map { list =>
      implicit val r = request.request
      val listWithVersionCount = list.map(c => (c, 0)) // TODO count
      Ok(views.list(request.project, listWithVersionCount))
    }
  }

  /**
    * Creates a submitted channel for the specified Project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Redirect to view of channels
    */
  def create(author: String, slug: String) = ChannelEditAction(author, slug).async { implicit request =>
    this.forms.ChannelEdit.bindFromRequest.fold(
      hasErrors => Future.successful(Redirect(self.showList(author, slug)).withError(hasErrors.errors.head.message)),
      channelData => {
        channelData.addTo(request.project.project).map { _.fold(
            error => Redirect(self.showList(author, slug)).withError(error),
            _ => Redirect(self.showList(author, slug))
          )
        }
      }
    )
  }

  /**
    * Submits changes to an existing channel.
    *
    * @param author      Project owner
    * @param slug        Project slug
    * @param channelName Channel name
    * @return View of channels
    */
  def save(author: String, slug: String, channelName: String) = ChannelEditAction(author, slug).async { implicit request =>
    implicit val project = request.project
    this.forms.ChannelEdit.bindFromRequest.fold(
      hasErrors =>
        Future.successful(Redirect(self.showList(author, slug)).withError(hasErrors.errors.head.message)),
      channelData => {
        implicit val p = project.project
        channelData.saveTo(channelName).map { _.map { error =>
            Redirect(self.showList(author, slug)).withError(error)
          } getOrElse {
            Redirect(self.showList(author, slug))
          }
        }
      }
    )
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
  def delete(author: String, slug: String, channelName: String) = ChannelEditAction(author, slug).async { implicit request =>
    implicit val project = request.project
    project.project.channels.all.flatMap { channels =>
      if (channels.size == 1) {
        Future.successful(Redirect(self.showList(author, slug)).withError("error.channel.last"))
      } else {
        channels.find(c => c.name.equals(channelName)) match {
          case None => Future.successful(NotFound)
          case Some(channel) =>
            for {
              nonEmpty <- channel.versions.nonEmpty
              channelCount <- Future.sequence(channels.map(_.versions.nonEmpty)).map(l => l.count(_ == true))
            } yield {
              if (nonEmpty && channelCount == 1) {
                Redirect(self.showList(author, slug)).withError("error.channel.lastNonEmpty")
              } else {
                val reviewedChannels = channels.filter(!_.isNonReviewed)
                if (!channel.isNonReviewed && reviewedChannels.size <= 1 && reviewedChannels.contains(channel)) {
                  Redirect(self.showList(author, slug)).withError("error.channel.lastReviewed")
                } else {
                  this.projects.deleteChannel(channel)
                  Redirect(self.showList(author, slug))
                }
              }
            }

        }
      }
    }
  }
}
