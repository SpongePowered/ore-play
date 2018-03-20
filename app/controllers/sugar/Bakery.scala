package controllers.sugar

import javax.inject.Inject
import ore.OreConfig

final class Bakery @Inject()(config: OreConfig) {

  def bake(name: String, value: String, maxAge: Option[Int] = None,
           secure: Boolean = this.config.security.get[Boolean]("secure"))
  = play.api.mvc.Cookie(name, value, maxAge, secure = secure)

}
