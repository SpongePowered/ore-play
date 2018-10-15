package db

import com.google.common.base.Preconditions.checkNotNull

/**
  * A registry for [[Model]] information such as [[ModelBase]]s.
  */
trait ModelRegistry {

  var modelBases: Map[Class[_ <: ModelBase[_]], ModelBase[_]] = Map.empty

  /**
    * Registers a new [[ModelBase]] with the service.
    *
    * @param base ModelBase
    */
  def registerModelBase(base: ModelBase[_ <: Model]): Unit = {
    checkNotNull(base, "model base is null", "")
    this.modelBases += base.getClass -> base
  }

  /**
    * Returns a registered [[ModelBase]] by class.
    *
    * @param clazz  ModelBase class
    * @tparam B     ModelBase type
    * @return       ModelBase of class
    */
  def getModelBase[B <: ModelBase[_]](clazz: Class[B]): B = {
    checkNotNull(clazz, "model class is null", "")
    this.modelBases
      .getOrElse(clazz, throw new RuntimeException("model base not found for class " + clazz))
      .asInstanceOf[B]
  }

}
