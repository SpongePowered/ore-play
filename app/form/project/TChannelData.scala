package form.project

import models.project.{Channel, Project}
import ore.Colors.Color
import ore.OreConfig
import ore.project.factory.ProjectFactory
import util.functional.{EitherT, OptionT}
import util.instances.future._
import util.syntax._
import util.StringUtils._
import scala.concurrent.{ExecutionContext, Future}

import db.ModelService

/**
  * Represents submitted [[Channel]] data.
  */
trait TChannelData {

  val config: OreConfig
  val factory: ProjectFactory

  /** The [[Channel]] [[Color]] **/
  val color: Color = Channel.Colors.find(_.hex.equalsIgnoreCase(channelColorHex)).get

  /** Channel name **/
  def channelName: String

  /** Channel color hex **/
  protected def channelColorHex: String

  val nonReviewed: Boolean

  /**
    * Attempts to add this ChannelData as a [[Channel]] to the specified
    * [[Project]].
    *
    * @param project  Project to add Channel to
    * @return         Either the new channel or an error message
    */
  def addTo(project: Project)(implicit ec: ExecutionContext, service: ModelService): EitherT[Future, String, Channel] = {
    EitherT.liftF(project.channels.all)
      .filterOrElse(_.size <= config.projects.get[Int]("max-channels"), "A project may only have up to five channels.")
      .filterOrElse(_.forall(ch => !ch.name.equalsIgnoreCase(this.channelName)), "A channel with that name already exists.")
      .filterOrElse(_.forall(_.color != this.color), "A channel with that color already exists.")
      .semiFlatMap(_ => this.factory.createChannel(project, this.channelName, this.color, this.nonReviewed))
  }

  /**
    * Attempts to save this ChannelData to the specified [[Channel]] name in
    * the specified [[Project]].
    *
    * @param oldName  Channel name to save to
    * @param project  Project of channel
    * @return         Error, if any
    */
  //TODO: Return NEL[String] if we get the type
  def saveTo(oldName: String)(implicit project: Project, ec: ExecutionContext, service: ModelService): EitherT[Future, List[String], Unit] = {
    EitherT.liftF(project.channels.all).flatMap { allChannels =>
      val (channelChangeSet, channels) = allChannels.partition(_.name.equalsIgnoreCase(oldName))
      val channel = channelChangeSet.toSeq.head
      //TODO: Rewrite this nicer if we ever get a Validated/Validation type
      val e1 = if(channels.exists(_.color == this.color)) List("error.channel.duplicateColor") else Nil
      val e2 = if(channels.exists(_.name.equalsIgnoreCase(this.channelName))) List("error.channel.duplicateName") else Nil
      val e3 = if(nonReviewed && channels.count(_.isReviewed) < 1) List("error.channel.minOneReviewed") else Nil
      val errors = e1 ::: e2 ::: e3

      if(errors.nonEmpty) {
        EitherT.leftT[Future, Unit](errors)
      }
      else {
        val effect = service.update(
          channel.copy(
            name = channelName,
            color = color,
            isNonReviewed = nonReviewed
          )
        )

        EitherT.right[List[String]](effect).map(_ => ())
      }
    }
  }

}
