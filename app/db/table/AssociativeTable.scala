package db.table

import db.{Model, ObjectReference}
import db.impl.OrePostgresDriver.api._

/**
  * Represents a associative table between two models.
  *
  * @param tag Table tag
  * @param name Table name
  */
abstract class AssociativeTable(tag: Tag,
                                name: String,
                                val firstClass: Class[_ <: Model],
                                val secondClass: Class[_ <: Model])
  extends Table[(ObjectReference, ObjectReference)](tag, name)
