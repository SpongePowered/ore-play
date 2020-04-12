package ore.models.user

import ore.db.{DbRef, ModelQuery}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.SecurityLogEventTable

import com.github.tminglei.slickpg.InetString
import enumeratum.values.{StringEnum, StringEnumEntry}
import io.circe.Json
import slick.lifted.TableQuery

case class SecurityLogEvent(
    userId: DbRef[User],
    ipAddress: InetString,
    userAgent: Option[String],
    location: Option[String],
    eventType: SecurityLogEvent.EventType,
    extraData: Option[Json]
)
object SecurityLogEvent
    extends DefaultModelCompanion[SecurityLogEvent, SecurityLogEventTable](TableQuery[SecurityLogEventTable]) {
  implicit val query: ModelQuery[SecurityLogEvent] = ModelQuery.from(this)

  sealed abstract class EventType(val value: String) extends StringEnumEntry
  object EventType extends StringEnum[EventType] {
    case object Login        extends EventType("login")
    case object CreateApiKey extends EventType("create_api_key")
    case object DeleteApiKey extends EventType("delete_api_key")

    override def values: IndexedSeq[EventType] = findValues
  }
}
