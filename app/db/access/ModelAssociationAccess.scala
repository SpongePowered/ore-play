package db.access

import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable
import db.{AssociationQuery, Model, ModelFilter, ModelQuery, ModelService, ObjectReference}

import cats.instances.future._
import cats.syntax.all._

class ModelAssociationAccess[Assoc <: AssociativeTable, P <: Model, C <: Model: ModelQuery](
    service: ModelService,
    parent: P,
)(implicit query: AssociationQuery[Assoc, P, C])
    extends ModelAccess[C](
      service,
      ModelFilter[C] { child =>
        val assocQuery = for {
          row <- query.baseQuery
          if query.parentRef(row) === parent.id.value
        } yield query.childRef(row)
        val childrenIds: Seq[ObjectReference] = service.await(service.runDBIO(assocQuery.result)).get
        child.id.inSetBind(childrenIds)
      }
    ) {

  override def add(model: C)(implicit ec: ExecutionContext): Future[C] =
    service.runDBIO(query.baseQuery += ((parent.id.value, model.id.value))).as(model)

  override def remove(model: C): Future[Int] =
    service.runDBIO(
      query.baseQuery.filter(t => query.parentRef(t) === parent.id.value && query.childRef(t) === model.id.value).delete
    )

}
