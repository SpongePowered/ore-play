package db.impl.table

import db.impl.OrePostgresDriver.api._
import models.statistic.StatEntry
import models.user.User
import ore.db.DbRef

import com.github.tminglei.slickpg.InetString

/**
  * Represents a table that represents statistics on a Model.
  *
  * @param tag          Table tag
  * @param name         Table name
  * @param modelIdName  Column name of model ID field
  */
abstract class StatTable[S, M <: StatEntry[S]](tag: Tag, name: String, modelIdName: String)
    extends ModelTable[M](tag, name) {

  /** The model ID of the statistic subject */
  def modelId = column[DbRef[S]](modelIdName)

  /** Client address */
  def address = column[InetString]("address")

  /** Unique browser cookie */
  def cookie = column[String]("cookie")

  /** User ID if applicable */
  def userId = column[DbRef[User]]("user_id")

}
