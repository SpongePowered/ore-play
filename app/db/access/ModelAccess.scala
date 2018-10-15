package db.access

import scala.concurrent.{ExecutionContext, Future}

import db.ModelFilter.IdFilter
import db.impl.OrePostgresDriver.api._
import db.{Model, ModelFilter, ModelQuery, ModelService, ObjectReference}

import cats.data.OptionT
import slick.lifted.ColumnOrdered

/**
  * Provides simple, synchronous, access to a ModelTable.
  */
class ModelAccess[M <: Model: ModelQuery](
    val service: ModelService,
    val baseFilter: ModelFilter[M] = ModelFilter[M]()
) {

  /**
    * Returns the model with the specified ID.
    *
    * @param id   ID to lookup
    * @return     Model with ID or None if not found
    */
  def get(id: ObjectReference)(implicit ec: ExecutionContext): OptionT[Future, M] =
    this.service.get[M](id, this.baseFilter.fn)

  /**
    * Returns a set of Models that have an ID that is in the specified Int set.
    *
    * @param ids  ID set
    * @return     Models in ID set
    */
  def in(ids: Set[ObjectReference])(implicit ec: ExecutionContext): Future[Set[M]] =
    this.service.in[M](ids, this.baseFilter.fn).map(_.toSet)

  /**
    * Returns all the [[Model]]s in the set.
    *
    * @return All models in set
    */
  def all(implicit ec: ExecutionContext): Future[Set[M]] =
    this.service.filter[M](this.baseFilter.fn).map(_.toSet)

  /**
    * Returns the size of this set.
    *
    * @return Size of set
    */
  def size: Future[Int] = this.service.count[M](this.baseFilter.fn)

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
  def contains(model: M)(implicit ec: ExecutionContext): Future[Boolean] =
    this.service.count[M]((this.baseFilter +&& IdFilter(model.id.value)).fn).map(_ > 0)

  /**
    * Returns true if any models match the specified filter.
    *
    * @param filter Filter to use
    * @return       True if any model matches
    */
  def exists(filter: M#T => Rep[Boolean])(implicit ec: ExecutionContext): Future[Boolean] =
    this.service.count[M]((this.baseFilter && filter).fn).map(_ > 0)

  /**
    * Adds a new model to it's table.
    *
    * @param model Model to add
    * @return New model
    */
  def add(model: M)(implicit ec: ExecutionContext): Future[M] = {
    identity(ec)
    this.service.insert(model)
  }

  /**
    * Updates an existing model.
    *
    * @param model The model to update
    * @return The updated model
    */
  def update(model: M)(implicit ec: ExecutionContext): Future[M] = this.service.update(model)

  /**
    * Removes the specified model from this set if it is contained.
    *
    * @param model Model to remove
    */
  def remove(model: M): Future[Int] = this.service.delete(model)

  /**
    * Removes all the models from this set matching the given filter.
    *
    * @param filter Filter to use
    */
  def removeAll(filter: M#T => Rep[Boolean] = _ => true): Future[Int] =
    this.service.deleteWhere[M]((this.baseFilter && filter).fn)

  /**
    * Returns the first model matching the specified filter.
    *
    * @param filter Filter to use
    * @return       Model matching filter, if any
    */
  def find(filter: M#T => Rep[Boolean])(implicit ec: ExecutionContext): OptionT[Future, M] =
    this.service.find[M]((this.baseFilter && filter).fn)

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
      ordering: M#T => ColumnOrdered[_],
      filter: M#T => Rep[Boolean] = null,
      limit: Int = -1,
      offset: Int = -1
  ): Future[Seq[M]] = this.service.sorted[M](ordering, (this.baseFilter && filter).fn, limit, offset)

  /**
    * Filters this set by the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filter(filter: M#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Future[Seq[M]] =
    this.service.filter[M]((this.baseFilter && filter).fn, limit, offset)

  /**
    * Filters this set by the opposite of the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filterNot(filter: M#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Future[Seq[M]] =
    this.filter(!filter(_), limit, offset)

  /**
    * Counts how many elements in this set fulfill some predicate.
    * @param predicate The predicate to use
    * @return The amount of elements that fulfill the predicate.
    */
  def count(predicate: M#T => Rep[Boolean]): Future[Int] =
    this.service.count((this.baseFilter && predicate).fn)

  /**
    * Returns a Seq of this set.
    *
    * @return Seq of set
    */
  def toSeq(implicit ec: ExecutionContext): Future[Seq[M]] = this.all.map(_.toSeq)

}
