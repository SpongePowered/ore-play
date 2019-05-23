package form.project

import scala.language.higherKinds

import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.ChannelTable
import ore.models.project.{Channel, Project}
import ore.db.access.ModelView
import ore.db.{Model, ModelService}
import ore.models.project.factory.ProjectFactory
import ore.OreConfig
import ore.data.Color
import ore.util.StringUtils._

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.syntax.all._

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
  def addTo[F[_]](
      project: Model[Project]
  )(implicit service: ModelService[F], F: cats.effect.Effect[F]): EitherT[F, List[String], Model[Channel]] = {
    val dbChannels = project.channels(ModelView.later(Channel))
    val conditions = (
      dbChannels.size <= config.ore.projects.maxChannels,
      dbChannels.forall(!equalsIgnoreCase[ChannelTable](_.name, this.channelName)(_)),
      dbChannels.forall(_.color =!= this.color)
    )

    EitherT.liftF(service.runDBIO(conditions.result)).flatMap {
      case (underMaxSize, uniqueName, uniqueColor) =>
        val errors = List(
          underMaxSize -> "A project may only have up to five channels.",
          uniqueName   -> "error.channel.duplicateName",
          uniqueColor  -> "error.channel.duplicateColor"
        ).collect {
          case (success, error) if !success => error
        }

        if (errors.nonEmpty) EitherT.leftT[F, Model[Channel]](errors)
        else EitherT.right[List[String]](factory.createChannel(project, channelName, color).to[F])
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
  def saveTo[F[_]](
      project: Model[Project],
      oldName: String
  )(implicit service: ModelService[F], F: Monad[F]): EitherT[F, List[String], Unit] = {
    val otherDbChannels = project.channels(ModelView.later(Channel)).filterView(_.name =!= oldName)
    val query = project.channels(ModelView.raw(Channel)).filter(_.name === oldName).map { channel =>
      (
        channel,
        otherDbChannels.forall(!equalsIgnoreCase[ChannelTable](_.name, this.channelName)(_)),
        otherDbChannels.forall(_.color =!= this.color),
        otherDbChannels.count(!_.isNonReviewed) < 1 && nonReviewed
      )
    }

    OptionT(service.runDBIO(query.result.headOption)).toRight(List("error.channel.nowFound")).flatMap {
      case (channel, uniqueName, uniqueColor, minOneReviewed) =>
        val errors = List(
          uniqueName     -> "error.channel.duplicateName",
          uniqueColor    -> "error.channel.duplicateColor",
          minOneReviewed -> "error.channel.minOneReviewed"
        ).collect {
          case (success, error) if !success => error
        }

        val effect = service.update(channel)(
          _.copy(
            name = channelName,
            color = color,
            isNonReviewed = nonReviewed
          )
        )

        if (errors.nonEmpty) EitherT.leftT[F, Unit](errors)
        else EitherT.right[List[String]](effect.void)
    }
  }

}
