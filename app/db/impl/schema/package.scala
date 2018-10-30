package db.impl

import java.sql.Timestamp

import scala.reflect.ClassTag

import db.{DbRef, ObjId, ObjectTimestamp}

import shapeless.Generic.Aux
import shapeless.Nat._
import shapeless._
import shapeless.ops.function._
import shapeless.ops.hlist
import shapeless.ops.hlist.At.Aux
import shapeless.ops.hlist.Drop.Aux
import shapeless.ops.hlist._
import slick.ast.MappedScalaType
import slick.lifted.{MappedProjection, ShapedValue}

// Alias Slick's Tag type because we have our own Tag type
package object schema {
  type RowTag     = slick.lifted.Tag
  type ProjectTag = models.project.Tag

  def mkTuple[A] = new MkTuplePartiallyApplied[A]

  def convertApply[F, Rest <: HList, R](f: F)(
      implicit toHList: FnToProduct.Aux[F, ObjId[R] :: ObjectTimestamp :: Rest => R],
      fromHList: FnFromProduct[Option[DbRef[R]] :: Option[Timestamp] :: Rest => R]
  ): fromHList.Out = {
    val objHListFun: ObjId[R] :: ObjectTimestamp :: Rest => R = toHList(f)
    val optHListFun: Option[DbRef[R]] :: Option[Timestamp] :: Rest => R = {
      case id :: time :: rest =>
        objHListFun(ObjId.unsafeFromOption(id) :: ObjectTimestamp.unsafeFromOption(time) :: rest)
    }
    val normalFun: fromHList.Out = fromHList.apply(optHListFun)

    normalFun
  }

  def convertUnapply[P <: Product, A, Repr <: HList, Rest <: HList](f: A => Option[P])(
      implicit
      fromTuple: Generic.Aux[P, Repr],
      at0: At.Aux[Repr, _0, ObjId[A]],
      at1: At.Aux[Repr, _1, ObjectTimestamp],
      drop2: Drop.Aux[Repr, _2, Rest],
      toTuple: Tupler[Option[DbRef[A]] :: Option[Timestamp] :: Rest]
  ): A => Option[toTuple.Out] = a => {
    val optProd: Option[P] = f(a)
    val mappedOptProd: Option[toTuple.Out] = optProd.map { prod =>
      val repr: Repr                                            = fromTuple.to(prod)
      val id: ObjId[A]                                          = at0(repr)
      val time: ObjectTimestamp                                 = at1(repr)
      val rest: Rest                                            = drop2(repr)
      val newGen: Option[DbRef[A]] :: Option[Timestamp] :: Rest = id.unsafeToOption :: time.unsafeToOption :: rest
      toTuple(newGen)
    }

    mappedOptProd
  }

  def mkProj[T, U, R: ClassTag](
      shapedValue: ShapedValue[T, U]
  )(fg: (U => R, R => Option[U])): MappedProjection[R, U] = {
    val f = fg._1
    val g = fg._2
    new MappedProjection[R, U](
      shapedValue.shape.toNode(shapedValue.value),
      MappedScalaType.Mapper(g.andThen(_.get).asInstanceOf[Any => Any], f.asInstanceOf[Any => Any], None),
      implicitly[ClassTag[R]]
    )
  }
}
