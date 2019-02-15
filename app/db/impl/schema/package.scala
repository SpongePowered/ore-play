package db.impl

import java.sql.Timestamp

import db.{DbModel, DbRef, ObjId, ObjTimestamp}

// Alias Slick's Tag type because we have our own Tag type
package object schema {

  def mkApply[A, Rest](restApply: Rest => A): ((Option[DbRef[A]], Option[Timestamp], Rest)) => DbModel[A] =
    t => DbModel(ObjId.unsafeFromOption(t._1), ObjTimestamp.unsafeFromOption(t._2), restApply(t._3))

  def mkUnapply[A, Rest](
      restUnapply: A => Option[Rest]
  ): DbModel[A] => Option[(Option[DbRef[A]], Option[Timestamp], Rest)] = model => {
    for {
      t <- DbModel.unapply(model)
      (id, time, inner) = t
      rest <- restUnapply(inner)
    } yield (id.unsafeToOption, time.unsafeToOption, rest)
  }
}
