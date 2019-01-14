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
  def get(id: DbRef[M0]): OptionT[IO, M0] = service.get(id, baseFilter)

  /**
    * Returns a set of Models that have an ID that is in the specified Int set.
    *
    * @param ids  ID set
    * @return     Models in ID set
    */
  def in(ids: Set[DbRef[M0]]): IO[Set[M0]] = service.in(ids, baseFilter).map(_.toSet)

  /**
    * Returns all the [[Model]]s in the set.
    *
    * @return All models in set
    */
  def all: IO[Set[M0]] = service.filter(baseFilter).map(_.toSet)

  /**
    * Returns the size of this set.
    *
    * @return Size of set
    */
  def size: IO[Int] = service.count(this.baseFilter)

  /**
    * Returns true if this set is empty.
    *
    * @return True if set is empty
    */
  def isEmpty: IO[Boolean] = size.map(_ == 0)

  /**
    * Returns true if this set is not empty.
    *
    * @return True if not empty
    */
  def nonEmpty: IO[Boolean] = size.map(_ > 0)

  /**
    * Returns true if this set contains the specified model.
    *
    * @param model Model to look for
    * @return True if contained in set
    */
  def contains(model: M0): IO[Boolean] = exists(IdFilter(model.id))

  /**
    * Returns true if any models match the specified filter.
    *
    * @param filter Filter to use
    * @return       True if any model matches
    */
  def exists(filter: M0#T => Rep[Boolean]): IO[Boolean] = service.count(baseFilter && filter).map(_ > 0)

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
  def find(filter: M0#T => Rep[Boolean]): OptionT[IO, M0] = service.find(baseFilter && filter)

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
  ): IO[Seq[M0]] = service.collect[M0](baseFilter && filter, Some(ordering), limit, offset)

  /**
    * Filters this set by the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filter(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): IO[Seq[M0]] =
    service.filter(baseFilter && filter, limit, offset)

  /**
    * Filters this set by the opposite of the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filterNot(filter: M0#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): IO[Seq[M0]] =
    this.filter(!filter(_), limit, offset)

  /**
    * Counts how many elements in this set fulfill some predicate.
    * @param predicate The predicate to use
    * @return The amount of elements that fulfill the predicate.
    */
  def count(predicate: M0#T => Rep[Boolean]): IO[Int] =
    service.count(this.baseFilter && predicate)

  /**
    * Returns a Seq of this set.
    *
    * @return Seq of set
    */
  def toSeq: IO[Seq[M0]] = all.map(_.toSeq)

}
