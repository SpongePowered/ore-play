package db

import scala.collection.JavaConverters._

import com.google.common.base.Preconditions.checkNotNull
import com.google.common.collect.{BiMap, HashBiMap}

/**
  * A registry for [[Model]] information such as [[ModelSchema]]s or
  * [[ModelBase]]s.
  */
trait ModelRegistry {

  val modelSchemas: BiMap[Class[_ <: Model], ModelSchema[_]]  = HashBiMap.create()
  var modelBases: Map[Class[_ <: ModelBase[_]], ModelBase[_]] = Map.empty

  /**
    * Registers a new [[ModelSchema]].
    *
    * TODO: Model's need to be able to have multiple schemas (i.e. "projects"
    * and "projects_deleted" tables)
    *
    * @param schema ModelSchema to register
    * @tparam S Schema type
    * @return Registered schema
    */
  //noinspection ComparingUnrelatedTypes
  def registerSchema[S <: ModelSchema[_ <: Model]](schema: S): S = {
    checkNotNull(schema, "schema is null", "")
    this.modelSchemas.put(schema.modelClass, schema)
    schema
  }

  /**
    * Finds a [[ModelSchema]] instance by the schema class.
    *
    * @param schemaClass  ModelSchema class
    * @tparam S           Actions type
    * @return             ModelSchema
    */
  //noinspection ComparingUnrelatedTypes
  def getSchema[S <: ModelSchema[_]](schemaClass: Class[S]): S = {
    checkNotNull(schemaClass, "schema class is null", "")
    this.modelSchemas.asScala
      .find(_._2.getClass.equals(schemaClass))
      .getOrElse(throw new RuntimeException("schema not found of type " + schemaClass))
      ._2
      .asInstanceOf[S]
  }

  /**
    * Returns a registered [[ModelSchema]] for the specified Model class.
    *
    * @param modelClass Model class
    * @tparam M         Model type
    * @return           ModelSchema of Model
    */
  def getSchemaByModel[M <: Model](modelClass: Class[_ <: M]): ModelSchema[M] = {
    checkNotNull(modelClass, "schema class is null", "")
    this.modelSchemas.asScala
      .find(_._1.isAssignableFrom(modelClass))
      .getOrElse(throw new RuntimeException("schema not found for model " + modelClass))
      ._2
      .asInstanceOf[ModelSchema[M]]
  }

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
