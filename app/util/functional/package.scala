package util

package object functional {

  type Id[A] = A

  implicit val idInstance: Monad[Id] = new Monad[Id] {
    override def flatMap[A, B](fa: A)(f: A => B): B = f(fa)
    override def pure[A](a: A): A = a

    override def ap[A, B](ff: Id[A => B])(fa: A): B = ff(fa)
    override def flatten[A](ffa: A): A = ffa: A
    override def map[A, B](fa: A)(f: A => B): B = f(fa)
    override def product[A, B](fa: A, fb: B): (A, B) = (fa, fb)
    override def map2[A, B, C](fa: A, fb: B)(f: (A, B) => C): Id[C] = f(fa, fb)
    override def <*>[A, B](ff: Id[A => B])(fa: A): B = ff(fa)
    override def *>[A, B](fa: A)(fb: B): B = fb
    override def <*[A, B](fa: A)(fb: B): A = fa
    override def as[A, B](fa: A, b: B): B = b
    override def fproduct[A, B](fa: A)(f: A => B): (A, B) = (fa, f(fa))
    override def tupleLeft[A, B](fa: A, b: B): (B, A) = (b, fa)
    override def tupleRight[A, B](fa: A, b: B): (A, B) = (fa, b)
  }

}
