package db

import java.sql.Timestamp
import java.util.Date

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

import db.ModelFilter.IdFilter
import db.access.{ModelAccess, ModelAssociationAccess}
import db.table.{AssociativeTable, ModelTable}

import cats.data.OptionT
import cats.instances.future._
import cats.syntax.all._
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.ColumnOrdered

/**
  * Represents a service that creates, deletes, and manipulates Models.
  */
abstract class ModelService(val driver: JdbcProfile) {
  import driver.api._

  /** All registered models. */
  def registry: ModelRegistry

  /**
    * The default timeout when awaiting a query result.
    */
  def DefaultTimeout: Duration

  /**
    * The database config for raw actions. Note: running raw queries will not
    * process any returned models and should be used only for model "meta-data"
    * (e.g. Project stars).
    */
  def DB: DatabaseConfig[JdbcProfile]

  /**
    * Performs initialization code for the ModelService.
    */
  def start(): Unit

  /**
    * Returns a current Timestamp.
    *
    * @return Timestamp of now
    */
  def theTime: Timestamp = new Timestamp(new Date().getTime)

  /**
    * Awaits the result of the specified future and returns the result.
    *
    * @param f        Future to await
    * @param timeout  Timeout duration
    * @tparam M       Return type
    * @return         Try of return type
    */
  def await[M](f: Future[M], timeout: Duration = DefaultTimeout): Try[M] = Await.ready(f, timeout).value.get

  /**
    * Returns the specified [[ModelBase]].
    *
    * @param clazz  ModelBase class
    * @tparam B     ModelBase type
    * @return       ModelBase
    */
  def getModelBase[B <: ModelBase[_]](clazz: Class[B]): B = this.registry.getModelBase(clazz)

