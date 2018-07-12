package util.instances

import util.functional.Monad

trait OptionInstances {

  implicit val optionInstance: Monad[Option] = new Monad[Option] {
    override def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] = fa.flatMap(f)
    override def pure[A](a: A): Option[A] = Some(a)
    override def flatten[A](ffa: Option[Option[A]]): Option[A] = ffa.flatten
    override def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa.map(f)
  }
}
