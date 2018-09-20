package db.access

import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.table.{AssociativeTable, ModelAssociation}
import db.{Model, ModelFilter, ModelService, ObjectReference}

import cats.syntax.all._
import cats.instances.future._

class ModelAssociationAccess[Assoc <: AssociativeTable, M <: Model](
    service: ModelService,
    parent: Model,
    parentRef: Assoc => Rep[ObjectReference],
    childClass: Class[M],
    childRef: Assoc => Rep[ObjectReference],
    assoc: ModelAssociation[Assoc]
) extends ModelAccess[M](
      service,
      childClass,
      ModelFilter[M] { child =>
        val assocQuery = for {
          row <- assoc.assocTable
          if parentRef(row) === parent.id.value
        } yield childRef(row)
        val childrenIds: Seq[ObjectReference] = service.await(service.DB.db.run(assocQuery.result)).get
        child.id.inSetBind(childrenIds)
      }
    ) {

  override def add(model: M)(implicit ec: ExecutionContext): Future[M] =
    this.assoc.assoc(this.parent, model).as(model)

  override def remove(model: M): Future[Int] = this.assoc.disassoc(this.parent, model)

  override def removeAll(filter: M#T => Rep[Boolean] = _ => true) = throw new UnsupportedOperationException

}
