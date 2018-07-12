package util.instances

import util.functional.Monad

trait SeqInstances {

  implicit val seqInstance: Monad[Seq] = new Monad[Seq] {
    override def flatMap[A, B](fa: Seq[A])(f: A => Seq[B]): Seq[B] = fa.flatMap(f)
    override def pure[A](a: A): Seq[A] = Seq(a)
    override def map[A, B](fa: Seq[A])(f: A => B): Seq[B] = fa.map(f)
  }
}
