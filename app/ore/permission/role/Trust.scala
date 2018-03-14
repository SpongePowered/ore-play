package ore.permission.role

/**
  * Represents a level of trust within the application.
  */
sealed trait Trust extends Ordered[Trust] {
  def level: Int
  override def compare(that: Trust) = this.level - that.level
}

/**
  * User has the default level of trust granted by signing up.
  */
case object Default extends Trust { override val level = 0 }

/**
  * User has a limited amount of trust and may perform certain actions not
  * afforded to regular [[models.user.User]]s.
  */
case object Limited extends Trust { override val level = 1 }

/**
  * User has a standard amount of trust and may perform moderator-like actions
  * within the site.
  */
case object Standard extends Trust { override val level = 2 }

/**
  * User that can perform any action but they are not on top.
  */
case object Lifted extends Trust { override val level = 3 }

/**
  * User is absolutely trusted and may perform any action.
  */
case object Absolute extends Trust { override val level = 4 }
