package form.project

import models.project.Channel

case class VersionDeployForm(apiKey: String, channel: Channel, recommended: Boolean, createForumPost: Boolean)
