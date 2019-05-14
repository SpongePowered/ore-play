package form.project

import ore.models.project.Channel
import ore.db.Model

import cats.data.OptionT
import cats.effect.IO

case class VersionDeployForm(
    apiKey: String,
    channel: OptionT[IO, Model[Channel]],
    recommended: Boolean,
    createForumPost: Boolean,
    changelog: Option[String]
)
