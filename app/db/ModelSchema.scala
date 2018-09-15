package db

import db.access.{ImmutableModelAccess, ModelAccess, ModelAssociationAccess}
import db.impl.OrePostgresDriver.api._
import db.table.{AssociativeTable, ModelAssociation, ModelTable}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

import util.functional.OptionT
import util.instances.future._

/**
  * Defines a set of [[Model]] behaviors such as relationships between other
  * Models or any other specialized database actions. Every [[Model]] has
  * exactly one [[ModelSchema]].
  *
  * @param service ModelService instance
  * @param modelClass Model class
  * @param baseQuery Model table [[TableQuery]] instance
  * @tparam M Model type
  */
class ModelSchema[M <: Model](val service: ModelService,
                              val modelClass: Class[M],
                              val baseQuery: TableQuery[_ <: M#T]) {

  private case class Associate[T <: AssociativeTable, A <: Model](tableClass: Class[T], modelClass: Class[A])

  private var associations: Map[Class[_ <: AssociativeTable], ModelAssociation[_]] = Map.empty
  private var associatedModels: Map[Class[_ <: AssociativeTable], Class[_ <: Model]] = Map.empty
  private var associativeSelfReferences: Map[Class[_ <: AssociativeTable], AssociativeTable => Rep[Int]] = Map.empty
  private var associativeOtherReferences: Map[Associate[_, _], AssociativeTable => Rep[Int]] = Map.empty

  private var children: Map[Class[_ <: Model], ModelTable[_] => Rep[Int]] = Map.empty
  private var siblings: Map[Class[_ <: Model], M => Int] = Map.empty

  /**
    * Adds a new [[ModelAssociation]] to this schema and defines a
    * many-to-many relationship between the schema's model and the specified
    * target [[Model]].
    *
    * @param association      ModelAssociation between the two models
    * @param selfReference    Reference to schema model in [[AssociativeTable]]
    * @param targetClass      Model to target
    * @param targetReference  Reference to target model in AssociativeTable
    * @tparam Assoc           AssociativeTable type
    * @tparam A               Model type
    * @return                 This schema instance
    */
  def withAssociation[Assoc <: AssociativeTable, A <: Model](association: ModelAssociation[Assoc],
                                                             selfReference: Assoc => Rep[Int],
                                                             targetClass: Class[A],
                                                             targetReference: Assoc => Rep[Int]): ModelSchema[M] = {
    val tableClass = association.tableClass
    this.associations += tableClass -> association
    this.associatedModels += tableClass -> targetClass
    this.associativeSelfReferences += tableClass -> selfReference.asInstanceOf[AssociativeTable => Rep[Int]]
    this.associativeOtherReferences += Associate[Assoc, A](tableClass, targetClass) ->
      targetReference.asInstanceOf[AssociativeTable => Rep[Int]]
    this
  }

  /**
    * Returns a new [[ModelAssociationAccess]] instance for the association
    * defined by the [[AssociativeTable]].
    *
    * @param assocTableClass  AssociativeTable class
    * @param model            Parent model
    * @tparam Assoc           AssociativeTable type
    * @tparam A               Model type
    * @return                 This schema instance
    */
  def getAssociation[Assoc <: AssociativeTable, A <: Model](assocTableClass: Class[Assoc],
                                                            model: M): ModelAssociationAccess[Assoc, A] = {
    val parentRef: AssociativeTable => Rep[Int] = this.associativeSelfReferences(assocTableClass)
    val otherClass: Class[A] = this.associatedModels(assocTableClass).asInstanceOf[Class[A]]
    val otherRef: AssociativeTable => Rep[Int] = this.associativeOtherReferences(Associate[Assoc, A](
      assocTableClass, otherClass))
    val association = this.associations(assocTableClass).asInstanceOf[ModelAssociation[Assoc]]
    new ModelAssociationAccess[Assoc, A](this.service, model, parentRef, otherClass, otherRef, association)
  }

  /**
    * Adds a one-to-many relationship to this schema for the specified child
    * [[Model]] class.
    *
    * @param childClass Child model class
    * @param ref        Reference to parent ID in child table
    * @tparam C         Child model type
    * @return           This schema instance
    */
  def withChildren[C <: Model](childClass: Class[C], ref: C#T => Rep[Int]): ModelSchema[M] = {
    this.children += childClass -> ref.asInstanceOf[ModelTable[_] => Rep[Int]]
    this
  }

  /**
    * Returns a new [[ModelAccess]] instance for the specified child [[Model]]
    * class. The returned ModelAccess will filter out any non-children but is
    * still referencing the original child Model's table. For this reason, the
    * returned ModelAccess is read-only.
    *
    * @param childClass Child Model class
    * @param model      Parent model
    * @tparam C         Child model class
    * @return           This schema instance
    */
  def getChildren[C <: Model](childClass: Class[C], model: M): ModelAccess[C] = {
    val ref: C#T => Rep[Int] = this.children(childClass)
    ImmutableModelAccess(this.service.access[C](childClass, ModelFilter[C](ref(_) === model.id.value)))
  }

  /**
    * Adds a one-to-one relationship to this schema for the specified child
    * [[Model]] class.
    *
    * @param siblingClass Sibling model class
    * @param ref          Reference to sibling model ID in original model
    * @tparam S           Sibling model type
    * @return             This schema instance
    */
  def withSibling[S <: Model](siblingClass: Class[S], ref: M => Int): ModelSchema[M] = {
    this.siblings += siblingClass -> ref
    this
  }

  /**
    * Retrieves a sibling [[Model]] of the specified type.
    *
    * @param siblingClass Sibling model class
    * @param model        Original model
    * @tparam S           Sibling model type
    * @return             Sibling
    */
  def getSibling[S <: Model](siblingClass: Class[S], model: M)(implicit ec: ExecutionContext): OptionT[Future, S] = {
    val ref: M => Int = this.siblings(siblingClass)
    this.service.get[S](siblingClass, ref(model))
  }

  /**
    * Returns the specified model or creates it if it doesn't exist.
    *
    * @param model  Model to get or create
    * @return       Existing or newly created model
    */
  def getOrInsert(model: M)(implicit ec: ExecutionContext): Future[M] = {
    val modelPromise = Promise[M]
    like(model).value.onComplete {
      case Failure(thrown) => modelPromise.failure(thrown)
      case Success(modelOpt) => modelOpt match {
        case Some(existing) => modelPromise.success(existing)
        case None => modelPromise.completeWith(service insert model)
      }
    }
    modelPromise.future
  }

  /**
    * Tries to find the specified model in it's table with an unset ID.
    *
    * @param model  Model to find
    * @return       Model if found
    */
  def like(model: M)(implicit ec: ExecutionContext): OptionT[Future, M] = OptionT.none[Future, M]

}
