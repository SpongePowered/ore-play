package form.project

import ore.OreConfig
import ore.models.project.factory.ProjectFactory

/**
  * Concrete counterpart to [[TChannelData]].
  *
  * @param channelName     Channel name
  * @param channelColorHex Channel color hex code
  */
case class ChannelData(
    channelName: String,
    protected val channelColorHex: String,
    nonReviewed: Boolean
)(implicit val config: OreConfig, val factory: ProjectFactory)
    extends TChannelData
