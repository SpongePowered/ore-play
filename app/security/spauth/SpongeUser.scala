package security.spauth

import play.api.i18n.Lang

/**
  * Represents a Sponge user.
  *
  * @param id        Unique ID
  * @param username  Username
  * @param email     Email
  * @param avatarUrl Avatar url
  * @param lang      Language
  * @param addGroups Groups
  */
case class SpongeUser(id: Int, username: String, email: String, avatarUrl: Option[String], lang: Option[Lang], addGroups: Option[String])
