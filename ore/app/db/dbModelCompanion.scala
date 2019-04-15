package db

import scala.language.higherKinds

import java.sql.Timestamp
import java.util.concurrent.TimeUnit

import db.impl.OrePostgresDriver.api._
import db.table.ModelTable

import cats.effect.Clock
import cats.syntax.all._
import cats.{Functor, Monad, ~>}

trait ModelCompanion[M] {
  type T <: ModelTable[M]

  def baseQuery: Query[T, Model[M], Seq]

  def asDbModel(model: M, id: ObjId[M], time: ObjTimestamp): Model[M]

  private def timeF[F[_]: Functor](implicit clock: Clock[F]) =
    clock.realTime(TimeUnit.MILLISECONDS).map(t => ObjTimestamp(new Timestamp(t)))

  /**
    * Creates the specified model in it's table.
    *
    * @param model  Model to create
    * @return       Newly created model
    */
  def insert[F[_]: Monad: Clock](model: M)(runDBIO: DBIO ~> F): F[Model[M]] = {
    val toInsertF = timeF.map(time => asDbModel(model, new ObjId.UnsafeUninitialized, time))
    toInsertF.flatMap { toInsert =>
      runDBIO {
        baseQuery.returning(baseQuery.map(_.id)).into {
          case (m, id) => asDbModel(m, ObjId(id), m.createdAt)
        } += toInsert
      }
    }
  }

  /**
    * Creates the specified models in it's table.
    *
    * @param models  Models to create
    * @return       Newly created models
    */
  def bulkInsert[F[_]: Clock](models: Seq[M])(runDBIO: DBIO ~> F)(implicit F: Monad[F]): F[Seq[Model[M]]] =
    if (models.nonEmpty) {
      val toInsertF = timeF.map(time => models.map(asDbModel(_, new ObjId.UnsafeUninitialized, time)))

      toInsertF.flatMap { toInsert =>
        runDBIO {
          baseQuery
            .returning(baseQuery.map(_.id))
            .into((m, id) => asDbModel(m, ObjId(id), m.createdAt)) ++= toInsert
        }
      }
    } else F.pure(Nil)

  def update[F[_]: Monad](model: Model[M])(update: M => M)(runDBIO: DBIO ~> F): F[Model[M]] = {
    val updatedModel = model.copy(obj = update(model.obj))
    runDBIO(baseQuery.filter(_.id === model.id.value).update(updatedModel)).as(updatedModel)
  }

  /**
    * Deletes the specified Model.
    *
    * @param model Model to delete
    */
  def delete[F[_]: Monad](model: Model[M])(runDBIO: DBIO ~> F): F[Int] =
    deleteWhere(_.id === model.id.value)(runDBIO)

  /**
    * Deletes all the models meeting the specified filter.
    *
    * @param filter     Filter to use
    */
  def deleteWhere[F[_]: Monad](filter: T => Rep[Boolean])(runDBIO: DBIO ~> F): F[Int] =
    runDBIO(baseQuery.filter(filter).delete)
}
object ModelCompanion {
  type Aux[M, T0 <: ModelTable[M]] = ModelCompanion[M] { type T = T0 }
}

abstract class ModelCompanionPartial[M, T0 <: ModelTable[M]](val baseQuery: Query[T0, Model[M], Seq])
    extends ModelCompanion[M] {
  type T = T0
}
abstract class DefaultModelCompanion[M, T0 <: ModelTable[M]](baseQuery: Query[T0, Model[M], Seq])
    extends ModelCompanionPartial(baseQuery) {
  override def asDbModel(model: M, id: ObjId[M], time: ObjTimestamp): Model[M] = Model(id, time, model)
}
