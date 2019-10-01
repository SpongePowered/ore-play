package ore.db

import scala.language.implicitConversions

import java.time.OffsetDateTime

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

sealed trait ObjOffsetDateTime extends DbInitialized[OffsetDateTime] {

  override def equals(other: Any): Boolean = other match {
    case that: ObjOffsetDateTime => value == that.value
    case _                       => false
  }

  override def hashCode(): Int = {
    val state = Seq(value)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
object ObjOffsetDateTime {
  implicit def unwrapObjTimestamp(objTimestamp: ObjOffsetDateTime): OffsetDateTime = objTimestamp.value

  object UnsafeUninitialized extends ObjOffsetDateTime {
    override def value: Nothing                  = sys.error("Tried to access uninitialized ObjTimestamp. This should be impossible")
    override def unsafeToOption: Option[Nothing] = None
  }

  private class RealObjOffsetDateTime(val value: OffsetDateTime) extends ObjOffsetDateTime {
    override def unsafeToOption: Option[OffsetDateTime] = Some(value)
  }

  def apply(timestamp: OffsetDateTime): ObjOffsetDateTime = new RealObjOffsetDateTime(timestamp)

  def unsafeFromOption(option: Option[OffsetDateTime]): ObjOffsetDateTime = option match {
    case Some(time) => ObjOffsetDateTime(time)
    case None       => UnsafeUninitialized
  }
}
