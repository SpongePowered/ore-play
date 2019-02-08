package db

import java.sql.Timestamp
import java.util.Date

import db.ModelFilter._
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import db.impl.OrePostgresDriver.api._

import cats.effect.IO
import cats.syntax.all._
import doobie.ConnectionIO
import slick.lifted.ColumnOrdered

/**
  * Represents a service that creates, deletes, and manipulates Models.
  */
abstract class ModelService {

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
    * @param program  Action to run
    * @return         Result
    */
  def runDbCon[R](program: ConnectionIO[R]): IO[R]

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insert[M <: Model](model: InsertFunc[M])(implicit query: ModelQuery[M]): IO[M] = {
    val toInsert = model(new ObjId.UnsafeUninitialized, ObjTimestamp(theTime))
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
  def bulkInsert[M <: Model](models: Seq[InsertFunc[M]])(implicit query: ModelQuery[M]): IO[Seq[M]] =
    if (models.nonEmpty) {
      val toInsert = models.map(_(new ObjId.UnsafeUninitialized, ObjTimestamp(theTime)))
      val action   = newAction[M]
      runDBIO {
        action
          .returning(action.map(_.id))
          .into((m, id) => query.copyWith(m)(ObjId(id), m.createdAt)) ++= toInsert
      }
    } else IO.pure(Nil)

  def update[M0 <: Model { type M = M0 }: ModelQuery](model: M0): IO[M0] =
    runDBIO(newAction.filter(IdFilter(model.id)).update(model)).as(model)

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[M0 <: Model { type M = M0 }: ModelQuery](model: M0): IO[Int] =
    deleteWhere[M0](IdFilter(model.id))

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    * @tparam M         Model
    */
  def deleteWhere[M <: Model: ModelQuery](filter: M#T => Rep[Boolean]): IO[Int] =
    runDBIO(newAction.filter(filter).delete)

  /**
    * Returns a collection of models with the specified limit and offset .
    *
    * @param limit  Amount of models to take
    * @param offset Offset to drop
    * @return       Collection of models
    */
  def collect[M <: Model: ModelQuery](
      filter: M#T => Rep[Boolean] = All,
      sort: Option[M#T => ColumnOrdered[_]] = None,
      limit: Int = -1,
      offset: Int = -1
  ): Query[M#T, M, Seq] = {
    type Q = Query[M#T, M, Seq]
    val addSort   = (query: Q) => sort.fold(query)(sort => query.sortBy(sort))
    val addOffset = (query: Q) => if (offset > -1) query.drop(offset) else query
    val addLimit  = (query: Q) => if (limit > -1) query.take(limit) else query

    Seq(addSort, addOffset, addLimit).foldLeft(newAction.filter(filter))((q, f) => f(q))
  }
}
