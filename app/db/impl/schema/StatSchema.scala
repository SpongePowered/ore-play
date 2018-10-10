package db.impl.schema

import scala.concurrent.{ExecutionContext, Future, Promise}

import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.{ModelFilter, ModelSchema, ModelService, ObjectReference}
import models.statistic.StatEntry

import cats.data.OptionT
import cats.instances.future._

/**
  * Records and determines uniqueness of StatEntries in a StatTable.
  *
  * @tparam M             Model type
  */
trait StatSchema[M <: StatEntry[_]] extends ModelSchema[M] {

  implicit def service: ModelService
  def modelClass: Class[M]

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
      case result =>
        result.get match {
          case None =>
            // No previous entry found, insert new entry
            promise.completeWith(this.service.insert(entry).map(_.isDefined))
          case Some(existingEntry) =>
            // Existing entry found, update the User ID if possible
            if (existingEntry.userId.isEmpty && entry.userId.isDefined) {
              service.update(setUserId(existingEntry, entry.userId.get))
            }
            promise.success(false)
        }
    }
    promise.future
  }

  def setUserId(m: M, id: ObjectReference): M

  override def like(entry: M)(implicit ec: ExecutionContext): OptionT[Future, M] = {
    val baseFilter: ModelFilter[M]  = ModelFilter[M](_.modelId === entry.modelId)
    val filter: M#T => Rep[Boolean] = e => e.address === entry.address || e.cookie === entry.cookie
    val userFilter = entry
      .user(ec, UserBase.fromService(service))
      .map[M#T => Rep[Boolean]](u => e => filter(e) || e.userId === u.id.value)
      .getOrElse(filter)
    OptionT.liftF(userFilter).flatMap { uFilter =>
      this.service.find(this.modelClass, (baseFilter && uFilter).fn)
    }
  }

}
