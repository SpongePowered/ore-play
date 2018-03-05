package form.project

import ore.project.FlagReasons
import ore.project.FlagReasons.FlagReason

case class FlagForm(reasonId: Int, comment: String) {

  val reason: FlagReason = FlagReasons.values.find(_.id == reasonId).getOrElse(FlagReasons.Other)

}
