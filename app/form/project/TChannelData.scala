package form.project

import models.project.{Channel, Project}
import ore.Colors.Color
import ore.OreConfig
import ore.project.factory.ProjectFactory

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
  def addTo(project: Project): Either[String, Channel] = {
    val channels = project.channels.all
    if (channels.size >= config.projects.get[Int]("max-channels")) {
      Left("A project may only have up to five channels.")
    } else {
      channels.find(_.name.equalsIgnoreCase(this.channelName)) match {
        case Some(_) =>
          Left("A channel with that name already exists.")
        case None => channels.find(_.color.equals(this.color)) match {
          case Some(_) =>
            Left("A channel with that color already exists.")
          case None =>
            Right(this.factory.createChannel(project, this.channelName, this.color, this.nonReviewed))
        }
      }
    }
  }

  /**
    * Attempts to save this ChannelData to the specified [[Channel]] name in
    * the specified [[Project]].
    *
    * @param oldName  Channel name to save to
    * @param project  Project of channel
    * @return         Error, if any
    */
  def saveTo(oldName: String)(implicit project: Project): Option[String] = {
    val channels = project.channels.all
    val channel = channels.find(_.name.equalsIgnoreCase(oldName)).get
    val colorChan = channels.find(_.color.equals(this.color))
    val colorTaken = colorChan.isDefined && !colorChan.get.equals(channel)
    if (colorTaken) {
      Some("A channel with that color already exists.")
    } else {
      val nameChan = channels.find(_.name.equalsIgnoreCase(this.channelName))
      val nameTaken = nameChan.isDefined && !nameChan.get.equals(channel)
      if (nameTaken) {
        Some("A channel with that name already exists.")
      } else {
        val reviewedChannels = channels.filter(!_.isNonReviewed)
        if (this.nonReviewed && reviewedChannels.size <= 1 && reviewedChannels.contains(channel)) {
          Some("There must be at least one reviewed channel.")
        } else {
          channel.name = this.channelName
          channel.color = this.color
          channel.setNonReviewed(this.nonReviewed)
          None
        }
      }
    }
  }

}