  /**
    * Returns the base query for the specified Model class.
    *
    * @tparam M         Model type
    * @return           Base query for Model
    */
  def newAction[M <: Model](implicit query: ModelQuery[M]): Query[M#T, M, Seq] = query.baseQuery

  /**
    * Runs the specified ModelAction on the DB and processes the resulting
    * model(s).
    *
    * @param action   Action to run
    * @return         Processed result
    */
  def doAction[R](action: DBIO[R]): Future[R] = DB.db.run(action)

  /**
    * Returns a new ModelAccess to access a ModelTable synchronously.
    *
    * @param baseFilter Base filter to apply
    * @tparam M         Model
    * @return           New ModelAccess
    */
  def access[M <: Model: ModelQuery](baseFilter: ModelFilter[M] = ModelFilter[M]()) =
    new ModelAccess[M](this, baseFilter)

  def associationAccess[Assoc <: AssociativeTable, P <: Model, C <: Model: ModelQuery](
      p: P
  )(implicit query: AssociationQuery[Assoc, P, C]): ModelAssociationAccess[Assoc, P, C] =
    new ModelAssociationAccess[Assoc, P, C](this, p)

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insert[M <: Model: ModelQuery](model: M): Future[M] = {
    val toInsert = model.copyWith(ObjectId.Uninitialized, ObjectTimestamp(theTime)).asInstanceOf[M]
    val models   = newAction[M]
    doAction {
      models.returning(models.map(_.id)).into {
        case (m, id) => m.copyWith(ObjectId(id), m.createdAt).asInstanceOf[M]
      } += toInsert
    }
  }

  def update[M <: Model: ModelQuery](model: M)(implicit ec: ExecutionContext): Future[M] = {
    val models = newAction[M]
    doAction(models.filter(_.id === model.id.value).update(model)).as(model)
  }

  def updateIfDefined[M <: Model](model: M)(implicit ec: ExecutionContext): Future[M] =
    if (model.isDefined) update(model) else Future.successful(model)

  /**
    * Sets a column in a [[ModelTable]].
    *
    * @param model  Model to update
    * @param column Column to update
    * @param value  Value to set
    * @param mapper JdbcType
    * @tparam A     Value type
    * @tparam M     Model type
    */
  def set[A, M <: Model: ModelQuery](model: M, column: M#T => Rep[A], value: A)(
      implicit mapper: JdbcType[A]
  ): Future[Int] = {
    doAction {
      (for {
        row <- newAction[M]
        if row.id === model.id.value
      } yield column(row)).update(value)
    }
  }

  /**
    * Returns the first model that matches the given predicate.
    *
    * @param filter  Filter
    * @return        Optional result
    */
  def find[M <: Model: ModelQuery](filter: M#T => Rep[Boolean])(
      implicit ec: ExecutionContext
  ): OptionT[Future, M] = {
    val modelPromise = Promise[Option[M]]
    val query        = newAction[M].filter(filter).take(1)
    doAction(query.result).andThen {
      case Failure(thrown) => modelPromise.failure(thrown)
      case Success(result) => modelPromise.success(result.headOption)
    }
    OptionT(modelPromise.future)
  }

  /**
    * Returns the size of the model table.
    *
    * @return Size of model table
    */
  def count[M <: Model: ModelQuery](filter: M#T => Rep[Boolean] = null): Future[Int] = {
    var query = newAction[M]
    if (filter != null) query = query.filter(filter)
    doAction(query.length.result)
  }

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[M <: Model: ModelQuery](model: M): Future[Int] =
    doAction(newAction[M].filter(IdFilter[M](model.id.value).fn).delete)

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    * @tparam M         Model
    */
  def deleteWhere[M <: Model: ModelQuery](filter: M#T => Rep[Boolean]): Future[Int] =
    doAction(newAction[M].filter(filter).delete)

  /**
    * Returns the model with the specified ID, if any.
    *
    * @param id   Model with ID
    * @return     Model if present, None otherwise
    */
  def get[M <: Model: ModelQuery](id: ObjectReference, filter: M#T => Rep[Boolean] = null)(
      implicit ec: ExecutionContext
  ): OptionT[Future, M] = find[M]((IdFilter[M](id) && filter).fn)

  /**
    * Returns a sequence of Model's that have an ID in the specified Set.
    *
    * @param ids        ID set
    * @param filter     Additional filter
    * @tparam M         Model type
    * @return           Seq of models in ID set
    */
  def in[M <: Model: ModelQuery](
      ids: Set[ObjectReference],
      filter: M#T => Rep[Boolean] = null
  ): Future[Seq[M]] = this.filter[M]((ModelFilter[M](_.id.inSetBind(ids)) && filter).fn)

  /**
    * Returns a collection of models with the specified limit and offset.
    *
    * @param limit  Amount of models to take
    * @param offset Offset to drop
    * @return       Collection of models
    */
  def collect[M <: Model: ModelQuery](
      filter: M#T => Rep[Boolean] = null,
      sort: M#T => ColumnOrdered[_] = null,
      limit: Int = -1,
      offset: Int = -1
  ): Future[Seq[M]] = {
    var query = newAction[M]
    if (filter != null) query = query.filter(filter)
    if (sort != null) query = query.sortBy(sort)
    if (offset > -1) query = query.drop(offset)
    if (limit > -1) query = query.take(limit)
    doAction(query.result)
  }

  /**
    * Filters the the models.
    *
    * @param filter Model filter
    * @param limit  Amount to take
    * @param offset Amount to drop
    * @tparam M     Model
    * @return       Filtered models
    */
  def filter[M <: Model: ModelQuery](
      filter: M#T => Rep[Boolean],
      limit: Int = -1,
      offset: Int = -1
  ): Future[Seq[M]] = collect(filter, null.asInstanceOf[M#T => ColumnOrdered[_]], limit, offset)

  /**
    * Sorts the models by the specified ColumnOrdered.
    *
    * @param sort       Ordering
    * @param limit      Amount to take
    * @param offset     Amount to drop
    * @tparam M         Model
    * @return           Sorted models
    */
  def sorted[M <: Model: ModelQuery](
      sort: M#T => ColumnOrdered[_],
      filter: M#T => Rep[Boolean] = null,
      limit: Int = -1,
      offset: Int = -1
  ): Future[Seq[M]] = collect(filter, sort, limit, offset)

}
