package ore.db

import java.sql.Timestamp
import java.util.Date

import cats.arrow.FunctionK
import cats.effect.{Clock, IO}
import cats.~>
import doobie.ConnectionIO
import slick.dbio.DBIO
import slick.lifted.Rep

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

  private val runDBIOFunc: DBIO ~> IO   = FunctionK.lift(runDBIO)
  implicit private val clock: Clock[IO] = Clock.create[IO]

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
  def insert[M](model: M)(implicit query: ModelQuery[M]): IO[Model[M]] = query.companion.insert(model)(runDBIOFunc)

  /**
    * Creates the specified models in it's table.
    *
    * @param models  Models to create
    * @return       Newly created models
    */
  def bulkInsert[M](models: Seq[M])(implicit query: ModelQuery[M]): IO[Seq[Model[M]]] =
    query.companion.bulkInsert(models)(runDBIOFunc)

  def update[M](model: Model[M])(update: M => M)(implicit query: ModelQuery[M]): IO[Model[M]] =
    query.companion.update(model)(update)(runDBIOFunc)

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[M](model: Model[M])(implicit query: ModelQuery[M]): IO[Int] = query.companion.delete(model)(runDBIOFunc)

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    * @tparam M         Model
    */
  def deleteWhere[M](model: ModelCompanion[M])(filter: model.T => Rep[Boolean]): IO[Int] =
    model.deleteWhere(filter)(runDBIOFunc)
}
