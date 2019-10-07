package ore.db

import slick.lifted.{Rep, TableQuery}

trait AssociationQuery[Assoc <: OreProfile#AssociativeTable[P, C], P, C] {
  def baseQuery: TableQuery[Assoc]

  def parentRef(t: Assoc): Rep[DbRef[P]]
  def childRef(t: Assoc): Rep[DbRef[C]]
}
object AssociationQuery {
  def apply[Assoc <: OreProfile#AssociativeTable[P, C], P, C](
      implicit query: AssociationQuery[Assoc, P, C]
  ): AssociationQuery[Assoc, P, C] = query

  def from[Assoc0 <: OreProfile#AssociativeTable[P0, C0], P0, C0](assocTable: TableQuery[Assoc0])(
      parentRef0: Assoc0 => Rep[DbRef[P0]],
      childRef0: Assoc0 => Rep[DbRef[C0]]
  ): AssociationQuery[Assoc0, P0, C0] = new AssociationQuery[Assoc0, P0, C0] {
    override def baseQuery: TableQuery[Assoc0] = assocTable

    override def parentRef(t: Assoc0): Rep[DbRef[P0]] = parentRef0(t)
    override def childRef(t: Assoc0): Rep[DbRef[C0]]  = childRef0(t)
  }
}
