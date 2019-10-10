package ore.external

import scala.concurrent.duration.FiniteDuration

trait Cacher[F[_]] {

  def cache[A](duration: FiniteDuration)(fa: F[A]): F[F[A]]
}
