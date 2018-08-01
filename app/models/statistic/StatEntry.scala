package models.statistic

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._

import db.Model
import db.impl.table.StatTable
import util.instances.future._
import util.functional.OptionT
import models.user.User
import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase

/**
  * Represents a statistic entry in a StatTable.
  */
abstract class StatEntry[Subject <: Model] extends Model { self =>

  override type M <: StatEntry[Subject] { type M = self.M }
  override type T <: StatTable[M]

  /**
    * ID of model the stat is on
    */
  def modelId: Int

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
  def userId: Option[Int]

  checkNotNull(address, "client address cannot be null", "")
  checkNotNull(cookie, "browser cookie cannot be null", "")

  /**
    * Returns the User associated with this entry, if any.
    *
    * @return User of entry
    */
  def user(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] = {
    OptionT.fromOption[Future](userId).flatMap(userBase.get)
  }
}
