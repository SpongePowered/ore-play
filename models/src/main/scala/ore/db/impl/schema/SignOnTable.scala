package ore.db.impl.schema

import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.SignOn

class SignOnTable(tag: Tag) extends ModelTable[SignOn](tag, "user_sign_ons") {

  def nonce       = column[String]("nonce")
  def isCompleted = column[Boolean]("is_completed")

  def * = (id.?, createdAt.?, (nonce, isCompleted)).<>(mkApply((SignOn.apply _).tupled), mkUnapply(SignOn.unapply))
}
