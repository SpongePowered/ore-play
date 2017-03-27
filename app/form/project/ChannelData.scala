package form.project

import ore.OreConfig
import ore.project.factory.ProjectFactory

/**
  * Concrete counterpart to [[TChannelData]].
  *
  * @param channelName     Channel name
  * @param channelColorHex Channel color hex code
  */
case class ChannelData(override val channelName: String,
                       override protected val channelColorHex: String,
                       override val nonReviewed: Boolean)
                      (implicit override val config: OreConfig,
                       override val factory: ProjectFactory)
                       extends TChannelData
