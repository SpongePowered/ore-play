package models.statistic

import db.access.ModelView
import db.impl.table.StatTable
import db.{DbRef, InsertFunc, Model, ModelService}
import models.user.User

import cats.data.OptionT
import cats.effect.IO
import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._

/**
  * Represents a statistic entry in a StatTable.
  */
abstract class StatEntry[Subject <: Model] extends Model { self =>

  override type M <: StatEntry[Subject] { type M = self.M }
  override type T <: StatTable[Subject, M]

  /**
    * ID of model the stat is on
    */
  def modelId: DbRef[Subject]

  /**
    * Client address
    */
  def address: InetString

  /**
    * Browser cookie
    */
  def cookie: String

  /**
    * User ID
    */
  def userId: Option[DbRef[User]]

  checkNotNull(address, "client address cannot be null", "")
  checkNotNull(cookie, "browser cookie cannot be null", "")
}

trait PartialStatEntry[Subject <: Model, M <: Model] {

  /**
    * ID of model the stat is on
    */
  def modelId: DbRef[Subject]

  /**
    * Client address
    */
  def address: InetString

  /**
    * Browser cookie
    */
  def cookie: String

  /**
    * User ID
    */
  def userId: Option[DbRef[User]]

  /**
    * Returns the User associated with this entry, if any.
    *
    * @return User of entry
    */
  def user(implicit service: ModelService): OptionT[IO, User] =
    OptionT.fromOption[IO](userId).flatMap(ModelView.now[User].get)

  def asFunc: InsertFunc[M]
}
