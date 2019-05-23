package ore

import scala.language.higherKinds

import play.api.cache.SyncCacheApi

import cats.effect.Sync

/**
  * Represents something that can be added to the Cache.
  */
trait Cacheable {

  def cacheApi: SyncCacheApi

  /**
    * Returns the key to this in the Cache.
    *
    * @return Key
    */
  def key: String

  /**
    * Caches this.
    */
  def cache[F[_]](implicit F: Sync[F]): F[Unit] = F.delay(this.cacheApi.set(this.key, this))

  /**
    * Removes this from the Cache.
    */
  def free[F[_]](implicit F: Sync[F]): F[Unit] = F.delay(this.cacheApi.remove(this.key))

}
