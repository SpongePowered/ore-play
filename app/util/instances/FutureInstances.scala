package util.instances

import scala.concurrent.{ExecutionContext, Future}

import util.functional.Monad

trait FutureInstances {

  implicit def futureInstance(implicit ec: ExecutionContext): Monad[Future] =
    new Monad[Future] {
      override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
      override def flatten[A](ffa: Future[Future[A]]): Future[A] = ffa.flatten
      override def pure[A](a: A): Future[A] = Future.successful(a)
      override def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)
      override def product[A, B](fa: Future[A], fb: Future[B]): Future[(A, B)] = fa.zip(fb)
    }

}
