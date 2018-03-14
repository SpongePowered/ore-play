package db.access

import db.ModelFilter.IdFilter
import db.impl.OrePostgresDriver.api._
import db.{Model, ModelFilter, ModelService}
import slick.lifted.ColumnOrdered

/**
  * Provides simple, synchronous, access to a ModelTable.
  */
class ModelAccess[M <: Model](val service: ModelService,
                              val modelClass: Class[M],
                              val baseFilter: ModelFilter[M] = ModelFilter[M]()) {

  import service.await

  /**
    * Returns the model with the specified ID.
    *
    * @param id   ID to lookup
    * @return     Model with ID or None if not found
    */
  def get(id: Int): Option[M] = await(this.service.get[M](this.modelClass, id, this.baseFilter.fn)).get

  /**
    * Returns a set of Models that have an ID that is in the specified Int set.
    *
    * @param ids  ID set
    * @return     Models in ID set
    */
  def in(ids: Set[Int]): Set[M] = await(this.service.in[M](this.modelClass, ids, this.baseFilter.fn)).get.toSet

  /**
    * Returns all the [[Model]]s in the set.
    *
    * @return All models in set
    */
  def all: Set[M] = await(this.service.filter[M](this.modelClass, this.baseFilter.fn)).get.toSet

  /**
    * Returns the size of this set.
    *
    * @return Size of set
    */
  def size: Int = await(this.service.count[M](this.modelClass, this.baseFilter.fn)).get

  /**
    * Returns true if this set is empty.
    *
    * @return True if set is empty
    */
  def isEmpty: Boolean = this.size == 0

  /**
    * Returns true if this set is not empty.
    *
    * @return True if not empty
    */
  def nonEmpty: Boolean = this.size > 0

  /**
    * Returns true if this set contains the specified model.
    *
    * @param model Model to look for
    * @return True if contained in set
    */
  def contains(model: M): Boolean
  = await(this.service.count[M](this.modelClass, (this.baseFilter +&& IdFilter(model.id.get)).fn)).get > 0

  /**
    * Returns true if any models match the specified filter.
    *
    * @param filter Filter to use
    * @return       True if any model matches
    */
  def exists(filter: M#T => Rep[Boolean]) = await(this.service.count[M](this.modelClass, (this.baseFilter && filter).fn)).get > 0

  /**
    * Adds a new model to it's table.
    *
    * @param model Model to add
    * @return New model
    */
  def add(model: M): M = await(this.service insert model).get

  /**
    * Removes the specified model from this set if it is contained.
    *
    * @param model Model to remove
    */
  def remove(model: M) = await(this.service delete model).get

  /**
    * Removes all the models from this set matching the given filter.
    *
    * @param filter Filter to use
    */
  def removeAll(filter: M#T => Rep[Boolean] = _ => true)
  = await(this.service.deleteWhere[M](this.modelClass, (this.baseFilter && filter).fn))

  /**
    * Returns the first model matching the specified filter.
    *
    * @param filter Filter to use
    * @return       Model matching filter, if any
    */
  def find(filter: M#T => Rep[Boolean]): Option[M]
  = await(this.service.find[M](this.modelClass, (this.baseFilter && filter).fn)).get

  /**
    * Returns a sorted Seq by the specified [[ColumnOrdered]].
    *
    * @param ordering Model ordering
    * @param filter   Filter to use
    * @param limit    Amount to take
    * @param offset   Amount to drop
    * @return         Sorted models
    */
  def sorted(ordering: M#T => ColumnOrdered[_], filter: M#T => Rep[Boolean] = null, limit: Int = -1,
             offset: Int = -1): Seq[M]
  = await(this.service.sorted[M](this.modelClass, ordering, (this.baseFilter && filter).fn, limit, offset)).get

  /**
    * Same as sorted but with multiple orderings
    */
  def sortedMultipleOrders(orderings: M#T => List[ColumnOrdered[_]], filter: M#T => Rep[Boolean] = null, limit: Int = -1,
             offset: Int = -1): Seq[M]
  = await(this.service.sortedMultipleOrders[M](this.modelClass, orderings, (this.baseFilter && filter).fn, limit, offset)).get

  /**
    * Filters this set by the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filter(filter: M#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Seq[M]
  = await(this.service.filter[M](this.modelClass, (this.baseFilter && filter).fn, limit, offset)).get

  /**
    * Filters this set by the opposite of the given function.
    *
    * @param filter Filter to use
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @return       Filtered models
    */
  def filterNot(filter: M#T => Rep[Boolean], limit: Int = -1, offset: Int = -1): Seq[M] = this.filter(!filter(_), limit, offset)

  /**
    * Returns a Seq of this set.
    *
    * @return Seq of set
    */
  def toSeq: Seq[M] = this.all.toSeq

}
