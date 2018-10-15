package db

import scala.language.implicitConversions

import scala.concurrent.Future

import db.table.ModelTable

/**
  * Represents a Model that may or may not exist in the database.
  */
abstract class Model { self =>

  def id: ObjectId
  def createdAt: ObjectTimestamp

  /** Self referential type */
  type M <: Model { type M = self.M }

  /** The model's table */
  type T <: ModelTable[M]

  /**
    * Removes this model from it's table.
    */
  def remove()(implicit service: ModelService, query: ModelQuery[M]): Future[Int] =
    Defined(service.delete(this.asInstanceOf[M]))

  /**
    * Returns true if this Project is defined in the database.
    *
    * @return True if defined in database
    */
  def isDefined: Boolean = this.id.unsafeToOption.isDefined

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id       ID to set
    * @param theTime  Timestamp
    * @return         Copy of model
    */
  def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model

  protected def Defined[R](f: => R): R =
    if (isDefined)
      f
    else
      throw new IllegalStateException("model must exist")

}
