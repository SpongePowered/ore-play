package ore.db.access

import scala.language.higherKinds

import ore.db._

import cats.Functor
import cats.syntax.all._
import slick.jdbc.JdbcProfile

trait ModelAssociationAccess[Assoc <: OreProfile#AssociativeTable[P, C], P, C, PT <: OreProfile#ModelTable[P], CT <: OreProfile#ModelTable[
  C
], F[_]] {
  import slick.lifted.Query

  def addAssoc(parent: DbRef[P], child: DbRef[C]): F[Unit]

  def removeAssoc(parent: DbRef[P], child: DbRef[C]): F[Unit]

  def contains(parent: DbRef[P], child: DbRef[C]): F[Boolean]

  def deleteAllFromParent(parent: DbRef[P]): F[Unit]

  def deleteAllFromChild(child: DbRef[C]): F[Unit]

  def allQueryFromParent(parent: DbRef[P]): Query[CT, Model[C], Seq]

  def allFromParent(parent: DbRef[P]): F[Seq[Model[C]]]

  def allQueryFromChild(child: DbRef[C]): Query[PT, Model[P], Seq]

  def allFromChild(child: DbRef[C]): F[Seq[Model[P]]]

  def applyChild(child: DbRef[C]): ChildAssociationAccess[Assoc, P, C, PT, CT, F] =
    new ChildAssociationAccess(child, this)
  def applyParent(parent: DbRef[P]): ParentAssociationAccess[Assoc, P, C, PT, CT, F] =
    new ParentAssociationAccess(parent, this)
}

class ParentAssociationAccess[Assoc <: OreProfile#AssociativeTable[P, C], P, C, PT <: OreProfile#ModelTable[P], CT <: OreProfile#ModelTable[
  C
], F[_]](
    parent: DbRef[P],
    val base: ModelAssociationAccess[Assoc, P, C, PT, CT, F]
) {
  import slick.lifted.Query

  def addAssoc(child: DbRef[C]): F[Unit] = base.addAssoc(parent, child)

  def removeAssoc(child: DbRef[C]): F[Unit] = base.removeAssoc(parent, child)

  def contains(child: DbRef[C]): F[Boolean] = base.contains(parent, child)

  //noinspection MutatorLikeMethodIsParameterless
  def deleteAllFromParent: F[Unit] = base.deleteAllFromParent(parent)

  def allQueryFromParent: Query[CT, Model[C], Seq] = base.allQueryFromParent(parent)

  def allFromParent: F[Seq[Model[C]]] = base.allFromParent(parent)
}

class ChildAssociationAccess[Assoc <: OreProfile#AssociativeTable[P, C], P, C, PT <: OreProfile#ModelTable[P], CT <: OreProfile#ModelTable[
  C
], F[_]](
    child: DbRef[C],
    val base: ModelAssociationAccess[Assoc, P, C, PT, CT, F]
) {
  import slick.lifted.Query

  def addAssoc(parent: DbRef[P]): F[Unit] = base.addAssoc(parent, child)

  def removeAssoc(parent: DbRef[P]): F[Unit] = base.removeAssoc(parent, child)

  def contains(parent: DbRef[P]): F[Boolean] = base.contains(parent, child)

  //noinspection MutatorLikeMethodIsParameterless
  def deleteAllFromChild: F[Unit] = base.deleteAllFromChild(child)

  def allQueryFromChild: Query[PT, Model[P], Seq] = base.allQueryFromChild(child)

  def allFromChild: F[Seq[Model[P]]] = base.allFromChild(child)
}

class ModelAssociationAccessImpl[
    Assoc <: OreProfile#AssociativeTable[P, C],
    P,
    C,
    PT <: OreProfile#ModelTable[P],
    CT <: OreProfile#ModelTable[C],
    F[_]
](val profile: JdbcProfile)(val pCompanion: ModelCompanion.Aux[P, PT], val cCompanion: ModelCompanion.Aux[C, CT])(
    implicit
    query: AssociationQuery[Assoc, P, C],
    service: ModelService[F],
    F: Functor[F]
) extends ModelAssociationAccess[Assoc, P, C, PT, CT, F] {
  import profile.api._

  def addAssoc(parent: DbRef[P], child: DbRef[C]): F[Unit] =
    service.runDBIO(query.baseQuery += ((parent, child))).void

  def removeAssoc(parent: DbRef[P], child: DbRef[C]): F[Unit] =
    service
      .runDBIO(
        query.baseQuery
          .filter(t => query.parentRef(t) === parent && query.childRef(t) === child)
          .delete
      )
      .void

  def contains(parent: DbRef[P], child: DbRef[C]): F[Boolean] = service.runDBIO(
    (query.baseQuery
      .filter(t => query.parentRef(t) === parent && query.childRef(t) === child)
      .length > 0).result
  )

  override def deleteAllFromParent(parent: DbRef[P]): F[Unit] =
    service.runDBIO(query.baseQuery.filter(query.parentRef(_) === parent).delete).void

  override def deleteAllFromChild(child: DbRef[C]): F[Unit] =
    service.runDBIO(query.baseQuery.filter(query.childRef(_) === child).delete).void

  override def allQueryFromParent(parent: DbRef[P]): Query[CT, Model[C], Seq] =
    for {
      assoc <- query.baseQuery if query.parentRef(assoc) === parent
      child <- cCompanion.baseQuery if query.childRef(assoc) === child.id
    } yield child

  def allFromParent(parent: DbRef[P]): F[Seq[Model[C]]] = service.runDBIO(allQueryFromParent(parent).result)

  override def allQueryFromChild(child: DbRef[C]): Query[PT, Model[P], Seq] =
    for {
      assoc  <- query.baseQuery if query.childRef(assoc) === child
      parent <- pCompanion.baseQuery if query.parentRef(assoc) === parent.id
    } yield parent

  def allFromChild(child: DbRef[C]): F[Seq[Model[P]]] = service.runDBIO(allQueryFromChild(child).result)
}
