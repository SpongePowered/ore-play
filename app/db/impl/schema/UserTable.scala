package db.impl.schema
import java.sql.Timestamp

import play.api.i18n.Lang

import db.{DbModel, DbRef, ObjId, ObjTimestamp}
import db.impl.OrePostgresDriver.api._
import db.impl.table.common.NameColumn
import db.table.ModelTable
import models.user.User
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

  override def * = {
    val applyFunc: (
        (
            Option[DbRef[User]],
            Option[Timestamp],
            Option[String],
            String,
            Option[String],
            Option[String],
            Option[Timestamp],
            List[Prompt],
            Option[String],
            Option[Timestamp],
            Boolean,
            Option[Lang]
        )
    ) => DbModel[User] = {
      case (id, time, fullName, name, email, tagline, joinDate, prompts, pgpKey, keyLastUpdate, locked, lang) =>
        DbModel(
          ObjId.unsafeFromOption(id),
          ObjTimestamp.unsafeFromOption(time),
          User(
            ObjId.unsafeFromOption(id),
            fullName,
            name,
            email,
            tagline,
            joinDate,
            prompts,
            pgpKey,
            keyLastUpdate,
            locked,
            lang
          )
        )
    }

    val unapplyFunc: DbModel[User] => Option[
      (
          Option[DbRef[User]],
          Option[Timestamp],
          Option[String],
          String,
          Option[String],
          Option[String],
          Option[Timestamp],
          List[Prompt],
          Option[String],
          Option[Timestamp],
          Boolean,
          Option[Lang]
      )
    ] = {
      case DbModel(
          _,
          createdAt,
          User(
            id,
            fullName,
            name,
            email,
            tagline,
            joinDate,
            readPrompts,
            pgpPubKey,
            lastPgpPubKeyUpdate,
            isLocked,
            lang
          )
          ) =>
        Option(
          (
            id.unsafeToOption,
            createdAt.unsafeToOption,
            fullName,
            name,
            email,
            tagline,
            joinDate,
            readPrompts,
            pgpPubKey,
            lastPgpPubKeyUpdate,
            isLocked,
            lang
          )
        )
    }

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
    ) <> (applyFunc, unapplyFunc)
  }
}
