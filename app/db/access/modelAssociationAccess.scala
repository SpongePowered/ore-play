package db.access

import scala.language.higherKinds

import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable
import db.{AssociationQuery, DbModel, DbModelCompanion, ModelService}

import cats.effect.IO
import cats.syntax.all._

trait ModelAssociationAccess[Assoc <: AssociativeTable[P, C], P, C, F[_]] {

  protected val pComanion: DbModelCompanion[P]
  protected val cComanion: DbModelCompanion[C]

  def addAssoc(parent: DbModel[P], child: DbModel[C]): F[Unit]

  def removeAssoc(parent: DbModel[P], child: DbModel[C]): F[Unit]

  def contains(parent: DbModel[P], child: DbModel[C]): F[Boolean]

  def deleteAllFromParent(parent: DbModel[P]): F[Unit]

  def deleteAllFromChild(child: DbModel[C]): F[Unit]

  def allQueryFromParent(parent: DbModel[P]): Query[cComanion.T, DbModel[C], Seq]

  def allFromParent(parent: DbModel[P]): F[Seq[DbModel[C]]]

  def allQueryFromChild(child: DbModel[C]): Query[pComanion.T, DbModel[P], Seq]

  def allFromChild(child: DbModel[C]): F[Seq[DbModel[P]]]
}

class ModelAssociationAccessImpl[
    Assoc <: AssociativeTable[P, C],
    P,
    C
](protected val pComanion: DbModelCompanion[P], protected val cCompanion: DbModelCompanion[C])(
    implicit
    query: AssociationQuery[Assoc, P, C],
    service: ModelService
) extends ModelAssociationAccess[Assoc, P, C, IO] {

  def addAssoc(parent: DbModel[P], child: DbModel[C]): IO[Unit] =
    service.runDBIO(query.baseQuery += ((parent.id, child.id))).void

  def removeAssoc(parent: DbModel[P], child: DbModel[C]): IO[Unit] =
    service
      .runDBIO(
        query.baseQuery
          .filter(t => query.parentRef(t) === parent.id.value && query.childRef(t) === child.id.value)
          .delete
      )
      .void

  def contains(parent: DbModel[P], child: DbModel[C]): IO[Boolean] = service.runDBIO(
    (query.baseQuery
      .filter(t => query.parentRef(t) === parent.id.value && query.childRef(t) === child.id.value)
      .length > 0).result
  )

  override def deleteAllFromParent(parent: DbModel[P]): IO[Unit] =
    service.runDBIO(query.baseQuery.filter(query.parentRef(_) === parent.id.value).delete).void

  override def deleteAllFromChild(child: DbModel[C]): IO[Unit] =
    service.runDBIO(query.baseQuery.filter(query.childRef(_) === child.id.value).delete).void

  override def allQueryFromParent(parent: DbModel[P]): Query[cCompanion.T, DbModel[C], Seq] =
    for {
      assoc <- query.baseQuery if query.parentRef(assoc) === parent.id.value
      child <- cCompanion.baseQuery if query.childRef(assoc) === child.id
    } yield child

  def allFromParent(parent: DbModel[P]): IO[Seq[DbModel[C]]] = service.runDBIO(allQueryFromParent(parent).result)

  override def allQueryFromChild(child: DbModel[C]): Query[pComanion.T, DbModel[P], Seq] =
    for {
      assoc  <- query.baseQuery if query.childRef(assoc) === child.id.value
      parent <- pComanion.baseQuery if query.parentRef(assoc) === parent.id
    } yield parent

  def allFromChild(child: DbModel[C]): IO[Seq[DbModel[P]]] = service.runDBIO(allQueryFromChild(child).result)
}
