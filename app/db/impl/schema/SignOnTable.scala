package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.user.SignOn

class SignOnTable(tag: RowTag) extends ModelTable[SignOn](tag, "user_sign_ons") {

  def nonce       = column[String]("nonce")
  def isCompleted = column[Boolean]("is_completed")

  def * = mkProj((id.?, createdAt.?, nonce, isCompleted))(mkTuple[SignOn]())
}
