package mail

import javax.inject.Inject

import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.Flash

import controllers.AssetsFinder
import controllers.sugar.Requests.OreRequest
import ore.OreConfig
import ore.models.user.User
import util.syntax._

final class EmailFactory @Inject()(
    val messagesApi: MessagesApi,
    assetsFinder: AssetsFinder
)(implicit config: OreConfig)
    extends I18nSupport {

  val AccountUnlocked = "email.accountUnlock"

  def create(user: User, id: String)(implicit request: OreRequest[_]): Email = {
    implicit val lang: Lang = user.langOrDefault

    implicit def flash: Flash         = request.flash
    implicit def assets: AssetsFinder = assetsFinder

    Email(
      recipient = user.email.get,
      subject = this.messagesApi(s"$id.subject"),
      content = views.html.utils.email(
        title = s"$id.subject",
        recipient = user.name,
        body = s"$id.body"
      )
    )
  }

}
