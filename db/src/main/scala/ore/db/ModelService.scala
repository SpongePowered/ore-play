package ore.db

import scala.language.higherKinds

import doobie.ConnectionIO
import slick.dbio.DBIO
import slick.lifted.Rep
import cats.tagless._
import cats.~>

/**
  * Represents a service that creates, deletes, and manipulates Models.
  */
trait ModelService[+F[_]] {

  /**
    * Runs the specified DBIO on the DB.
    *
    * @param action   Action to run
    * @return         Result
    */
  def runDBIO[R](action: DBIO[R]): F[R]

  /**
    * Runs the specified db program on the DB.
    *
    * @param program  Action to run
    * @return         Result
    */
  def runDbCon[R](program: ConnectionIO[R]): F[R]

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insertRaw[M](companion: ModelCompanion[M])(model: M): F[Model[M]]

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insert[M](model: M)(implicit query: ModelQuery[M]): F[Model[M]] = insertRaw(query.companion)(model)

  /**
    * Creates the specified models in it's table.
    *
    * @param models  Models to create
    * @return       Newly created models
    */
  def bulkInsert[M](models: Seq[M])(implicit query: ModelQuery[M]): F[Seq[Model[M]]]

  /**
    * Updates the model in the database to be equal to the value created in the update function.
    * @param model The model to update
    * @param update An function to update the inner model
    * @return The updated model
    */
  def updateRaw[M](companion: ModelCompanion[M])(model: Model[M])(update: M => M): F[Model[M]]

  /**
    * Updates the model in the database to be equal to the value created in the update function.
    * @param model The model to update
    * @param update An function to update the inner model
    * @return The updated model
    */
  def update[M](model: Model[M])(update: M => M)(implicit query: ModelQuery[M]): F[Model[M]] =
    updateRaw(query.companion)(model)(update)

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[M](model: Model[M])(implicit query: ModelQuery[M]): F[Int]

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    * @tparam M         Model
    */
  def deleteWhere[M](model: ModelCompanion[M])(filter: model.T => Rep[Boolean]): F[Int]
}
object ModelService {
  def apply[F[_]](implicit service: ModelService[F]): ModelService[F] = service

  implicit val functorK: FunctorK[ModelService] = new FunctorK[ModelService] {
    override def mapK[F[_], G[_]](af: ModelService[F])(fk: F ~> G): ModelService[G] = new ModelService[G] {
      override def runDBIO[R](action: DBIO[R]) =
        fk(af.runDBIO(action))

      override def runDbCon[R](program: ConnectionIO[R]): G[R] =
        fk(af.runDbCon(program))

      override def insertRaw[M](companion: ModelCompanion[M])(model: M): G[Model[M]] =
        fk(af.insertRaw(companion)(model))

      override def bulkInsert[M](models: Seq[M])(implicit query: ModelQuery[M]): G[Seq[Model[M]]] =
        fk(af.bulkInsert(models))

      override def updateRaw[M](companion: ModelCompanion[M])(model: Model[M])(update: M => M): G[Model[M]] =
        fk(af.updateRaw(companion)(model)(update))

      override def delete[M](model: Model[M])(implicit query: ModelQuery[M]): G[Int] =
        fk(af.delete(model))

      override def deleteWhere[M](model: ModelCompanion[M])(filter: model.T => Rep[Boolean]): G[Int] =
        fk(af.deleteWhere(model)(filter))
    }
  }
}
