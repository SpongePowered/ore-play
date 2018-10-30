package db.impl.schema
import java.sql.Timestamp

import db.{DbRef, ObjId, ObjectTimestamp}

import shapeless.{::, Generic, HList, HNil}
import shapeless.Nat.{_0, _1, _2}
import shapeless.ops.hlist.{At, Drop, Prepend, Tupler}

class MkTuplePartiallyApplied[A](private val b: Boolean = false) extends AnyVal {
  def apply[ARepr <: HList, Rest <: HList, Out <: Product, OutRepr <: HList]()(
      implicit
      gen: Generic.Aux[A, ARepr],
      at0A: At.Aux[ARepr, _0, ObjId[A]],
      at1A: At.Aux[ARepr, _1, ObjectTimestamp],
      drop2A: Drop.Aux[ARepr, _2, Rest],
      concatA: Prepend.Aux[Option[DbRef[A]] :: Option[Timestamp] :: HNil, Rest, OutRepr],
      outTupler: Tupler.Aux[OutRepr, Out],
      outGen: Generic.Aux[Out, OutRepr],
      at0Out: At.Aux[OutRepr, _0, Option[DbRef[A]]],
      at1Out: At.Aux[OutRepr, _1, Option[Timestamp]],
      drop2Out: Drop.Aux[OutRepr, _2, Rest],
      concatOut: Prepend.Aux[ObjId[A] :: ObjectTimestamp :: HNil, Rest, ARepr],
  ): (Out => A, A => Option[Out]) = {
    identity(outTupler) //Only needed for stuff to compile

    val apply: Out => A = out => {
      val repr    = outGen.to(out)
      val id      = ObjId.unsafeFromOption[A](at0Out(repr))
      val time    = ObjectTimestamp.unsafeFromOption(at1Out(repr))
      val rest    = drop2Out(repr)
      val newRepr = concatOut(id :: time :: HNil, rest)
      gen.from(newRepr)
    }

    val unapply: A => Option[Out] = a => {
      val repr    = gen.to(a)
      val optId   = at0A(repr).unsafeToOption
      val optTime = at1A(repr).unsafeToOption
      val rest    = drop2A(repr)
      val newRepr = concatA(optId :: optTime :: HNil, rest)
      Some(outGen.from(newRepr))
    }

    (apply, unapply)
  }
}
