package util.instances

import scala.concurrent.ExecutionContext

import slick.dbio.DBIO
import util.functional.Monad

trait DBIOInstances {

  implicit def dbioInstance(implicit ec: ExecutionContext): Monad[DBIO] = new Monad[DBIO] {
    override def flatMap[A, B](fa: DBIO[A])(f: A => DBIO[B]): DBIO[B] = fa.flatMap(f)
    override def pure[A](a: A): DBIO[A] = DBIO.successful(a)
    override def flatten[A](ffa: DBIO[DBIO[A]]): DBIO[A] = ffa.flatten
    override def map[A, B](fa: DBIO[A])(f: A => B): DBIO[B] = fa.map(f)
    override def product[A, B](fa: DBIO[A], fb: DBIO[B]): DBIO[(A, B)] = fa.zip(fb)
    override def map2[A, B, C](fa: DBIO[A], fb: DBIO[B])(f: (A, B) => C): DBIO[C] = fa.zipWith(fb)(f)
    override def *>[A, B](fa: DBIO[A])(fb: DBIO[B]): DBIO[B] = fa >> fb
  }
}
