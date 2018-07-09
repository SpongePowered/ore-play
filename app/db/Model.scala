package db

import scala.concurrent.Future

import com.google.common.base.Preconditions.checkNotNull
import db.table.ModelTable
import db.table.key.Key

import scala.language.implicitConversions

/**
  * Represents a Model that may or may not exist in the database.
  */
abstract class Model(val id: ObjectId, val createdAt: ObjectTimestamp) { self =>

  /** Self referential type */
  type M <: Model { type M = self.M }
  /** The model's table */
  type T <: ModelTable[M]
  /** The model's schema */
  type S <: ModelSchema[M]

  /** The ModelService that this Model was processed with */
  implicit var service: ModelService = _

  private var _isProcessed = false

  implicit def convertKey[A](key: Key[_, A]): Key[M, A] = {
    if (!key.isInstanceOf[Key[M @unchecked, A @unchecked]])
      throw new RuntimeException("tried to use key on wrong model")
    key.asInstanceOf[Key[M, A]]
  }

  /**
    * Updates the specified key in the model's table.
    *
    * @param key Model key to update
    */
  def update[A](key: Key[M, A]): Future[Int] = Defined(key.update(this.asInstanceOf[M]))

  /**
    * Removes this model from it's table.
    */
  def remove(): Future[Int] = Defined(this.service.delete(this.asInstanceOf[M]))

  /**
    * Returns true if this Project is defined in the database.
    *
    * @return True if defined in database
    */
  def isDefined: Boolean = this.id.unsafeToOption.isDefined

  /**
    * Returns the ModelActions associated with this Model.
    *
    * @param service  Optional model service to provide to the model if it has
    *                 not yet been processed
    * @return         ModelActions
    */
  def schema(implicit service: ModelService = null): S = {
    if (this.service == null)
      this.service = service
    checkNotNull(this.service, "service is null", "")
    this.service.getSchemaByModel(getClass).asInstanceOf[S]
  }

  /**
    * Returns a copy of this model with an updated ID and timestamp.
    *
    * @param id       ID to set
    * @param theTime  Timestamp
    * @return         Copy of model
    */
  def copyWith(id: ObjectId, theTime: ObjectTimestamp): Model

  /**
    * Returns true if this model has been processed internally by some
    * ModelService and has had it's bindings processed.
    *
    * @return True if processed
    */
  def isProcessed: Boolean = this._isProcessed

  protected[db] def setProcessed(processed: Boolean): Unit = this._isProcessed = processed

  protected def Defined[R](f: => R): R = {
    if (isDefined)
      f
    else
      throw new IllegalStateException("model must exist")
  }

}
