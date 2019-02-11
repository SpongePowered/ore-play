package db

import java.sql.Timestamp
import java.util.Date

import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import db.impl.OrePostgresDriver.api._

import cats.effect.IO
import cats.syntax.all._
import doobie.ConnectionIO

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
  def insert[M](model: M)(implicit query: ModelQuery[M]): IO[DbModel[M]] = {
    val toInsert = query.asDbModel(model)(new ObjId.UnsafeUninitialized, ObjTimestamp(theTime))
    val models   = query.baseQuery
    runDBIO {
      models.returning(models.map(_.id)).into {
        case (m, id) => query.asDbModel(m)(ObjId(id), m.createdAt)
      } += toInsert
    }
  }

  /**
    * Creates the specified models in it's table.
    *
    * @param models  Models to create
    * @return       Newly created models
    */
  def bulkInsert[M](models: Seq[M])(implicit query: ModelQuery[M]): IO[Seq[DbModel[M]]] =
    if (models.nonEmpty) {
      val toInsert = models.map(query.asDbModel(_)(new ObjId.UnsafeUninitialized, ObjTimestamp(theTime)))
      val action   = query.baseQuery
      runDBIO {
        action
          .returning(action.map(_.id))
          .into((m, id) => query.asDbModel(m)(ObjId(id), m.createdAt)) ++= toInsert
      }
    } else IO.pure(Nil)

  def update[M](model: DbModel[M])(update: M => M)(implicit query: ModelQuery[M]): IO[DbModel[M]] = {
    val updatedModel = model.copy(obj = update(model.obj))
    runDBIO(query.baseQuery.filter(_.id === model.id.value).update(updatedModel)).as(updatedModel)
  }

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[M](model: DbModel[M])(implicit query: ModelQuery[M]): IO[Int] =
    deleteWhere(query.companion)(_.id === model.id.value)

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    * @tparam M         Model
    */
  def deleteWhere[M](model: DbModelCompanion[M])(filter: model.T => Rep[Boolean]): IO[Int] =
    runDBIO(model.baseQuery.filter(filter).delete)
}
