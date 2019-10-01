package ore.db.impl

import java.time.OffsetDateTime

import ore.db.{DbRef, Model, ObjId, ObjOffsetDateTime}

// Alias Slick's Tag type because we have our own Tag type
package object schema {

  def mkApply[A, Rest](restApply: Rest => A): ((Option[DbRef[A]], Option[OffsetDateTime], Rest)) => Model[A] =
    t => Model(ObjId.unsafeFromOption(t._1), ObjOffsetDateTime.unsafeFromOption(t._2), restApply(t._3))

  def mkUnapply[A, Rest](
      restUnapply: A => Option[Rest]
  ): Model[A] => Option[(Option[DbRef[A]], Option[OffsetDateTime], Rest)] = model => {
    for {
      t <- Model.unapply(model)
      (id, time, inner) = t
      rest <- restUnapply(inner)
    } yield (id.unsafeToOption, time.unsafeToOption, rest)
  }
}
