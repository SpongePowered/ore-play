package form.project

import scala.concurrent.{ExecutionContext, Future}

import db.ModelService
import models.project.{Channel, Project}
import ore.{Color, OreConfig}
import ore.project.factory.ProjectFactory

import cats.data.{EitherT, NonEmptyList => NEL}
import cats.instances.future._

/**
  * Represents submitted [[Channel]] data.
  */
//TODO: Return Use Validated for the values in here
trait TChannelData {

  def config: OreConfig
  def factory: ProjectFactory

  /** The [[Channel]] [[Color]] **/
  val color: Color = Channel.Colors.find(_.hex.equalsIgnoreCase(channelColorHex)).get

  /** Channel name **/
  def channelName: String

  /** Channel color hex **/
  protected def channelColorHex: String

  def nonReviewed: Boolean

  /**
    * Attempts to add this ChannelData as a [[Channel]] to the specified
    * [[Project]].
    *
    * @param project  Project to add Channel to
    * @return         Either the new channel or an error message
    */
  def addTo(
      project: Project
  )(implicit ec: ExecutionContext, service: ModelService): EitherT[Future, String, Channel] = {
    EitherT
      .liftF(project.channels.all)
      .ensure("A project may only have up to five channels.")(_.size <= config.projects.get[Int]("max-channels"))
      .ensure("A channel with that name already exists.")(_.forall(ch => !ch.name.equalsIgnoreCase(this.channelName)))
      .ensure("A channel with that color already exists.")(_.forall(_.color != this.color))
      .semiflatMap(_ => this.factory.createChannel(project, this.channelName, this.color))
  }

  /**
    * Attempts to save this ChannelData to the specified [[Channel]] name in
    * the specified [[Project]].
    *
    * @param oldName  Channel name to save to
    * @param project  Project of channel
    * @return         Error, if any
    */
  def saveTo(
      project: Project,
      oldName: String
  )(implicit ec: ExecutionContext, service: ModelService): EitherT[Future, NEL[String], Unit] = {
    EitherT.liftF(project.channels.all).flatMap { allChannels =>
      val (channelChangeSet, channels) = allChannels.partition(_.name.equalsIgnoreCase(oldName))
      val channel                      = channelChangeSet.toSeq.head
      //TODO: Rewrite this nicer if we ever get a Validated/Validation type
      val e1 = if (channels.exists(_.color == this.color)) List("error.channel.duplicateColor") else Nil
      val e2 =
        if (channels.exists(_.name.equalsIgnoreCase(this.channelName))) List("error.channel.duplicateName") else Nil
      val e3 = if (nonReviewed && channels.count(_.isReviewed) < 1) List("error.channel.minOneReviewed") else Nil

      NEL.fromList(e1 ::: e2 ::: e3) match {
        case Some(errors) => EitherT.leftT[Future, Unit](errors)
        case None =>
          val effect = service.update(
            channel.copy(
              name = channelName,
              color = color,
              isNonReviewed = nonReviewed
            )
          )

          //TODO: Replace this with void once IntelliJ understands it
          EitherT.right[NEL[String]](effect).map(_ => ())
      }
    }
  }

}
