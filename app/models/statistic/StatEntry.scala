package models.statistic

import java.sql.Timestamp

import com.github.tminglei.slickpg.InetString
import com.google.common.base.Preconditions._
import db.Model
import db.impl.model.OreModel
import db.impl.table.ModelKeys._
import db.impl.table.StatTable
import models.user.User

import scala.concurrent.Future

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
abstract class StatEntry[Subject <: Model](override val id: Option[Int] = None,
                                           override val createdAt: Option[Timestamp] = None,
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
  def user: Future[Option[User]] = {
    this._userId match {
      case None => Future.successful(None)
      case Some(id) => this.userBase.get(id)
    }
  }

  def userId = _userId

  /**
    * Sets the User associated with this entry, if any.
    *
    * @param user User of entry
    */
  def setUser(user: User) = {
    checkNotNull(user, "user is null", "")
    checkArgument(user.isDefined, "undefined user", "")
    this._userId = user.id
    if (isDefined) update(UserId)
  }

  def setUserId(userId: Int) = {
    this._userId = Some(userId)
    if (isDefined) update(UserId)
  }

}
