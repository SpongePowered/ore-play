package util

import scala.concurrent.{ExecutionContext, Future}

object FutureUtils {

  def race[A, B](fa: Future[A], fb: Future[B])(implicit ec: ExecutionContext): Future[Either[A, B]] =
    Future.firstCompletedOf(Seq(fa.map(Left.apply), fb.map(Right.apply)))

  def raceBoolean(fa: Future[Boolean], fb: Future[Boolean])(implicit ec: ExecutionContext): Future[Boolean] =
    race(fa, fb).flatMap {
      case Left(false)  => fb
      case Right(false) => fa
      case _            => Future.successful(true)
    }
}
