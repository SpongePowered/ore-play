package db.impl

import java.sql.Timestamp

import db.{ObjectId, ObjectReference, ObjectTimestamp}

import shapeless.Nat._
import shapeless._
import shapeless.ops.function._
import shapeless.ops.hlist._

// Alias Slick's Tag type because we have our own Tag type
package object schema {
  type RowTag     = slick.lifted.Tag
  type ProjectTag = models.project.Tag

  def convertApply[F, Rest <: HList, R](f: F)(
      implicit toHList: FnToProduct.Aux[F, ObjectId :: ObjectTimestamp :: Rest => R],
      fromHList: FnFromProduct[Option[ObjectReference] :: Option[Timestamp] :: Rest => R]
  ): fromHList.Out = {
    val objHListFun: ObjectId :: ObjectTimestamp :: Rest => R = toHList(f)
    val optHListFun: Option[ObjectReference] :: Option[Timestamp] :: Rest => R = {
      case id :: time :: rest =>
        objHListFun(ObjectId.unsafeFromOption(id) :: ObjectTimestamp.unsafeFromOption(time) :: rest)
    }
    val normalFun: fromHList.Out = fromHList.apply(optHListFun)

    normalFun
  }

  def convertUnapply[P <: Product, A, Repr <: HList, Rest <: HList](f: A => Option[P])(
      implicit
      fromTuple: Generic.Aux[P, Repr],
      at0: At.Aux[Repr, _0, ObjectId],
      at1: At.Aux[Repr, _1, ObjectTimestamp],
      drop2: Drop.Aux[Repr, _2, Rest],
      toTuple: Tupler[Option[ObjectReference] :: Option[Timestamp] :: Rest]
  ): A => Option[toTuple.Out] = a => {
    val optProd: Option[P] = f(a)
    val mappedOptProd: Option[toTuple.Out] = optProd.map { prod =>
      val repr: Repr            = fromTuple.to(prod)
      val id: ObjectId          = at0(repr)
      val time: ObjectTimestamp = at1(repr)
      val rest: Rest            = drop2(repr)
      val newGen
        : Option[ObjectReference] :: Option[Timestamp] :: Rest = id.unsafeToOption :: time.unsafeToOption :: rest
      toTuple(newGen)
    }

    mappedOptProd
  }
}
