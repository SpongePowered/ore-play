package controllers.project

import javax.inject.Inject

import controllers.BaseController
import controllers.sugar.Bakery
import db.ModelService
import form.OreForms
import ore.permission.EditChannels
import ore.project.factory.ProjectFactory
import ore.{OreConfig, OreEnv}
import play.api.i18n.MessagesApi
import security.spauth.SingleSignOnConsumer
import views.html.projects.{channels => views}

/**
  * Controller for handling Channel related actions.
  */
class Channels @Inject()(forms: OreForms,
                         factory: ProjectFactory,
                         implicit override val bakery: Bakery,
                         implicit override val sso: SingleSignOnConsumer,
                         implicit override val messagesApi: MessagesApi,
                         implicit override val env: OreEnv,
                         implicit override val config: OreConfig,
                         implicit override val service: ModelService)
                         extends BaseController {

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
  def showList(author: String, slug: String) = ChannelEditAction(author, slug) { implicit request =>
    val project = request.project
    Ok(views.list(project, project.channels.toSeq))
  }

  /**
    * Creates a submitted channel for the specified Project.
    *
    * @param author Project owner
    * @param slug   Project slug
    * @return Redirect to view of channels
    */
  def create(author: String, slug: String) = ChannelEditAction(author, slug) { implicit request =>
    this.forms.ChannelEdit.bindFromRequest.fold(
      hasErrors => Redirect(self.showList(author, slug)).withError(hasErrors.errors.head.message),
      channelData => {
        channelData.addTo(request.project).fold(
          error => Redirect(self.showList(author, slug)).withError(error),
          _ => Redirect(self.showList(author, slug))
        )
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
  def save(author: String, slug: String, channelName: String) = ChannelEditAction(author, slug) { implicit request =>
    implicit val project = request.project
    this.forms.ChannelEdit.bindFromRequest.fold(
      hasErrors =>
        Redirect(self.showList(author, slug)).withError(hasErrors.errors.head.message),
      channelData => {
        channelData.saveTo(channelName).map { error =>
          Redirect(self.showList(author, slug)).withError(error)
        } getOrElse {
          Redirect(self.showList(author, slug))
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
  def delete(author: String, slug: String, channelName: String) = ChannelEditAction(author, slug) { implicit request =>
    implicit val project = request.project
    val channels = project.channels.all
    if (channels.size == 1) {
      Redirect(self.showList(author, slug)).withError("error.channel.last")
    } else {
      channels.find(c => c.name.equals(channelName)) match {
        case None =>
          NotFound
        case Some(channel) =>
          if (channel.versions.nonEmpty && channels.count(c => c.versions.nonEmpty) == 1) {
            Redirect(self.showList(author, slug)).withError("error.channel.lastNonEmpty")
          } else {
            this.projects.deleteChannel(channel)
            Redirect(self.showList(author, slug))
          }
      }
    }
  }

}
