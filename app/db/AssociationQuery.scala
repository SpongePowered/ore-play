package db

import db.impl.OrePostgresDriver.api._
import db.table.AssociativeTable

import slick.lifted.TableQuery

trait AssociationQuery[Assoc <: AssociativeTable, P, C] {
  def baseQuery: TableQuery[Assoc]

  def parentRef(t: Assoc): Rep[ObjectReference]
  def childRef(t: Assoc): Rep[ObjectReference]
}
object AssociationQuery {
  def apply[Assoc <: AssociativeTable, P, C](
      implicit query: AssociationQuery[Assoc, P, C]
  ): AssociationQuery[Assoc, P, C] = query

  def from[Assoc0 <: AssociativeTable, P0, C0](assocTable: TableQuery[Assoc0])(
      parentRef0: Assoc0 => Rep[ObjectReference],
      childRef0: Assoc0 => Rep[ObjectReference]
  ): AssociationQuery[Assoc0, P0, C0] = new AssociationQuery[Assoc0, P0, C0] {
    override def baseQuery: TableQuery[Assoc0] = assocTable

    override def parentRef(t: Assoc0): Rep[ObjectReference] = parentRef0(t)
    override def childRef(t: Assoc0): Rep[ObjectReference]  = childRef0(t)
  }
}
