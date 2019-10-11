package util

import scala.concurrent.duration.FiniteDuration

import ore.external.Cacher

import zio.ZIO
import zio.clock.Clock

class ZIOCacher[R, E] extends Cacher[ZIO[R with Clock, E, *]] {
  override def cache[A](duration: FiniteDuration)(
      fa: ZIO[R with Clock, E, A]
  ): ZIO[R with Clock, E, ZIO[R with Clock, E, A]] = fa.cached(zio.duration.Duration.fromScala(duration))
}
object ZIOCacher {
  implicit def instance[R, E]: Cacher[ZIO[R with Clock, E, *]] = new ZIOCacher[R, E]
}
