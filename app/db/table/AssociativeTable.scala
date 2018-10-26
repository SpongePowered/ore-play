package db.table

import db.ObjectReference
import db.impl.OrePostgresDriver.api._

/**
  * Represents a associative table between two models.
  *
  * @param tag Table tag
  * @param name Table name
  */
abstract class AssociativeTable(
    tag: Tag,
    name: String,
) extends Table[(ObjectReference, ObjectReference)](tag, name)
