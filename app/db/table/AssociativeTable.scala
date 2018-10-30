package db.table

import db.DbRef
import db.impl.OrePostgresDriver.api._

/**
  * Represents a associative table between two models.
  *
  * @param tag Table tag
  * @param name Table name
  */
abstract class AssociativeTable[A, B](
    tag: Tag,
    name: String,
) extends Table[(DbRef[A], DbRef[B])](tag, name)
