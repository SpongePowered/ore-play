package db

import java.sql.Timestamp

import db.ObjectId.Uninitialized

sealed trait DbInitialized[A] {
  def value: A
  def unsafeToOption: Option[A]
}

sealed trait ObjectId extends DbInitialized[ObjectReference]
object ObjectId {
  case object Uninitialized extends ObjectId {
    override def value: Nothing = sys.error("Tried to access uninitialized ObjectId")
    override def unsafeToOption: Option[Nothing] = None
  }

  private case class RealObjectId(value: ObjectReference) extends ObjectId {
    override def unsafeToOption: Option[ObjectReference] = Some(value)
  }

  def apply(id: ObjectReference): ObjectId = RealObjectId(id)

  def unsafeFromOption(option: Option[ObjectReference]): ObjectId = option match {
    case Some(id) => ObjectId(id)
    case None => Uninitialized
  }
}

sealed trait ObjectTimestamp extends DbInitialized[Timestamp]
object ObjectTimestamp {
  case object Uninitialized extends ObjectTimestamp {
    override def value: Nothing = sys.error("Tried to access uninitialized ObjectTimestamp")
    override def unsafeToOption: Option[Nothing] = None
  }

  private case class RealObjectTimestamp(value: Timestamp) extends ObjectTimestamp {
    override def unsafeToOption: Option[Timestamp] = Some(value)
  }

  def apply(timestamp: Timestamp): ObjectTimestamp = RealObjectTimestamp(timestamp)

  def unsafeFromOption(option: Option[Timestamp]): ObjectTimestamp = option match {
    case Some(id) => ObjectTimestamp(id)
    case None => Uninitialized
  }
}