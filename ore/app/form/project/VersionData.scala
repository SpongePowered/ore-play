package form.project

import ore.OreConfig
import ore.models.project.factory.ProjectFactory

/**
  * Represents submitted [[ore.models.project.Version]] data.
  *
  * @param channelName      Name of channel
  * @param channelColorHex  Channel color hex
  * @param recommended      True if recommended version
  */
case class VersionData(
    unstable: Boolean,
    recommended: Boolean,
    channelName: String,
    protected val channelColorHex: String,
    nonReviewed: Boolean,
    content: Option[String],
    forumPost: Boolean
)(implicit val config: OreConfig, val factory: ProjectFactory)
    extends TChannelData
