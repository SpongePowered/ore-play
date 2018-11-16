package db.impl

import scala.reflect.ClassTag

import slick.ast.MappedScalaType
import slick.lifted.{MappedProjection, ShapedValue}

// Alias Slick's Tag type because we have our own Tag type
package object schema {

  def mkTuple[A] = new MkTuplePartiallyApplied[A]

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
