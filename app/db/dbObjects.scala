package db

import java.sql.Timestamp

sealed trait DbInitialized[A] {
  def value: A
  def unsafeToOption: Option[A]
  override def toString: String = unsafeToOption match {
    case Some(value) => value.toString
    case None        => "DbInitialized.Uninitialized"
  }
}

sealed trait ObjId[A] extends DbInitialized[DbRef[A]]
object ObjId {
  private[db] case class UnsafeUninitialized[A]() extends ObjId[A] {
    override def value: Nothing                  = sys.error("Tried to access uninitialized ObjectId")
    override def unsafeToOption: Option[Nothing] = None
  }

  private case class RealObjId[A](value: DbRef[A]) extends ObjId[A] {
    override def unsafeToOption: Option[DbRef[A]] = Some(value)
  }

  def apply[A](id: DbRef[A]): ObjId[A] = RealObjId(id)

  def unsafeFromOption[A](option: Option[DbRef[A]]): ObjId[A] = option match {
    case Some(id) => ObjId(id)
    case None     => UnsafeUninitialized()
  }
}

sealed trait ObjectTimestamp extends DbInitialized[Timestamp]
object ObjectTimestamp {
  private[db] case object UnsafeUninitialized extends ObjectTimestamp {
    override def value: Nothing                  = sys.error("Tried to access uninitialized ObjectTimestamp")
    override def unsafeToOption: Option[Nothing] = None
  }

  private case class RealObjectTimestamp(value: Timestamp) extends ObjectTimestamp {
    override def unsafeToOption: Option[Timestamp] = Some(value)
  }

  def apply(timestamp: Timestamp): ObjectTimestamp = RealObjectTimestamp(timestamp)

  def unsafeFromOption(option: Option[Timestamp]): ObjectTimestamp = option match {
    case Some(time) => ObjectTimestamp(time)
    case None       => UnsafeUninitialized
  }
}
