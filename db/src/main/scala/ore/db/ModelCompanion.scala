package ore.db

import scala.language.higherKinds

import cats._
import cats.effect.Clock

trait ModelCompanion[M] {
  type T <: profile.ModelTable[M]
  val profile: OreProfile
  import profile.api._

  def baseQuery: Query[T, Model[M], Seq]

  def asDbModel(model: M, id: ObjId[M], time: ObjOffsetDateTime): Model[M]

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insert[F[_]: Monad: Clock](model: M): F[DBIO[Model[M]]]

  /**
    * Creates the specified models in it's table.
    *
    * @param models  Models to create
    * @return       Newly created models
    */
  def bulkInsert[F[_]: Monad: Clock](models: Seq[M]): F[DBIO[Seq[Model[M]]]]

  def update[F[_]: Monad](model: Model[M])(update: M => M): F[DBIO[Model[M]]]

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete(model: Model[M]): DBIO[Int]

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    */
  def deleteWhere(filter: T => Rep[Boolean]): DBIO[Int]
}
object ModelCompanion {
  type Aux[M, T0 <: OreProfile#ModelTable[M]] = ModelCompanion[M] { type T = T0 }
}
