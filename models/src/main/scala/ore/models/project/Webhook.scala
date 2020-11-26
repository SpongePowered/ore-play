package ore.models.project

import java.util.UUID

import ore.db.{DbRef, ModelQuery}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.WebhookTable

import enumeratum.values.{StringEnum, StringEnumEntry}
import slick.lifted.TableQuery

case class Webhook(
    projectId: DbRef[Project],
    publicId: UUID,
    name: String,
    callbackUrl: String,
    discordFormatted: Boolean,
    events: List[Webhook.WebhookEventType],
    secret: String,
    lastError: Option[String]
)
object Webhook extends DefaultModelCompanion[Webhook, WebhookTable](TableQuery[WebhookTable]) {

  implicit val query: ModelQuery[Webhook] = ModelQuery.from(this)

  sealed abstract class WebhookEventType(val value: String) extends StringEnumEntry
  object WebhookEventType extends StringEnum[WebhookEventType] {
    override def values: IndexedSeq[WebhookEventType] = findValues

    case object Ping                    extends WebhookEventType("ping")
    case object VersionCreated          extends WebhookEventType("version_created")
    case object VersionChangelogEdited  extends WebhookEventType("version_changelog_edited")
    case object VersionEdited           extends WebhookEventType("version_edited")
    case object VersionVisibilityChange extends WebhookEventType("version_visibility_change")
    case object VersionDeleted          extends WebhookEventType("version_deleted")
    case object PageCreated             extends WebhookEventType("page_created")
    case object PageUpdated             extends WebhookEventType("page_updated")
    case object PageDeleted             extends WebhookEventType("page_deleted")
    case object MemberAdded             extends WebhookEventType("member_added")
    case object MemberChanged           extends WebhookEventType("member_changed")
    case object MemberRemoved           extends WebhookEventType("member_removed")
  }
}
