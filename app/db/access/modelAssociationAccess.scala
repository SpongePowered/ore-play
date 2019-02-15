package db.access

import scala.language.higherKinds

import db.impl.OrePostgresDriver.api._
import db.table.{AssociativeTable, ModelTable}
import db.{AssociationQuery, DbModel, DbModelCompanion, ModelService}

import cats.effect.IO
import cats.syntax.all._

trait ModelAssociationAccess[Assoc <: AssociativeTable[P, C], P, C, PT <: ModelTable[P], CT <: ModelTable[C], F[_]] {

  def addAssoc(parent: DbModel[P], child: DbModel[C]): F[Unit]

  def removeAssoc(parent: DbModel[P], child: DbModel[C]): F[Unit]

  def contains(parent: DbModel[P], child: DbModel[C]): F[Boolean]

  def deleteAllFromParent(parent: DbModel[P]): F[Unit]

  def deleteAllFromChild(child: DbModel[C]): F[Unit]

  def allQueryFromParent(parent: DbModel[P]): Query[CT, DbModel[C], Seq]

  def allFromParent(parent: DbModel[P]): F[Seq[DbModel[C]]]

  def allQueryFromChild(child: DbModel[C]): Query[PT, DbModel[P], Seq]

  def allFromChild(child: DbModel[C]): F[Seq[DbModel[P]]]

  def applyChild(child: DbModel[C]): ChildAssociationAccess[Assoc, P, C, PT, CT, F] =
    new ChildAssociationAccess(child, this)
  def applyParent(parent: DbModel[P]): ParentAssociationAccess[Assoc, P, C, PT, CT, F] =
    new ParentAssociationAccess(parent, this)
}

class ParentAssociationAccess[Assoc <: AssociativeTable[P, C], P, C, PT <: ModelTable[P], CT <: ModelTable[C], F[_]](
    parent: DbModel[P],
    val base: ModelAssociationAccess[Assoc, P, C, PT, CT, F]
) {

  def addAssoc(child: DbModel[C]): F[Unit] = base.addAssoc(parent, child)

  def removeAssoc(child: DbModel[C]): F[Unit] = base.removeAssoc(parent, child)

  def contains(child: DbModel[C]): F[Boolean] = base.contains(parent, child)

  def deleteAllFromParent: F[Unit] = base.deleteAllFromParent(parent)

  def allQueryFromParent: Query[CT, DbModel[C], Seq] = base.allQueryFromParent(parent)

  def allFromParent: F[Seq[DbModel[C]]] = base.allFromParent(parent)
}

class ChildAssociationAccess[Assoc <: AssociativeTable[P, C], P, C, PT <: ModelTable[P], CT <: ModelTable[C], F[_]](
    child: DbModel[C],
    val base: ModelAssociationAccess[Assoc, P, C, PT, CT, F]
) {
  def addAssoc(parent: DbModel[P]): F[Unit] = base.addAssoc(parent, child)

  def removeAssoc(parent: DbModel[P]): F[Unit] = base.removeAssoc(parent, child)

  def contains(parent: DbModel[P]): F[Boolean] = base.contains(parent, child)

  def deleteAllFromChild: F[Unit] = base.deleteAllFromChild(child)

  def allQueryFromChild: Query[PT, DbModel[P], Seq] = base.allQueryFromChild(child)

  def allFromChild: F[Seq[DbModel[P]]] = base.allFromChild(child)
}

class ModelAssociationAccessImpl[
    Assoc <: AssociativeTable[P, C],
    P,
    C,
    PT <: ModelTable[P],
    CT <: ModelTable[C]
](val pCompanion: DbModelCompanion.Aux[P, PT], val cCompanion: DbModelCompanion.Aux[C, CT])(
    implicit
    query: AssociationQuery[Assoc, P, C],
    service: ModelService
) extends ModelAssociationAccess[Assoc, P, C, PT, CT, IO] {

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

  override def allQueryFromParent(parent: DbModel[P]): Query[CT, DbModel[C], Seq] =
    for {
      assoc <- query.baseQuery if query.parentRef(assoc) === parent.id.value
      child <- cCompanion.baseQuery if query.childRef(assoc) === child.id
    } yield child

  def allFromParent(parent: DbModel[P]): IO[Seq[DbModel[C]]] = service.runDBIO(allQueryFromParent(parent).result)

  override def allQueryFromChild(child: DbModel[C]): Query[PT, DbModel[P], Seq] =
    for {
      assoc  <- query.baseQuery if query.childRef(assoc) === child.id.value
      parent <- pCompanion.baseQuery if query.parentRef(assoc) === parent.id
    } yield parent

  def allFromChild(child: DbModel[C]): IO[Seq[DbModel[P]]] = service.runDBIO(allQueryFromChild(child).result)
}
