package db

import java.sql.Timestamp
import java.util.Date

import scala.concurrent.duration.Duration

import db.ModelFilter._
import db.access.ModelAccess
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import db.table.ModelTable

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._
import doobie.ConnectionIO
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.ColumnOrdered

/**
  * Represents a service that creates, deletes, and manipulates Models.
  */
abstract class ModelService(val driver: JdbcProfile) {
  import driver.api._

  /**
    * The default timeout when awaiting a query result.
    */
  def DefaultTimeout: Duration

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

  def userBase: UserBase

  def projectBase: ProjectBase

  def organizationBase: OrganizationBase

  /**
    * Returns the base query for the specified Model class.
    *
    * @tparam M         Model type
    * @return           Base query for Model
    */
  def newAction[M <: Model](implicit query: ModelQuery[M]): Query[M#T, M, Seq] = query.baseQuery

  /**
    * Runs the specified DBIO on the DB.
    *
    * @param action   Action to run
    * @return         Result
    */
  def runDBIO[R](action: DBIO[R]): IO[R]

  /**
    * Runs the specified db program on the DB.
    *
    * @param action   Action to run
    * @return         Result
    */
  def runDbCon[R](program: ConnectionIO[R]): IO[R]

  /**
    * Returns a new ModelAccess to access a ModelTable synchronously.
    *
    * @param baseFilter Base filter to apply
    * @tparam M0         Model
    * @return           New ModelAccess
    */
  def access[M0 <: Model { type M = M0 }: ModelQuery](baseFilter: M0#T => Rep[Boolean] = All[M0]) =
    new ModelAccess[M0](this, baseFilter)

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insert[M <: Model](model: M)(implicit query: ModelQuery[M]): IO[M] = {
    val toInsert = query.copyWith(model)(ObjId.Uninitialized(), ObjectTimestamp(theTime))
    val models   = newAction
    runDBIO {
      models.returning(models.map(_.id)).into {
        case (m, id) => query.copyWith(m)(ObjId(id), m.createdAt)
      } += toInsert
    }
  }

  /**
    * Creates the specified models in it's table.
    *
    * @param models  Models to create
    * @return       Newly created models
    */
  def bulkInsert[M <: Model](models: Seq[M])(implicit query: ModelQuery[M]): IO[Seq[M]] =
    if (models.nonEmpty) {
      val toInsert = models.map(query.copyWith(_)(ObjId.Uninitialized(), ObjectTimestamp(theTime)))
      val action   = newAction[M]
      runDBIO {
        action
          .returning(action.map(_.id))
          .into((m, id) => query.copyWith(m)(ObjId(id), m.createdAt)) ++= toInsert
      }
    } else IO.pure(Nil)

  def update[M0 <: Model { type M = M0 }: ModelQuery](model: M0): IO[M0] =
    runDBIO(newAction.filter(IdFilter(model.id.value)).update(model)).as(model)

  def updateIfDefined[M0 <: Model { type M = M0 }: ModelQuery](model: M0): IO[M0] =
    if (model.isDefined) update(model) else IO.pure(model)

  /**
    * Sets a column in a [[ModelTable]].
    *
    * @param model  Model to update
    * @param column Column to update
    * @param value  Value to set
    * @param mapper JdbcType
    * @tparam A     Value type
    * @tparam M0     Model type
    */
  def set[A, M0 <: Model { type M = M0 }: ModelQuery](model: M0, column: M0#T => Rep[A], value: A)(
      implicit mapper: JdbcType[A]
  ): IO[Int] = runDBIO(newAction.filter(IdFilter(model.id.value)).map(column(_)).update(value))

  /**
    * Returns the first model that matches the given predicate.
    *
    * @param filter  Filter
    * @return        Optional result
    */
  def find[M <: Model: ModelQuery](filter: M#T => Rep[Boolean]): OptionT[IO, M] =
    OptionT(runDBIO(newAction.filter(filter).take(1).result).map(_.headOption))

  /**
    * Returns the size of the model table.
    *
    * @return Size of model table
    */
  def count[M <: Model: ModelQuery](filter: M#T => Rep[Boolean] = All): IO[Int] =
    runDBIO(newAction.filter(filter).length.result)

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[M0 <: Model { type M = M0 }: ModelQuery](model: M0): IO[Int] =
    deleteWhere[M0](IdFilter(model.id.value))

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    * @tparam M         Model
    */
  def deleteWhere[M <: Model: ModelQuery](filter: M#T => Rep[Boolean]): IO[Int] =
    runDBIO(newAction.filter(filter).delete)

  /**
    * Returns the model with the specified ID, if any.
    *
    * @param id   Model with ID
    * @return     Model if present, None otherwise
    */
  def get[M0 <: Model { type M = M0 }: ModelQuery](id: DbRef[M0], filter: M0#T => Rep[Boolean] = All): OptionT[IO, M0] =
    find(IdFilter[M0](id) && filter)

  /**
    * Returns a sequence of Model's that have an ID in the specified Set.
    *
    * @param ids        ID set
    * @param filter     Additional filter
    * @tparam M0         Model type
    * @return           Seq of models in ID set
    */
  def in[M0 <: Model { type M = M0 }: ModelQuery](
      ids: Set[DbRef[M0]],
      filter: M0#T => Rep[Boolean] = All
  ): IO[Seq[M0]] = this.filter(ModelFilter[M0](_.id.inSetBind(ids)) && filter)

  /**
    * Returns a collection of models with the specified limit and offset.
    *
    * @param limit  Amount of models to take
    * @param offset Offset to drop
    * @return       Collection of models
    */
  def collect[M <: Model: ModelQuery](
      filter: M#T => Rep[Boolean] = All,
      sort: M#T => ColumnOrdered[_] = null,
      limit: Int = -1,
      offset: Int = -1
  ): IO[Seq[M]] = {
    var query = newAction.filter(filter)
    if (sort != null) query = query.sortBy(sort)
    if (offset > -1) query = query.drop(offset)
    if (limit > -1) query = query.take(limit)
    runDBIO(query.result)
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
  ): IO[Seq[M]] = collect(filter, null.asInstanceOf[M#T => ColumnOrdered[_]], limit, offset)

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
      filter: M#T => Rep[Boolean] = All,
      limit: Int = -1,
      offset: Int = -1
  ): IO[Seq[M]] = collect(filter, sort, limit, offset)
}
