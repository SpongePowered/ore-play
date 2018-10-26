package db

import scala.language.implicitConversions

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
    * Returns true if this Project is defined in the database.
    *
    * @return True if defined in database
    */
  def isDefined: Boolean = this.id.unsafeToOption.isDefined

  protected def Defined[R](f: => R): R =
    if (isDefined)
      f
    else
      throw new IllegalStateException("model must exist")

}
