package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.user.{SecurityLogEvent, User}

import com.github.tminglei.slickpg.InetString
import io.circe.Json

class SecurityLogEventTable(tag: Tag) extends ModelTable[SecurityLogEvent](tag, "security_log_events") {
  def userId: Rep[DbRef[User]]                   = column[DbRef[User]]("userId")
  def ipAddress: Rep[InetString]                 = column[InetString]("ipAddress")
  def userAgent: Rep[Option[String]]             = column[Option[String]]("userAgent")
  def location: Rep[Option[String]]              = column[Option[String]]("location")
  def eventType: Rep[SecurityLogEvent.EventType] = column[SecurityLogEvent.EventType]("eventType")
  def extraData: Rep[Option[Json]]               = column[Option[Json]]("extraData")

  def * =
    (id.?, createdAt.?, (userId, ipAddress, userAgent, location, eventType, extraData)) <> (mkApply(
      (SecurityLogEvent.apply _).tupled
    ), mkUnapply(
      SecurityLogEvent.unapply
    ))
}
