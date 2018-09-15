package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.{ModelFilter, ModelSchema, ModelService}
import models.statistic.StatEntry
import scala.concurrent.{ExecutionContext, Future, Promise}

import util.functional.OptionT
import util.instances.future._

/**
  * Records and determines uniqueness of StatEntries in a StatTable.
  *
  * @tparam M             Model type
  */
trait StatSchema[M <: StatEntry[_]] extends ModelSchema[M] {

  val service: ModelService
  val modelClass: Class[M]

  /**
    * Checks if the specified StatEntry exists and records the entry in the
    * database by either inserting a new entry or updating an existing entry
    * with the User ID if applicable. Returns true if recorded in database.
    *
    * @param entry  Entry to check
    * @return       True if recorded in database
    */
  def record(entry: M)(implicit ec: ExecutionContext): Future[Boolean] = {
    val promise = Promise[Boolean]
    this.like(entry).value.andThen {
      case result => result.get match {
        case None =>
          // No previous entry found, insert new entry
          promise.completeWith(this.service.insert(entry).map(_.isDefined))
        case Some(existingEntry) =>
          // Existing entry found, update the User ID if possible
          if (existingEntry.userId.isEmpty && entry.userId.isDefined) {
            existingEntry.setUserId(entry.userId.get)
          }
          promise.success(false)
      }
    }
    promise.future
  }

  override def like(entry: M)(implicit ec: ExecutionContext): OptionT[Future, M] = {
    val baseFilter: ModelFilter[M] = ModelFilter[M](_.modelId === entry.modelId)
    val filter: M#T => Rep[Boolean] = e => e.address === entry.address || e.cookie === entry.cookie
    val userFilter = entry.user.map[M#T => Rep[Boolean]](u => e => filter(e) || e.userId === u.id.value).getOrElse(filter)
    OptionT.liftF(userFilter).flatMap { uFilter =>
      this.service.find(this.modelClass, (baseFilter && uFilter).fn)
    }
  }

}
