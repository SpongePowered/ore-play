package form.project

import ore.models.project.Channel
import ore.db.Model

import cats.data.OptionT
import zio.UIO

case class VersionDeployForm(
    apiKey: String,
    channel: OptionT[UIO, Model[Channel]],
    recommended: Boolean,
    createForumPost: Boolean,
    changelog: Option[String]
)
