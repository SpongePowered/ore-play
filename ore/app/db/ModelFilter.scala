package db

import scala.language.implicitConversions

import db.impl.OrePostgresDriver.api._

/**
  * A wrapper class for a T => Rep[Boolean] on a ModelTable. This allows for easier
  * chaining of filters on a ModelTable. ModelFilters can have their base
  * function lifted out of this wrapper implicitly.
  *
  * @param fn   Base filter function
  */
class ModelFilter[T <: Table[_]](private val fn: T => Rep[Boolean]) extends AnyVal {

  /**
    * Applies && to the wrapped function and returns a new filter.
    *
    * @param that Filter function to apply
    * @return New model filter
    */
  def &&(that: T => Rep[Boolean]): T => Rep[Boolean] = m => fn(m) && that(m)

  /**
    * Applies || to the wrapped function and returns a new filter.
    *
    * @param that Filter function to apply
    * @return New filter
    */
  def ||(that: T => Rep[Boolean]): T => Rep[Boolean] = m => fn(m) || that(m)
}

object ModelFilter {

  implicit def liftFilter[T <: Table[_]](f: T => Rep[Boolean]): ModelFilter[T] = new ModelFilter(f)

  def apply[M](model: ModelCompanion[M])(f: model.T => Rep[Boolean]): model.T => Rep[Boolean] = f
}
