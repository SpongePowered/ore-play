package form.project

import db.Model
import models.project.Channel

import cats.data.OptionT
import cats.effect.IO

case class VersionDeployForm(
    apiKey: String,
    channel: OptionT[IO, Model[Channel]],
    recommended: Boolean,
    createForumPost: Boolean,
    changelog: Option[String]
)
