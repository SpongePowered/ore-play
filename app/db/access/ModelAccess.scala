package db.access

import db.ModelFilter._
import db.impl.OrePostgresDriver.api._
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService}

import cats.data.OptionT
import cats.effect.IO
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
  def get(id: DbRef[M0]): OptionT[IO, M0] = service.getNow(id, baseFilter)

  /**
    * Returns a query set of Models that have an ID that is in the specified Int set.
    *
    * @param ids  ID set
    * @return     Models in ID set
    */
  def in(ids: Set[DbRef[M0]]): Query[M0#T, M0, Set] = service.in(ids, baseFilter).to[Set]

  /**
    * Returns a set of Models that have an ID that is in the specified Int set.
    *
    * @param ids  ID set
    * @return     Models in ID set
    */
  def inNow(ids: Set[DbRef[M0]]): IO[Set[M0]] = service.runDBIO(in(ids).result)

  /**
    * Returns the query equivalent of this access.
    */
  def query: Query[M0#T, M0, Seq] = service.filter(baseFilter)

  /**
    * Returns all the [[Model]]s in the set.
    *
    * @return All models in set
    */
  def allNow: IO[Set[M0]] = service.runDBIO(service.filter(baseFilter).to[Set].result)

  /**
    * Returns a query for the size of this set.
    *
    * @return Size of set
    */
  def size: Rep[Int] = service.count(this.baseFilter)

  /**
    * Returns the size of this set.
    *
    * @return Size of set
    */
  def sizeNow: IO[Int] = service.countNow(this.baseFilter)

  /**
    * Returns true if this set is empty.
    *
    * @return True if set is empty
    */
  def isEmpty: Rep[Boolean] = size === 0

  /**
    * Returns true if this set is not empty.
    *
    * @return True if not empty
    */
  def nonEmpty: Rep[Boolean] = size > 0

  /**
    * Returns true if this set is empty and runs the program now.
    *
    * @return True if set is empty
    */
  def isEmptyNow: IO[Boolean] = service.runDBIO(isEmpty.result)

  /**
    * Returns true if this set is not empty and runs the program now.
    *
    * @return True if not empty
    */
  def nonEmptyNow: IO[Boolean] = service.runDBIO(nonEmpty.result)

  /**
    * Returns true if this set contains the specified model.
    *
    * @param model Model to look for
    * @return True if contained in set
    */
  def contains(model: M0): Rep[Boolean] = exists(IdFilter(model.id))

  /**
    * Returns true if this set contains the specified model and runs this program now.
    *
    * @param model Model to look for
    * @return True if contained in set
    */
  def containsNow(model: M0): IO[Boolean] = service.runDBIO(contains(model).result)

  /**
    * Returns true if any models match the specified filter.
    *
    * @param filter Filter to use
    * @return       True if any model matches
    */
  def exists(filter: M0#T => Rep[Boolean]): Rep[Boolean] = service.count(baseFilter && filter) > 0

  /**
    * Returns true if any models match the specified filter and runs this program now.
    *
    * @param filter Filter to use
    * @return       True if any model matches
    */
  def existsNow(filter: M0#T => Rep[Boolean]): IO[Boolean] = service.runDBIO(exists(filter).result)

  /**
    * Adds a new model to it's table.
    *
    * @param model Model to add
    * @return New model
    */
  def add(model: InsertFunc[M0]): IO[M0] = service.insert(model)

  /**
    * Updates an existing model.
    *
    * @param model The model to update
    * @return The updated model
    */
  def update(model: M0): IO[M0] = service.update(model)

  /**
    * Removes the specified model from this set if it is contained.
    *
    * @param model Model to remove
    */
  def remove(model: M0): IO[Int] = service.delete(model)

  /**
    * Removes all the models from this set matching the given filter.
    *
    * @param filter Filter to use
    */
  def removeAll(filter: M0#T => Rep[Boolean] = All): IO[Int] =
    service.deleteWhere(baseFilter && filter)

  /**
    * Returns the first model matching the specified filter.
    *
    * @param filter Filter to use
    * @return       Model matching filter, if any
    */
  def find(filter: M0#T => Rep[Boolean]): Query[M0#T, M0, Seq] = service.find(baseFilter && filter)

  /**
    * Returns the first model matching the specified filter and runs this program now.
    *
    * @param filter Filter to use
    * @return       Model matching filter, if any
    */
  def findNow(filter: M0#T => Rep[Boolean]): OptionT[IO, M0] = service.findNow(baseFilter && filter)

  /**
    * Returns a sorted query by the specified [[ColumnOrdered]].
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
  ): Query[M0#T, M0, Seq] = service.collect[M0](baseFilter && filter, Some(ordering), limit, offset)

  /**
    * Returns a sorted Seq by the specified [[ColumnOrdered]].
    *
    * @param ordering Model ordering
    * @param filter   Filter to use
    * @param limit    Amount to take
    * @param offset   Amount to drop
    * @return         Sorted models
    */
  def sortedNow(
      ordering: M0#T => ColumnOrdered[_],
      filter: M0#T => Rep[Boolean] = All,
      limit: Int = -1,
      offset: Int = -1
  ): IO[Seq[M0]] = service.runDBIO(sorted(ordering, filter, limit, offset).result)

  /**
    * Filters this query by the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filter(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Query[M0#T, M0, Seq] =
    service.filter(baseFilter && filter, limit, offset)

  /**
    * Filters this set by the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filterNow(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): IO[Seq[M0]] =
    service.runDBIO(this.filter(filter, limit, offset).result)

  /**
    * Filters this query by the opposite of the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filterNot(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Query[M0#T, M0, Seq] =
    this.filter(!filter(_), limit, offset)

  /**
    * Filters this set by the opposite of the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filterNotNow(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): IO[Seq[M0]] =
    this.filterNow(!filter(_), limit, offset)

  /**
    * Counts how many elements in this set fulfill some predicate.
    * @param predicate The predicate to use
    * @return The amount of elements that fulfill the predicate.
    */
  def count(predicate: M0#T => Rep[Boolean]): Rep[Int] =
    service.count(this.baseFilter && predicate)

  /**
    * Counts how many elements in this set fulfill some predicate and runs this program now.
    * @param predicate The predicate to use
    * @return The amount of elements that fulfill the predicate.
    */
  def countNow(predicate: M0#T => Rep[Boolean]): IO[Int] =
    service.runDBIO(count(this.baseFilter && predicate).result)

  /**
    * Returns a Seq of this set.
    *
    * @return Seq of set
    */
  def toSeqNow: IO[Seq[M0]] = service.runDBIO(service.filter(baseFilter).result)

}
