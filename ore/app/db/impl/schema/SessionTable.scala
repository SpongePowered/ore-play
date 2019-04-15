package db.impl.schema

import java.sql.Timestamp

import db.impl.OrePostgresDriver.api._
import models.user.{Session => DbSession}

class SessionTable(tag: Tag) extends ModelTable[DbSession](tag, "user_sessions") {

  def expiration = column[Timestamp]("expiration")
  def username   = column[String]("username")
  def token      = column[String]("token")

  def * =
    (id.?, createdAt.?, (expiration, username, token)) <> (mkApply((DbSession.apply _).tupled), mkUnapply(
      DbSession.unapply
    ))
}
