package controllers.sugar

import ore.OreConfig

final class Bakery(config: OreConfig) {

  def bake(
      name: String,
      value: String,
      maxAge: Option[Int] = None,
      secure: Boolean = this.config.ore.session.secure
  ) = play.api.mvc.Cookie(name, value, maxAge, secure = secure)

}
