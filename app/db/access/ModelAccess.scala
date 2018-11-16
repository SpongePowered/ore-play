package db.access

import scala.concurrent.{ExecutionContext, Future}

import db.ModelFilter._
import db.impl.OrePostgresDriver.api._
import db.{DbRef, Model, ModelQuery, ModelService}

import cats.data.OptionT
import slick.lifted.ColumnOrdered

/**
  * Provides simple, synchronous, access to a ModelTable.
  */
class ModelAccess[M0 <: Model { type M = M0 }: ModelQuery](
    val service: ModelService,
    val baseFilter: M0#T => Rep[Boolean]
) {

  /**
    * Returns the model with the specified ID.
    *
    * @param id   ID to lookup
    * @return     Model with ID or None if not found
    */
  def get(id: DbRef[M0])(implicit ec: ExecutionContext): OptionT[Future, M0] =
    this.service.get(id, this.baseFilter)

  /**
    * Returns a set of Models that have an ID that is in the specified Int set.
    *
    * @param ids  ID set
    * @return     Models in ID set
    */
  def in(ids: Set[DbRef[M0]])(implicit ec: ExecutionContext): Future[Set[M0]] =
    this.service.in(ids, this.baseFilter).map(_.toSet)

  /**
    * Returns all the [[Model]]s in the set.
    *
    * @return All models in set
    */
  def all(implicit ec: ExecutionContext): Future[Set[M0]] =
    this.service.filter(this.baseFilter).map(_.toSet)

  /**
    * Returns the size of this set.
    *
    * @return Size of set
    */
  def size: Future[Int] = this.service.count(this.baseFilter)

  /**
    * Returns true if this set is empty.
    *
    * @return True if set is empty
    */
  def isEmpty(implicit ec: ExecutionContext): Future[Boolean] = this.size.map(_ == 0)

  /**
    * Returns true if this set is not empty.
    *
    * @return True if not empty
    */
  def nonEmpty(implicit ec: ExecutionContext): Future[Boolean] = this.size.map(_ > 0)

  /**
    * Returns true if this set contains the specified model.
    *
    * @param model Model to look for
    * @return True if contained in set
    */
  def contains(model: M0)(implicit ec: ExecutionContext): Future[Boolean] =
    exists(IdFilter(model.id.value))

  /**
    * Returns true if any models match the specified filter.
    *
    * @param filter Filter to use
    * @return       True if any model matches
    */
  def exists(filter: M0#T => Rep[Boolean])(implicit ec: ExecutionContext): Future[Boolean] =
    this.service.count(this.baseFilter && filter).map(_ > 0)

  /**
    * Adds a new model to it's table.
    *
    * @param model Model to add
    * @return New model
    */
  def add(model: M0)(implicit ec: ExecutionContext): Future[M0] = {
    identity(ec)
    this.service.insert(model)
  }

  /**
    * Updates an existing model.
    *
    * @param model The model to update
    * @return The updated model
    */
  def update(model: M0)(implicit ec: ExecutionContext): Future[M0] = this.service.update(model)

  /**
    * Removes the specified model from this set if it is contained.
    *
    * @param model Model to remove
    */
  def remove(model: M0): Future[Int] = this.service.delete(model)

  /**
    * Removes all the models from this set matching the given filter.
    *
    * @param filter Filter to use
    */
  def removeAll(filter: M0#T => Rep[Boolean] = All): Future[Int] =
    this.service.deleteWhere(this.baseFilter && filter)

  /**
    * Returns the first model matching the specified filter.
    *
    * @param filter Filter to use
    * @return       Model matching filter, if any
    */
  def find(filter: M0#T => Rep[Boolean])(implicit ec: ExecutionContext): OptionT[Future, M0] =
    this.service.find(this.baseFilter && filter)

  /**
    * Returns a sorted Seq by the specified [[ColumnOrdered]].
    *
    * @param ordering Model ordering
    * @param filter   Filter to use
    * @param limit    Amount to take
    * @param offset   Amount to drop
    * @return         Sorted models
    */
  def sorted(
      ordering: M0#T => ColumnOrdered[_],
      filter: M0#T => Rep[Boolean] = All,
      limit: Int = -1,
      offset: Int = -1
  ): Future[Seq[M0]] = this.service.sorted[M0](ordering, this.baseFilter && filter, limit, offset)

  /**
    * Filters this set by the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filter(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Future[Seq[M0]] =
    this.service.filter(this.baseFilter && filter, limit, offset)

  /**
    * Filters this set by the opposite of the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filterNot(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Future[Seq[M0]] =
    this.filter(!filter(_), limit, offset)

  /**
    * Counts how many elements in this set fulfill some predicate.
    * @param predicate The predicate to use
    * @return The amount of elements that fulfill the predicate.
    */
  def count(predicate: M0#T => Rep[Boolean]): Future[Int] =
    this.service.count(this.baseFilter && predicate)

  /**
    * Returns a Seq of this set.
    *
    * @return Seq of set
    */
  def toSeq(implicit ec: ExecutionContext): Future[Seq[M0]] = this.all.map(_.toSeq)

}
