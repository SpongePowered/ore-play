package db

import scala.language.implicitConversions

import db.impl.OrePostgresDriver.api._

/**
  * A wrapper class for a T => Rep[Boolean] on a ModelTable. This allows for easier
  * chaining of filters on a ModelTable. ModelFilters can have their base
  * function lifted out of this wrapper implicitly.
  *
  * @param fn   Base filter function
  * @tparam M   Model type
  */
class ModelFilter[M <: Model](private val fn: M#T => Rep[Boolean]) extends AnyVal {

  /**
    * Applies && to the wrapped function and returns a new filter.
    *
    * @param that Filter function to apply
    * @return New model filter
    */
  def &&(that: M#T => Rep[Boolean]): M#T => Rep[Boolean] = m => fn(m) && that(m)

  /**
    * Applies || to the wrapped function and returns a new filter.
    *
    * @param that Filter function to apply
    * @return New filter
    */
  def ||(that: M#T => Rep[Boolean]): M#T => Rep[Boolean] = m => fn(m) || that(m)
}

object ModelFilter {

  implicit def liftFilter[M <: Model](f: M#T => Rep[Boolean]): ModelFilter[M] = new ModelFilter(f)

  def apply[M <: Model](f: M#T => Rep[Boolean]): M#T => Rep[Boolean] = f

  def Empty[M <: Model]: M#T => Rep[Boolean] = _ => false

  def All[M <: Model]: M#T => Rep[Boolean] = _ => true

  /** Filters models by ID */
  def IdFilter[M <: Model](id: ObjectReference): M#T => Rep[Boolean] = _.id === id

}
