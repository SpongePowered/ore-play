package ore.db

import scala.language.implicitConversions

import java.sql.Timestamp

sealed trait DbInitialized[+A] {
  def value: A
  def unsafeToOption: Option[A]
  override def toString: String = unsafeToOption match {
    case Some(value) => value.toString
    case None        => "DbInitialized.Uninitialized"
  }
}

sealed trait ObjId[+A] extends DbInitialized[DbRef[A]] {

  override def equals(other: Any): Boolean = other match {
    case that: ObjId[_] => value == that.value
    case _              => false
  }

  override def hashCode(): Int = {
    val state = Seq(value)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
object ObjId {
  implicit def unwrapObjId[A](objId: ObjId[A]): DbRef[A] = objId.value

  class UnsafeUninitialized[A] extends ObjId[A] {
    override def value: Nothing                  = sys.error("Tried to access uninitialized ObjId. This should be impossible")
    override def unsafeToOption: Option[Nothing] = None
  }

  private class RealObjId[A](val value: DbRef[A]) extends ObjId[A] {
    override def unsafeToOption: Option[DbRef[A]] = Some(value)
  }

  def apply[A](id: DbRef[A]): ObjId[A] = new RealObjId(id)

  def unsafeFromOption[A](option: Option[DbRef[A]]): ObjId[A] = option match {
    case Some(id) => ObjId(id)
    case None     => new UnsafeUninitialized
  }
}

sealed trait ObjTimestamp extends DbInitialized[Timestamp] {

  override def equals(other: Any): Boolean = other match {
    case that: ObjTimestamp => value == that.value
    case _                  => false
  }

  override def hashCode(): Int = {
    val state = Seq(value)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
object ObjTimestamp {
  implicit def unwrapObjTimestamp(objTimestamp: ObjTimestamp): Timestamp = objTimestamp.value

  object UnsafeUninitialized extends ObjTimestamp {
    override def value: Nothing                  = sys.error("Tried to access uninitialized ObjTimestamp. This should be impossible")
    override def unsafeToOption: Option[Nothing] = None
  }

  private class RealObjTimestamp(val value: Timestamp) extends ObjTimestamp {
    override def unsafeToOption: Option[Timestamp] = Some(value)
  }

  def apply(timestamp: Timestamp): ObjTimestamp = new RealObjTimestamp(timestamp)

  def unsafeFromOption(option: Option[Timestamp]): ObjTimestamp = option match {
    case Some(time) => ObjTimestamp(time)
    case None       => UnsafeUninitialized
  }
}
