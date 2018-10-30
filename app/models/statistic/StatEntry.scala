package models.statistic

import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import db.impl.table.StatTable
import db.{Model, DbRef}
import models.user.User

import cats.data.OptionT
import cats.instances.future._
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

  /**
    * Returns the User associated with this entry, if any.
    *
    * @return User of entry
    */
  def user(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] =
    OptionT.fromOption[Future](userId).flatMap(userBase.get)
}
