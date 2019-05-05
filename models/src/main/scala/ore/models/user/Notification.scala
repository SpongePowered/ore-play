package ore.models.user

import scala.language.higherKinds

import ore.data.user.notification.NotificationType
import ore.db.access.ModelView
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.NotificationTable
import ore.db.{DbRef, Model, ModelQuery, ModelService}

import cats.MonadError
import cats.data.{OptionT, NonEmptyList => NEL}
import slick.lifted.TableQuery

/**
  * Represents a [[User]] notification.
  *
  * @param userId           ID of User this notification belongs to
  * @param notificationType Type of notification
  * @param messageArgs      The unlocalized message to display, with the
  *                         parameters to use when localizing
  * @param action           Action to perform on click
  * @param isRead             True if notification has been read
  */
case class Notification(
    userId: DbRef[User],
    originId: Option[DbRef[User]] = None,
    notificationType: NotificationType,
    messageArgs: NEL[String],
    action: Option[String] = None,
    isRead: Boolean = false
) {

  /**
    * Returns the [[User]] from which this Notification originated from.
    *
    * @return User from which this originated from
    */
  def origin[F[_]: ModelService](implicit F: MonadError[F, Throwable]): OptionT[F, Model[User]] = {
    OptionT
      .fromOption[F](originId)
      .semiflatMap { id =>
        ModelView.now(User).get(id).getOrElseF(F.raiseError(new NoSuchElementException("Get on None")))
      }
  }
}
object Notification extends DefaultModelCompanion[Notification, NotificationTable](TableQuery[NotificationTable]) {

  implicit val query: ModelQuery[Notification] =
    ModelQuery.from(this)

  implicit val isUserOwned: UserOwned[Notification] = (a: Notification) => a.userId
}
