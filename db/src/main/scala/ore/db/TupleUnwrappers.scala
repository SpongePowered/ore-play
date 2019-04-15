package ore.db

// GENERATED CODE. DO NOT EDIT!
import ore.db.Model.{SuperLowPriorityUnwrappers, Unwrapper}

/*
(1 to 22).map { i =>
  val (tpesSeq, impsSeq, transformsSeq) = (1 to i).map { j =>
    val tpe = ('A' + j - 1).toChar.toString
    val outTpe = tpe + "O"
    val imp = s"el${j}Unwrapper: Unwrapper.Aux[$tpe, $outTpe]"
    val transform = s"el${j}Unwrapper(t._$j)"
    ((tpe, outTpe), imp, transform)
  }.unzip3
  val inTpes = tpesSeq.map(_._1).mkString(", ")
  val outTpes = tpesSeq.map(_._2).mkString(", ")
  val tpes = tpesSeq.flatMap(t => Seq(t._1, t._2)).mkString(", ")
  val imps = impsSeq.mkString(", ")
  val transforms = transformsSeq.mkString(", ")
  s"""implicit def unwrapTuple$i[$tpes](implicit $imps): Unwrapper.Aux[Tuple$i[$inTpes], Tuple$i[$outTpes]] = new Unwrapper[Tuple$i[$inTpes]] {
     |  override type Out = Tuple$i[$outTpes]
     |  override def apply(t: Tuple$i[$inTpes]) = Tuple$i($transforms)
     |}""".stripMargin
}.foreach { s =>
  println(s)
  println()
}
 */
trait TupleUnwrappers extends SuperLowPriorityUnwrappers {
  implicit def unwrapTuple1[A, AO](implicit el1Unwrapper: Unwrapper.Aux[A, AO]): Unwrapper.Aux[Tuple1[A], Tuple1[AO]] =
    new Unwrapper[Tuple1[A]] {
      override type Out = Tuple1[AO]
      override def apply(t: Tuple1[A]) = Tuple1(el1Unwrapper(t._1))
    }

