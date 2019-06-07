package util.uiowrappers

import ore.db.{Model, ModelCompanion, ModelQuery, ModelService}

import scalaz.zio.{Task, UIO}
import slick.dbio.DBIO
import slick.lifted.Rep

class UIOModelService(underlying: ModelService[Task]) extends ModelService[UIO] {
  override def runDBIO[R](action: DBIO[R]) = underlying.runDBIO(action).orDie

  override def runDbCon[R](program: doobie.ConnectionIO[R]): UIO[R] = underlying.runDbCon(program).orDie

  override def insertRaw[M](companion: ModelCompanion[M])(model: M): UIO[Model[M]] =
    underlying.insertRaw(companion)(model).orDie

  override def bulkInsert[M](models: Seq[M])(implicit query: ModelQuery[M]): UIO[Seq[Model[M]]] =
    underlying.bulkInsert(models).orDie

  override def updateRaw[M](companion: ModelCompanion[M])(model: Model[M])(update: M => M): UIO[Model[M]] =
    underlying.updateRaw(companion)(model)(update).orDie

  override def delete[M](model: Model[M])(implicit query: ModelQuery[M]): UIO[Int] = underlying.delete(model).orDie

  override def deleteWhere[M](model: ModelCompanion[M])(filter: model.T => Rep[Boolean]): UIO[Int] =
    underlying.deleteWhere(model)(filter).orDie
}
