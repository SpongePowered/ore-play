package db.impl.schema
import java.sql.Timestamp

import play.api.i18n.Lang

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import db.table.ModelTable
import models.user.User
import ore.permission.role.Role
import ore.user.Prompt

class UserTable(tag: Tag) extends ModelTable[User](tag, "users") with NameColumn[User] {

  // Override to remove auto increment
  override def id = column[DbRef[User]]("id", O.PrimaryKey)

  def fullName            = column[String]("full_name")
  def email               = column[String]("email")
  def pgpPubKey           = column[String]("pgp_pub_key")
  def lastPgpPubKeyUpdate = column[Timestamp]("last_pgp_pub_key_update")
  def isLocked            = column[Boolean]("is_locked")
  def tagline             = column[String]("tagline")
  def joinDate            = column[Timestamp]("join_date")
  def readPrompts         = column[List[Prompt]]("read_prompts")
  def lang                = column[Lang]("language")

  override def * =
    mkProj(
      (
        id.?,
        createdAt.?,
        fullName.?,
        name,
        email.?,
        tagline.?,
        joinDate.?,
        readPrompts,
        pgpPubKey.?,
        lastPgpPubKeyUpdate.?,
        isLocked,
        lang.?
      )
    )(mkTuple[User]())
}