  implicit def unwrapTuple2[A, AO, B, BO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO]
  ): Unwrapper.Aux[Tuple2[A, B], Tuple2[AO, BO]] = new Unwrapper[Tuple2[A, B]] {
    override type Out = Tuple2[AO, BO]
    override def apply(t: Tuple2[A, B]) = Tuple2(el1Unwrapper(t._1), el2Unwrapper(t._2))
  }

  implicit def unwrapTuple3[A, AO, B, BO, C, CO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO]
  ): Unwrapper.Aux[Tuple3[A, B, C], Tuple3[AO, BO, CO]] = new Unwrapper[Tuple3[A, B, C]] {
    override type Out = Tuple3[AO, BO, CO]
    override def apply(t: Tuple3[A, B, C]) = Tuple3(el1Unwrapper(t._1), el2Unwrapper(t._2), el3Unwrapper(t._3))
  }

  implicit def unwrapTuple4[A, AO, B, BO, C, CO, D, DO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO]
  ): Unwrapper.Aux[Tuple4[A, B, C, D], Tuple4[AO, BO, CO, DO]] = new Unwrapper[Tuple4[A, B, C, D]] {
    override type Out = Tuple4[AO, BO, CO, DO]
    override def apply(t: Tuple4[A, B, C, D]) =
      Tuple4(el1Unwrapper(t._1), el2Unwrapper(t._2), el3Unwrapper(t._3), el4Unwrapper(t._4))
  }

  implicit def unwrapTuple5[A, AO, B, BO, C, CO, D, DO, E, EO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO]
  ): Unwrapper.Aux[Tuple5[A, B, C, D, E], Tuple5[AO, BO, CO, DO, EO]] = new Unwrapper[Tuple5[A, B, C, D, E]] {
    override type Out = Tuple5[AO, BO, CO, DO, EO]
    override def apply(t: Tuple5[A, B, C, D, E]) =
      Tuple5(el1Unwrapper(t._1), el2Unwrapper(t._2), el3Unwrapper(t._3), el4Unwrapper(t._4), el5Unwrapper(t._5))
  }

  implicit def unwrapTuple6[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO]
  ): Unwrapper.Aux[Tuple6[A, B, C, D, E, F], Tuple6[AO, BO, CO, DO, EO, FO]] = new Unwrapper[Tuple6[A, B, C, D, E, F]] {
    override type Out = Tuple6[AO, BO, CO, DO, EO, FO]
    override def apply(t: Tuple6[A, B, C, D, E, F]) =
      Tuple6(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6)
      )
  }

  implicit def unwrapTuple7[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO, G, GO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO]
  ): Unwrapper.Aux[Tuple7[A, B, C, D, E, F, G], Tuple7[AO, BO, CO, DO, EO, FO, GO]] =
    new Unwrapper[Tuple7[A, B, C, D, E, F, G]] {
      override type Out = Tuple7[AO, BO, CO, DO, EO, FO, GO]
      override def apply(t: Tuple7[A, B, C, D, E, F, G]) =
        Tuple7(
          el1Unwrapper(t._1),
          el2Unwrapper(t._2),
          el3Unwrapper(t._3),
          el4Unwrapper(t._4),
          el5Unwrapper(t._5),
          el6Unwrapper(t._6),
          el7Unwrapper(t._7)
        )
    }

  implicit def unwrapTuple8[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO, G, GO, H, HO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO]
  ): Unwrapper.Aux[Tuple8[A, B, C, D, E, F, G, H], Tuple8[AO, BO, CO, DO, EO, FO, GO, HO]] =
    new Unwrapper[Tuple8[A, B, C, D, E, F, G, H]] {
      override type Out = Tuple8[AO, BO, CO, DO, EO, FO, GO, HO]
      override def apply(t: Tuple8[A, B, C, D, E, F, G, H]) =
        Tuple8(
          el1Unwrapper(t._1),
          el2Unwrapper(t._2),
          el3Unwrapper(t._3),
          el4Unwrapper(t._4),
          el5Unwrapper(t._5),
          el6Unwrapper(t._6),
          el7Unwrapper(t._7),
          el8Unwrapper(t._8)
        )
    }

  implicit def unwrapTuple9[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO, G, GO, H, HO, I, IO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO]
  ): Unwrapper.Aux[Tuple9[A, B, C, D, E, F, G, H, I], Tuple9[AO, BO, CO, DO, EO, FO, GO, HO, IO]] =
    new Unwrapper[Tuple9[A, B, C, D, E, F, G, H, I]] {
      override type Out = Tuple9[AO, BO, CO, DO, EO, FO, GO, HO, IO]
      override def apply(t: Tuple9[A, B, C, D, E, F, G, H, I]) =
        Tuple9(
          el1Unwrapper(t._1),
          el2Unwrapper(t._2),
          el3Unwrapper(t._3),
          el4Unwrapper(t._4),
          el5Unwrapper(t._5),
          el6Unwrapper(t._6),
          el7Unwrapper(t._7),
          el8Unwrapper(t._8),
          el9Unwrapper(t._9)
        )
    }

  implicit def unwrapTuple10[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO, G, GO, H, HO, I, IO, J, JO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO]
  ): Unwrapper.Aux[Tuple10[A, B, C, D, E, F, G, H, I, J], Tuple10[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO]] =
    new Unwrapper[Tuple10[A, B, C, D, E, F, G, H, I, J]] {
      override type Out = Tuple10[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO]
      override def apply(t: Tuple10[A, B, C, D, E, F, G, H, I, J]) =
        Tuple10(
          el1Unwrapper(t._1),
          el2Unwrapper(t._2),
          el3Unwrapper(t._3),
          el4Unwrapper(t._4),
          el5Unwrapper(t._5),
          el6Unwrapper(t._6),
          el7Unwrapper(t._7),
          el8Unwrapper(t._8),
          el9Unwrapper(t._9),
          el10Unwrapper(t._10)
        )
    }

  implicit def unwrapTuple11[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO, G, GO, H, HO, I, IO, J, JO, K, KO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO]
  ): Unwrapper.Aux[Tuple11[A, B, C, D, E, F, G, H, I, J, K], Tuple11[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO]] =
    new Unwrapper[Tuple11[A, B, C, D, E, F, G, H, I, J, K]] {
      override type Out = Tuple11[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO]
      override def apply(t: Tuple11[A, B, C, D, E, F, G, H, I, J, K]) =
        Tuple11(
          el1Unwrapper(t._1),
          el2Unwrapper(t._2),
          el3Unwrapper(t._3),
          el4Unwrapper(t._4),
          el5Unwrapper(t._5),
          el6Unwrapper(t._6),
          el7Unwrapper(t._7),
          el8Unwrapper(t._8),
          el9Unwrapper(t._9),
          el10Unwrapper(t._10),
          el11Unwrapper(t._11)
        )
    }

  implicit def unwrapTuple12[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO, G, GO, H, HO, I, IO, J, JO, K, KO, L, LO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO]
  ): Unwrapper.Aux[Tuple12[A, B, C, D, E, F, G, H, I, J, K, L], Tuple12[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO
  ]] = new Unwrapper[Tuple12[A, B, C, D, E, F, G, H, I, J, K, L]] {
    override type Out = Tuple12[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO]
    override def apply(t: Tuple12[A, B, C, D, E, F, G, H, I, J, K, L]) =
      Tuple12(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12)
      )
  }

  implicit def unwrapTuple13[A, AO, B, BO, C, CO, D, DO, E, EO, F, FO, G, GO, H, HO, I, IO, J, JO, K, KO, L, LO, M, MO](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO]
  ): Unwrapper.Aux[Tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M], Tuple13[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO
  ]] = new Unwrapper[Tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M]] {
    override type Out = Tuple13[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO]
    override def apply(t: Tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M]) =
      Tuple13(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13)
      )
  }

  implicit def unwrapTuple14[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO]
  ): Unwrapper.Aux[Tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N], Tuple14[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO
  ]] = new Unwrapper[Tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N]] {
    override type Out = Tuple14[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO]
    override def apply(t: Tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N]) =
      Tuple14(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14)
      )
  }

  implicit def unwrapTuple15[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO]
  ): Unwrapper.Aux[Tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O], Tuple15[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO
  ]] = new Unwrapper[Tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]] {
    override type Out = Tuple15[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO]
    override def apply(t: Tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]) =
      Tuple15(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15)
      )
  }

  implicit def unwrapTuple16[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO,
      P,
      PO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO],
      el16Unwrapper: Unwrapper.Aux[P, PO]
  ): Unwrapper.Aux[Tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P], Tuple16[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO,
    PO
  ]] = new Unwrapper[Tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]] {
    override type Out = Tuple16[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO, PO]
    override def apply(t: Tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]) =
      Tuple16(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15),
        el16Unwrapper(t._16)
      )
  }

  implicit def unwrapTuple17[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO,
      P,
      PO,
      Q,
      QO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO],
      el16Unwrapper: Unwrapper.Aux[P, PO],
      el17Unwrapper: Unwrapper.Aux[Q, QO]
  ): Unwrapper.Aux[Tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q], Tuple17[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO,
    PO,
    QO
  ]] = new Unwrapper[Tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]] {
    override type Out = Tuple17[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO, PO, QO]
    override def apply(t: Tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]) =
      Tuple17(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15),
        el16Unwrapper(t._16),
        el17Unwrapper(t._17)
      )
  }

  implicit def unwrapTuple18[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO,
      P,
      PO,
      Q,
      QO,
      R,
      RO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO],
      el16Unwrapper: Unwrapper.Aux[P, PO],
      el17Unwrapper: Unwrapper.Aux[Q, QO],
      el18Unwrapper: Unwrapper.Aux[R, RO]
  ): Unwrapper.Aux[Tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R], Tuple18[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO,
    PO,
    QO,
    RO
  ]] = new Unwrapper[Tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]] {
    override type Out = Tuple18[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO, PO, QO, RO]
    override def apply(t: Tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]) =
      Tuple18(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15),
        el16Unwrapper(t._16),
        el17Unwrapper(t._17),
        el18Unwrapper(t._18)
      )
  }

  implicit def unwrapTuple19[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO,
      P,
      PO,
      Q,
      QO,
      R,
      RO,
      S,
      SO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO],
      el16Unwrapper: Unwrapper.Aux[P, PO],
      el17Unwrapper: Unwrapper.Aux[Q, QO],
      el18Unwrapper: Unwrapper.Aux[R, RO],
      el19Unwrapper: Unwrapper.Aux[S, SO]
  ): Unwrapper.Aux[Tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S], Tuple19[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO,
    PO,
    QO,
    RO,
    SO
  ]] = new Unwrapper[Tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]] {
    override type Out = Tuple19[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO, PO, QO, RO, SO]
    override def apply(t: Tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]) =
      Tuple19(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15),
        el16Unwrapper(t._16),
        el17Unwrapper(t._17),
        el18Unwrapper(t._18),
        el19Unwrapper(t._19)
      )
  }

  implicit def unwrapTuple20[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO,
      P,
      PO,
      Q,
      QO,
      R,
      RO,
      S,
      SO,
      T,
      TO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO],
      el16Unwrapper: Unwrapper.Aux[P, PO],
      el17Unwrapper: Unwrapper.Aux[Q, QO],
      el18Unwrapper: Unwrapper.Aux[R, RO],
      el19Unwrapper: Unwrapper.Aux[S, SO],
      el20Unwrapper: Unwrapper.Aux[T, TO]
  ): Unwrapper.Aux[Tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T], Tuple20[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO,
    PO,
    QO,
    RO,
    SO,
    TO
  ]] = new Unwrapper[Tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]] {
    override type Out = Tuple20[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO, PO, QO, RO, SO, TO]
    override def apply(t: Tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]) =
      Tuple20(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15),
        el16Unwrapper(t._16),
        el17Unwrapper(t._17),
        el18Unwrapper(t._18),
        el19Unwrapper(t._19),
        el20Unwrapper(t._20)
      )
  }

  implicit def unwrapTuple21[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO,
      P,
      PO,
      Q,
      QO,
      R,
      RO,
      S,
      SO,
      T,
      TO,
      U,
      UO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO],
      el16Unwrapper: Unwrapper.Aux[P, PO],
      el17Unwrapper: Unwrapper.Aux[Q, QO],
      el18Unwrapper: Unwrapper.Aux[R, RO],
      el19Unwrapper: Unwrapper.Aux[S, SO],
      el20Unwrapper: Unwrapper.Aux[T, TO],
      el21Unwrapper: Unwrapper.Aux[U, UO]
  ): Unwrapper.Aux[Tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U], Tuple21[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO,
    PO,
    QO,
    RO,
    SO,
    TO,
    UO
  ]] = new Unwrapper[Tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]] {
    override type Out = Tuple21[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO, PO, QO, RO, SO, TO, UO]
    override def apply(t: Tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]) =
      Tuple21(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15),
        el16Unwrapper(t._16),
        el17Unwrapper(t._17),
        el18Unwrapper(t._18),
        el19Unwrapper(t._19),
        el20Unwrapper(t._20),
        el21Unwrapper(t._21)
      )
  }

  implicit def unwrapTuple22[
      A,
      AO,
      B,
      BO,
      C,
      CO,
      D,
      DO,
      E,
      EO,
      F,
      FO,
      G,
      GO,
      H,
      HO,
      I,
      IO,
      J,
      JO,
      K,
      KO,
      L,
      LO,
      M,
      MO,
      N,
      NO,
      O,
      OO,
      P,
      PO,
      Q,
      QO,
      R,
      RO,
      S,
      SO,
      T,
      TO,
      U,
      UO,
      V,
      VO
  ](
      implicit el1Unwrapper: Unwrapper.Aux[A, AO],
      el2Unwrapper: Unwrapper.Aux[B, BO],
      el3Unwrapper: Unwrapper.Aux[C, CO],
      el4Unwrapper: Unwrapper.Aux[D, DO],
      el5Unwrapper: Unwrapper.Aux[E, EO],
      el6Unwrapper: Unwrapper.Aux[F, FO],
      el7Unwrapper: Unwrapper.Aux[G, GO],
      el8Unwrapper: Unwrapper.Aux[H, HO],
      el9Unwrapper: Unwrapper.Aux[I, IO],
      el10Unwrapper: Unwrapper.Aux[J, JO],
      el11Unwrapper: Unwrapper.Aux[K, KO],
      el12Unwrapper: Unwrapper.Aux[L, LO],
      el13Unwrapper: Unwrapper.Aux[M, MO],
      el14Unwrapper: Unwrapper.Aux[N, NO],
      el15Unwrapper: Unwrapper.Aux[O, OO],
      el16Unwrapper: Unwrapper.Aux[P, PO],
      el17Unwrapper: Unwrapper.Aux[Q, QO],
      el18Unwrapper: Unwrapper.Aux[R, RO],
      el19Unwrapper: Unwrapper.Aux[S, SO],
      el20Unwrapper: Unwrapper.Aux[T, TO],
      el21Unwrapper: Unwrapper.Aux[U, UO],
      el22Unwrapper: Unwrapper.Aux[V, VO]
  ): Unwrapper.Aux[Tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V], Tuple22[
    AO,
    BO,
    CO,
    DO,
    EO,
    FO,
    GO,
    HO,
    IO,
    JO,
    KO,
    LO,
    MO,
    NO,
    OO,
    PO,
    QO,
    RO,
    SO,
    TO,
    UO,
    VO
  ]] = new Unwrapper[Tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V]] {
    override type Out = Tuple22[AO, BO, CO, DO, EO, FO, GO, HO, IO, JO, KO, LO, MO, NO, OO, PO, QO, RO, SO, TO, UO, VO]
    override def apply(t: Tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V]) =
      Tuple22(
        el1Unwrapper(t._1),
        el2Unwrapper(t._2),
        el3Unwrapper(t._3),
        el4Unwrapper(t._4),
        el5Unwrapper(t._5),
        el6Unwrapper(t._6),
        el7Unwrapper(t._7),
        el8Unwrapper(t._8),
        el9Unwrapper(t._9),
        el10Unwrapper(t._10),
        el11Unwrapper(t._11),
        el12Unwrapper(t._12),
        el13Unwrapper(t._13),
        el14Unwrapper(t._14),
        el15Unwrapper(t._15),
        el16Unwrapper(t._16),
        el17Unwrapper(t._17),
        el18Unwrapper(t._18),
        el19Unwrapper(t._19),
        el20Unwrapper(t._20),
        el21Unwrapper(t._21),
        el22Unwrapper(t._22)
      )
  }
}
