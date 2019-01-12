package db

import scala.language.implicitConversions

import db.table.ModelTable

/**
  * Represents a Model that may or may not exist in the database.
  */
abstract class Model { self =>

  def id: ObjId[M]
  def createdAt: ObjectTimestamp

  /** Self referential type */
  type M <: Model { type M = self.M }

  /** The model's table */
  type T <: ModelTable[M]
}
