package db.access

import db.impl.OrePostgresDriver.api._
import db.table.{AssociativeTable, ModelAssociation}
import db.{Model, ModelFilter, ModelService}

import scala.concurrent.{ExecutionContext, Future}

class ModelAssociationAccess[Assoc <: AssociativeTable, M <: Model](service: ModelService,
                                                                    parent: Model,
                                                                    parentRef: Assoc => Rep[Int],
                                                                    childClass: Class[M],
                                                                    childRef: Assoc => Rep[Int],
                                                                    assoc: ModelAssociation[Assoc])
  extends ModelAccess[M](service, childClass, ModelFilter[M] { child =>
    val assocQuery = for {
      row <- assoc.assocTable
      if parentRef(row) === parent.id.value
    } yield childRef(row)
    val childrenIds: Seq[Int] = service.await(service.DB.db.run(assocQuery.result)).get
    child.id inSetBind childrenIds
  }) {

  override def add(model: M)(implicit ec: ExecutionContext): Future[M] = {
    this.assoc.assoc(this.parent, model).map(_ => model)
  }

  override def remove(model: M): Future[Int] = this.assoc.disassoc(this.parent, model)

  override def removeAll(filter: M#T => Rep[Boolean] = _ => true) = throw new UnsupportedOperationException

}
