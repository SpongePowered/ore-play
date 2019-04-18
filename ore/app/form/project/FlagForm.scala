package form.project

import ore.models.project.FlagReason

case class FlagForm(reasonId: Int, comment: String) {

  val reason: FlagReason = FlagReason.values.find(_.value == reasonId).getOrElse(FlagReason.Other)

}
