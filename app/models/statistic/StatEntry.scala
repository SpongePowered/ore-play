package models.statistic

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._
import db.{Model, ObjectId, ObjectTimestamp}
import db.impl.model.OreModel
import db.impl.table.ModelKeys._
import db.impl.table.StatTable
import util.instances.future._
import util.functional.OptionT
import models.user.User

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a statistic entry in a StatTable.
  *
  * @param id         Unique ID of entry
  * @param createdAt  Timestamp instant of creation
  * @param modelId    ID of model the stat is on
  * @param address    Client address
  * @param cookie     Browser cookie
  * @param _userId     User ID
  */
abstract class StatEntry[Subject <: Model](override val id: ObjectId = ObjectId.Uninitialized,
                                           override val createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                                           val modelId: Int,
                                           val address: InetString,
                                           val cookie: String,
                                           private var _userId: Option[Int] = None)
                                           extends OreModel(id, createdAt) { self =>

  override type M <: StatEntry[Subject] { type M = self.M }
  override type T <: StatTable[M]

  checkNotNull(address, "client address cannot be null", "")
  checkNotNull(cookie, "browser cookie cannot be null", "")

  /**
    * Returns the User associated with this entry, if any.
    *
    * @return User of entry
    */
  def user(implicit ec: ExecutionContext): OptionT[Future, User] = {
    OptionT.fromOption[Future](this._userId).flatMap(this.userBase.get(_))
  }

  def userId: Option[Int] = _userId

  /**
    * Sets the User associated with this entry, if any.
    *
    * @param user User of entry
    */
  def setUser(user: User) = {
    checkNotNull(user, "user is null", "")
    checkArgument(user.isDefined, "undefined user", "")
    this._userId = Some(user.id.value)
    if (isDefined) update(UserId)
  }

  def setUserId(userId: Int) = {
    this._userId = Some(userId)
    if (isDefined) update(UserId)
  }

}
