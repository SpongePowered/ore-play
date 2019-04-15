package ore.db

import scala.language.higherKinds

import cats._
import cats.effect.Clock

trait ModelCompanion[M] {
  type T <: profile.ModelTable[M]
  val profile: OreProfile
  import profile.api._

  def baseQuery: Query[T, Model[M], Seq]

  def asDbModel(model: M, id: ObjId[M], time: ObjTimestamp): Model[M]

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insert[F[_]: Monad: Clock](model: M)(runDBIO: DBIO ~> F): F[Model[M]]

  /**
    * Creates the specified models in it's table.
    *
    * @param models  Models to create
    * @return       Newly created models
    */
  def bulkInsert[F[_]: Clock](models: Seq[M])(runDBIO: DBIO ~> F)(implicit F: Monad[F]): F[Seq[Model[M]]]

  def update[F[_]: Monad](model: Model[M])(update: M => M)(runDBIO: DBIO ~> F): F[Model[M]]

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[F[_]: Monad](model: Model[M])(runDBIO: DBIO ~> F): F[Int]

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    */
  def deleteWhere[F[_]: Monad](filter: T => Rep[Boolean])(runDBIO: DBIO ~> F): F[Int]
}
object ModelCompanion {
  type Aux[M, T0 <: OreProfile#ModelTable[M]] = ModelCompanion[M] { type T = T0 }
}
