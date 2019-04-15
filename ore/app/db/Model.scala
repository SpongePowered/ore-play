package db

import scala.language.{higherKinds, implicitConversions}

import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom

import ore.organization.OrganizationOwned
import ore.permission.scope.HasScope
import ore.project.ProjectOwned
import ore.user.UserOwned

import cats.Functor
import cats.syntax.all._
import shapeless._

case class Model[+A](
    id: ObjId[A],
    createdAt: ObjTimestamp,
    obj: A
)
object Model {
  implicit def unwrap[A](dbModel: Model[A]): A = dbModel.obj

  implicit def isProjectOwned[A](implicit isOwned: ProjectOwned[A]): ProjectOwned[Model[A]] =
    (a: Model[A]) => isOwned.projectId(a.obj)
  implicit def isUserOwned[A](implicit isOwned: UserOwned[A]): UserOwned[Model[A]] =
    (a: Model[A]) => isOwned.userId(a.obj)
  implicit def isOrgOwned[A](implicit isOwned: OrganizationOwned[A]): OrganizationOwned[Model[A]] =
    (a: Model[A]) => isOwned.organizationId(a.obj)

  implicit def hasUnderlyingScope[A](implicit hasScope: HasScope[A]): HasScope[Model[A]] =
    (a: Model[A]) => hasScope.scope(a.obj)

  def unwrapNested[Out]: UnwrapNestedPartiallyApplied[Out] = new UnwrapNestedPartiallyApplied[Out]()

  class UnwrapNestedPartiallyApplied[Out](private val dummy: Boolean = false) extends AnyVal {
    def apply[A](obj: A)(implicit unwrapper: Unwrapper.Aux[A, Out]): Out = unwrapper(obj)
  }

  trait Unwrapper[A] extends DepFn1[A]
  object Unwrapper extends TupleUnwrappers with LowPriorityUnwrappers {
    type Aux[A, Out0] = Unwrapper[A] { type Out = Out0 }

    def apply[A](implicit unwrapper: Unwrapper[A]): Unwrapper.Aux[A, unwrapper.Out]        = unwrapper
    def solve[In, Out](implicit unwrapper: Unwrapper.Aux[In, Out]): Unwrapper.Aux[In, Out] = unwrapper

    implicit def unwrapWrapper[A]: Unwrapper.Aux[Model[A], A] = new Unwrapper[Model[A]] {
      type Out = A
      def apply(t: Model[A]): Out = t.obj
    }

    implicit def unwrapHCons[H, T <: HList, HOut, TOut <: HList](
        implicit hUnwrapper: Lazy[Unwrapper.Aux[H, HOut]],
        tUnwrapper: Lazy[Unwrapper.Aux[T, TOut]]
    ): Unwrapper.Aux[H :: T, HOut :: TOut] = new Unwrapper[H :: T] {
      type Out = HOut :: TOut
      def apply(t: H :: T): Out = hUnwrapper.value(t.head) :: tUnwrapper.value(t.tail)
    }

    implicit val unwrapHNil: Unwrapper.Aux[HNil, HNil] = new Unwrapper[HNil] {
      type Out = HNil
      def apply(t: HNil): Out = t
    }

    implicit def unwrapF[F[_]: Functor, A, InnerOut](
        implicit inner: Unwrapper.Aux[A, InnerOut]
    ): Unwrapper.Aux[F[A], F[InnerOut]] = new Unwrapper[F[A]] {
      override type Out = F[InnerOut]
      override def apply(t: F[A]): Out = t.map(inner(_))
    }
  }
  trait LowPriorityUnwrappers extends TupleUnwrappers {
    implicit def seqUnwrapper[A, InnerOut, CC[X] <: TraversableLike[X, CC[X]]](
        implicit inner: Unwrapper.Aux[A, InnerOut],
        buildFrom: CanBuildFrom[CC[A], InnerOut, CC[InnerOut]]
    ): Unwrapper.Aux[CC[A], CC[InnerOut]] =
      new Unwrapper[CC[A]] {
        override type Out = CC[InnerOut]
        override def apply(t: CC[A]): Out = t.map(inner(_))(buildFrom)
      }
  }
  trait SuperLowPriorityUnwrappers {
    implicit def unwrapConst[A]: Unwrapper.Aux[A, A] = new Unwrapper[A] {
      type Out = A
      def apply(t: A): Out = t
    }
  }
}
