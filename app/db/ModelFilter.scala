package db

import db.impl.OrePostgresDriver.api._

/**
  * A wrapper class for a T => Rep[Boolean] on a ModelTable. This allows for easier
  * chaining of filters on a ModelTable. ModelFilters can have their base
  * function lifted out of this wrapper implicitly.
  *
  * @param fn   Base filter function
  * @tparam M   Model type
  */
case class ModelFilter[M <: Model](fn: M#T => Rep[Boolean] = null) {

  /**
    * Applies && to the wrapped function and returns a new filter.
    *
    * @param that Filter function to apply
    * @return New model filter
    */
  def &&(that: M#T => Rep[Boolean]): ModelFilter[M] = ModelFilter[M](m => trueIfNull(fn)(m) && trueIfNull(that)(m))

  /**
    * Applies && to the specified filter's function and returns the result
    * filter.
    *
    * @param that Filter to apply
    * @return New filter
    */
  def +&&(that: ModelFilter[M]): ModelFilter[M] = this && that.fn

  /**
    * Applies && to the specified filter's function and returns the result
    * filter function.
    *
    * @param that Filter function to apply
    * @return Filter function
    */
  def &&^(that: M#T => Rep[Boolean]): M#T => Rep[Boolean] = (this && that).fn

  /**
    * Applies && to the specified filter's function and returns the result
    * filter function.
    *
    * @param that Filter to apply
    * @return Filter function
    */
  def +&&^(that: ModelFilter[M]): M#T => Rep[Boolean] = (this +&& that).fn

  /**
    * Applies || to the wrapped function and returns a new filter.
    *
    * @param that Filter function to apply
    * @return New filter
    */
  def ||(that: M#T => Rep[Boolean]): ModelFilter[M] = ModelFilter[M](m => falseIfNull(fn)(m) || falseIfNull(that)(m))

  /**
    * Applies || to the specified filter's function and returns the result
    * filter.
    *
    * @param that Filter to apply
    * @return New filter
    */
  def +||(that: ModelFilter[M]): ModelFilter[M] = this || that.fn

  /**
    * Applies || to the specified filter's function and returns the result
    * filter function.
    *
    * @param that Filter function to apply
    * @return Filter function
    */
  def ||^(that: M#T => Rep[Boolean]): M#T => Rep[Boolean] = (this || that).fn

  /**
    * Applies || to the specified filter's function and returns the result
    * filter function.
    *
    * @param that Filter to apply
    * @return Filter function
    */
  def +||^(that: M#T => Rep[Boolean]): M#T => Rep[Boolean] = (this +|| ModelFilter[M](that)).fn

  private def trueIfNull(fn: M#T => Rep[Boolean]): M#T => Rep[Boolean] = if (fn == null) _ => true else fn
  private def falseIfNull(fn: M#T => Rep[Boolean]): M#T => Rep[Boolean] = if (fn == null) _ => false else fn

}

object ModelFilter {

  def Empty[M <: Model]: ModelFilter[M] = ModelFilter[M](_ => true)

  def All[M <: Model]: ModelFilter[M] = ModelFilter[M](_ => false)

  /** Filters models by ID */
  def IdFilter[M <: Model](id: ObjectReference): ModelFilter[M] = ModelFilter[M](_.id === id)

}
