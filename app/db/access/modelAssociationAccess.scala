package db.access
import scala.language.higherKinds

import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable
import db.{AssociationQuery, Model, ModelQuery, ModelService}

import cats.instances.future._
import cats.syntax.all._

trait ModelAssociationAccess[Assoc <: AssociativeTable[P, C], P <: Model { type M = P }, C <: Model { type M = C }, F[
    _
]] {

  def addAssoc(parent: P, child: C): F[Unit]

  def removeAssoc(parent: P, child: C): F[Unit]

  def contains(parent: P, child: C): F[Boolean]

  def allQueryFromParent(parent: P): Query[C#T, C, Seq]

  def allFromParent(parent: P): F[Seq[C]]

  def allQueryFromChild(child: C): Query[P#T, P, Seq]

  def allFromChild(child: C): F[Seq[P]]
}

class ModelAssociationAccessImpl[
    Assoc <: AssociativeTable[P, C],
    P <: Model { type M = P }: ModelQuery,
    C <: Model { type M = C }: ModelQuery
](
    implicit
    query: AssociationQuery[Assoc, P, C],
    service: ModelService,
    ec: ExecutionContext
) extends ModelAssociationAccess[Assoc, P, C, Future] {

  def addAssoc(parent: P, child: C): Future[Unit] =
    service.runDBIO(query.baseQuery += ((parent.id.value, child.id.value))).void

  def removeAssoc(parent: P, child: C): Future[Unit] =
    service
      .runDBIO(
        query.baseQuery
          .filter(t => query.parentRef(t) === parent.id.value && query.childRef(t) === child.id.value)
          .delete
      )
      .void

  def contains(parent: P, child: C): Future[Boolean] = service.runDBIO(
    (query.baseQuery
      .filter(t => query.parentRef(t) === parent.id.value && query.childRef(t) === child.id.value)
      .length > 0).result
  )

  override def allQueryFromParent(parent: P): Query[C#T, C, Seq] =
    for {
      assoc <- query.baseQuery if query.parentRef(assoc) === parent.id.value
      child <- ModelQuery[C].baseQuery if query.childRef(assoc) === child.id
    } yield child

  def allFromParent(parent: P): Future[Seq[C]] = service.runDBIO(allQueryFromParent(parent).result)

  override def allQueryFromChild(child: C): Query[P#T, P, Seq] =
    for {
      assoc  <- query.baseQuery if query.childRef(assoc) === child.id.value
      parent <- ModelQuery[P].baseQuery if query.parentRef(assoc) === parent.id
    } yield parent

  def allFromChild(child: C): Future[Seq[P]] = service.runDBIO(allQueryFromChild(child).result)
}
