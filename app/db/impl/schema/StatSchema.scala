package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.{ModelFilter, ModelSchema, ModelService}
import models.statistic.StatEntry

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

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
  def record(entry: M): Future[Boolean] = {
    val promise = Promise[Boolean]
    this.like(entry).andThen {
      case result => result.get match {
        case None =>
          // No previous entry found, insert new entry
          promise.completeWith(this.service.insert(entry).map(_.isDefined))
        case Some(existingEntry) =>
          // Existing entry found, update the User ID if possible
          if (existingEntry.user.isEmpty && entry.user.isDefined) {
            existingEntry.user = entry.user.get
          }
          promise.success(false)
      }
    }
    promise.future
  }

  override def like(entry: M): Future[Option[M]] = {
    val baseFilter: ModelFilter[M] = ModelFilter[M](_.modelId === entry.modelId)
    val filter: M#T => Rep[Boolean] = e => e.address === entry.address || e.cookie === entry.cookie
    val userFilter = entry.user.map(_.map[M#T => Rep[Boolean]](u => e => filter(e) || e.userId === u.id.get).getOrElse(filter))
    this.service.find(this.modelClass, (baseFilter && userFilter).fn)
  }

}
