package form.project

import ore.OreConfig
import ore.project.factory.ProjectFactory

/**
  * Represents submitted [[models.project.Version]] data.
  *
  * @param channelName      Name of channel
  * @param channelColorHex  Channel color hex
  * @param recommended      True if recommended version
  */
case class VersionData(recommended: Boolean,
                       override val channelName: String,
                       override protected val channelColorHex: String,
                       override val nonReviewed: Boolean,
                       content: Option[String])
                      (implicit override val config: OreConfig,
                       override val factory: ProjectFactory)
                       extends TChannelData
